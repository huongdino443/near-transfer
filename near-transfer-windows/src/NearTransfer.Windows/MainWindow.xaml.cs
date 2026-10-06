using Microsoft.Win32;
using NearTransfer.Core;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using WpfDragEventArgs = System.Windows.DragEventArgs;

namespace NearTransfer.Windows;

public partial class MainWindow : Window
{
    private const string NoPeersMessage =
        "Chưa thấy thiết bị. Hãy kết nối cùng Wi-Fi và mở Near Transfer trên Android.";

    private readonly AppSettings _settings;
    private readonly SettingsStore _settingsStore;
    private readonly ObservableCollection<QueuedFile> _queuedFiles = [];
    private readonly ObservableCollection<PeerRowViewModel> _peers = [];
    private readonly ObservableCollection<PeerRowViewModel> _savedPeers = [];
    private readonly DispatcherTimer _receivedTextActionTimer = new();
    private readonly PeerDiscoveryService _discovery;
    private readonly LanTransferServer _transferServer;
    private readonly LanTransferClient _transferClient = new();
    private CancellationTokenSource? _sendCancellation;
    private string? _queuedText;
    private string? _activeReceiveTransferId;
    private bool _isSending;
    private bool _isReceiving;
    private bool _showSavedPeers;
    private int _deviceTabAnimationVersion;

    public MainWindow(AppSettings settings, SettingsStore settingsStore)
    {
        InitializeComponent();
        _settings = settings;
        _settingsStore = settingsStore;
        _settings.SavedPeers ??= [];
        TransferProgressView.CloseRequested += TransferProgressView_CloseRequested;
        ReceivedTextView.CopyRequested += ReceivedTextView_CopyRequested;
        ReceivedTextView.SaveRequested += ReceivedTextView_SaveRequested;
        _receivedTextActionTimer.Tick += (_, _) =>
        {
            _receivedTextActionTimer.Stop();
            LeaveReceivedTextView();
        };
        QueueList.ItemsSource = _queuedFiles;
        DeviceList.ItemsSource = _peers;
        SavedPeerList.ItemsSource = _savedPeers;
        LoadSavedPeers();
        SaveFolderText.Text = _settings.SaveFolder;

        _discovery = new PeerDiscoveryService(Environment.MachineName);
        _discovery.PeersChanged += OnPeersChanged;
        _discovery.Error += OnDiscoveryError;
        _transferServer = new LanTransferServer(() => _settings.SaveFolder ?? string.Empty);
        _transferServer.OfferReceived += ConfirmIncomingOfferAsync;
        _transferServer.ProgressChanged += OnTransferProgress;
        _transferServer.FileReceived += OnFileReceived;
        _transferServer.TextReceived += OnTextReceived;
        _transferServer.Error += OnTransferError;

        StartLanServices();
        UpdateLocalIpText();
        UpdateDevicePanelState(updateTabSelection: true);
        UpdateQueueSummary();
    }

    protected override async void OnClosed(EventArgs e)
    {
        _sendCancellation?.Cancel();
        await _discovery.DisposeAsync();
        await _transferServer.DisposeAsync();
        _transferClient.Dispose();
        base.OnClosed(e);
    }

    private void StartLanServices()
    {
        try
        {
            _transferServer.Start();
            ReceiveIndicator.Fill = new SolidColorBrush(Color.FromRgb(184, 200, 255));
            ReceiveStatusText.Text = $"Sẵn sàng nhận · cổng {_transferServer.LocalPort}";
        }
        catch (SocketException exception)
        {
            ReceiveIndicator.Fill = new SolidColorBrush(Color.FromRgb(255, 180, 171));
            ReceiveStatusText.Text =
                $"Không mở được cổng nhận {TransferProtocol.HttpPort}: {exception.Message}";
        }

        try
        {
            _discovery.Start();
            NetworkStatusText.Text = "Đang tìm thiết bị Near Transfer trong mạng nội bộ.";
        }
        catch (SocketException exception)
        {
            NetworkStatusText.Text =
                $"Không mở được cổng tìm thiết bị {TransferProtocol.DiscoveryPort}: {exception.Message}";
        }
    }

    private void AddFiles_Click(object sender, RoutedEventArgs e)
    {
        if (_isSending)
        {
            return;
        }

        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Multiselect = true,
            CheckFileExists = true,
            Title = "Chọn file để gửi"
        };
        if (dialog.ShowDialog(this) == true)
        {
            AddFiles(dialog.FileNames);
        }
    }

    private void AddMedia_Click(object sender, RoutedEventArgs e)
    {
        if (_isSending)
        {
            return;
        }

        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Multiselect = true,
            CheckFileExists = true,
            Title = "Chọn ảnh hoặc video",
            Filter = "Ảnh và video|*.jpg;*.jpeg;*.png;*.gif;*.bmp;*.webp;*.tif;*.tiff;*.heic;*.heif;*.avif;*.mp4;*.m4v;*.mov;*.avi;*.mkv;*.wmv;*.webm;*.3gp;*.mpg;*.mpeg;*.ts|Tất cả tệp|*.*"
        };
        if (dialog.ShowDialog(this) == true)
        {
            AddFiles(dialog.FileNames);
        }
    }

    private void PasteText_Click(object sender, RoutedEventArgs e)
    {
        if (_isSending)
        {
            return;
        }

        try
        {
            if (!Clipboard.ContainsText(TextDataFormat.UnicodeText))
            {
                MessageBox.Show(
                    this,
                    "Clipboard không có văn bản.",
                    "Near Transfer",
                    MessageBoxButton.OK,
                    MessageBoxImage.Information);
                return;
            }

            QueueText(Clipboard.GetText(TextDataFormat.UnicodeText));
        }
        catch (ExternalException exception)
        {
            MessageBox.Show(
                this,
                $"Không đọc được clipboard: {exception.Message}",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
    }

    private void ComposeText_Click(object sender, RoutedEventArgs e)
    {
        if (_isSending)
        {
            return;
        }

        var replacingFiles = _queuedFiles.Count > 0;
        if (replacingFiles &&
            MessageBox.Show(
                this,
                "Thay các file đang chờ bằng một tin nhắn văn bản?",
                "Near Transfer",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question) != MessageBoxResult.Yes)
        {
            return;
        }

        var dialog = new TextComposerWindow(_queuedText) { Owner = this };
        if (dialog.ShowDialog() == true)
        {
            QueueText(dialog.SelectedText, filesReplacementConfirmed: replacingFiles);
        }
    }

    private void Window_DragEnter(object sender, WpfDragEventArgs e) => SetDropEffect(e);

    private void Window_DragOver(object sender, WpfDragEventArgs e) => SetDropEffect(e);

    private void Window_Drop(object sender, WpfDragEventArgs e)
    {
        if (e.Data.GetDataPresent(System.Windows.DataFormats.FileDrop) &&
            e.Data.GetData(System.Windows.DataFormats.FileDrop) is string[] paths)
        {
            AddFiles(paths);
        }
        else
        {
            e.Effects = System.Windows.DragDropEffects.None;
        }
        e.Handled = true;
    }

    private static void SetDropEffect(WpfDragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(System.Windows.DataFormats.FileDrop)
            ? System.Windows.DragDropEffects.Copy
            : System.Windows.DragDropEffects.None;
        e.Handled = true;
    }

    private void AddFiles(IEnumerable<string> paths)
    {
        if (_isSending)
        {
            return;
        }
        var requestedPaths = paths.ToArray();
        if (requestedPaths.Length == 0)
        {
            return;
        }
        if (_queuedText is not null &&
            MessageBox.Show(
                this,
                "Thay tin nhắn văn bản đang chờ bằng file?",
                "Near Transfer",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question) != MessageBoxResult.Yes)
        {
            return;
        }

        var knownPaths = new HashSet<string>(
            _queuedFiles.Select(file => file.Path),
            StringComparer.OrdinalIgnoreCase);
        var added = 0;
        var skipped = 0;
        long currentTotal = _queuedFiles.Sum(file => file.Size);

        foreach (var path in requestedPaths)
        {
            if (_queuedFiles.Count >= TransferProtocol.MaximumFiles)
            {
                skipped++;
                continue;
            }

            try
            {
                var fullPath = Path.GetFullPath(path);
                var info = new FileInfo(fullPath);
                if (!info.Exists || (File.GetAttributes(fullPath) & FileAttributes.Directory) != 0)
                {
                    skipped++;
                    continue;
                }
                if (!knownPaths.Add(fullPath))
                {
                    continue;
                }
                if (info.Length > TransferProtocol.MaximumFileBytes ||
                    currentTotal > TransferProtocol.MaximumBatchBytes - info.Length)
                {
                    skipped++;
                    continue;
                }

                currentTotal += info.Length;
                _queuedText = null;
                _queuedFiles.Add(new QueuedFile(
                    fullPath,
                    Path.GetFileName(fullPath),
                    info.Length,
                    FormatSize(info.Length)));
                added++;
            }
            catch (Exception exception) when (
                exception is IOException or UnauthorizedAccessException or ArgumentException or NotSupportedException)
            {
                skipped++;
            }
        }

        UpdateQueueSummary();
        if (added == 0 && skipped > 0)
        {
            MessageBox.Show(
                this,
                "Không có file phù hợp để thêm vào hàng chờ.",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
        }
    }

    private void RemoveFile_Click(object sender, RoutedEventArgs e)
    {
        if (!_isSending && sender is Button { Tag: QueuedFile file })
        {
            _queuedFiles.Remove(file);
            UpdateQueueSummary();
        }
    }

    private void ClearQueue_Click(object sender, RoutedEventArgs e)
    {
        if (_isSending)
        {
            return;
        }

        _queuedFiles.Clear();
        _queuedText = null;
        UpdateQueueSummary();
    }

    private void RemoveText_Click(object sender, RoutedEventArgs e)
    {
        if (!_isSending)
        {
            _queuedText = null;
            UpdateQueueSummary();
        }
    }

    private void QueueText(string text, bool filesReplacementConfirmed = false)
    {
        if (_isSending)
        {
            return;
        }

        var byteCount = Encoding.UTF8.GetByteCount(text);
        if (byteCount == 0)
        {
            MessageBox.Show(
                this,
                "Văn bản đang trống.",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
            return;
        }
        if (byteCount > TransferProtocol.MaximumTextBytes)
        {
            MessageBox.Show(
                this,
                "Văn bản không được vượt quá 256 KiB.",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
            return;
        }
        if (_queuedFiles.Count > 0 && !filesReplacementConfirmed &&
            MessageBox.Show(
                this,
                "Thay các file đang chờ bằng một tin nhắn văn bản?",
                "Near Transfer",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question) != MessageBoxResult.Yes)
        {
            return;
        }

        _queuedFiles.Clear();
        _queuedText = text;
        UpdateQueueSummary();
    }

    private async void PeerSend_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: PeerRowViewModel row })
        {
            await SendQueuedContentToPeerAsync(row.Peer);
        }
    }

    private async void SavedPeerSend_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: PeerRowViewModel row })
        {
            await SendQueuedContentToPeerAsync(row.Peer);
        }
    }

    private async Task SendQueuedContentToPeerAsync(Peer peer)
    {
        if (_isSending)
        {
            return;
        }
        if (_queuedFiles.Count == 0 && _queuedText is null)
        {
            MessageBox.Show(
                this,
                "Chọn file hoặc nhập/dán văn bản trước khi gửi.",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
            return;
        }
        if (_queuedFiles.Count > 0 && _queuedText is not null)
        {
            MessageBox.Show(
                this,
                "Một lần gửi chỉ có thể chứa file hoặc một tin nhắn văn bản.",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
            return;
        }

        var queuedFiles = _queuedFiles.ToArray();
        var paths = queuedFiles.Select(file => file.Path).ToArray();
        var text = _queuedText;
        var isText = text is not null;
        IReadOnlyList<TransferFile> transferFiles = isText
            ? [new TransferFile("Tin nhắn văn bản", Encoding.UTF8.GetByteCount(text!))]
            : queuedFiles.Select(file => new TransferFile(file.Name, file.Size)).ToArray();
        _isSending = true;
        var cancellation = new CancellationTokenSource();
        _sendCancellation = cancellation;
        UpdateQueueSummary();
        HomeView.Visibility = Visibility.Collapsed;
        TransferProgressView.StartTransfer(peer.Name, transferFiles, isText: isText);
        TransferProgressView.Visibility = Visibility.Visible;
        var timer = Stopwatch.StartNew();

        try
        {
            var progress = new Action<TransferProgress>(item =>
                _ = Dispatcher.InvokeAsync(() => TransferProgressView.UpdateProgress(item)));
            if (text is not null)
            {
                await _transferClient.SendTextAsync(
                    peer,
                    text,
                    Environment.MachineName,
                    progress,
                    cancellation.Token);
            }
            else
            {
                await _transferClient.SendFilesAsync(
                    peer,
                    paths,
                    Environment.MachineName,
                    progress,
                    cancellation.Token);
            }
            timer.Stop();
            _queuedFiles.Clear();
            _queuedText = null;
            UpdateQueueSummary();
            TransferProgressView.ShowSuccess(timer.Elapsed);
        }
        catch (TransferRejectedException exception)
        {
            timer.Stop();
            TransferProgressView.ShowRejected(exception.Message, timer.Elapsed);
        }
        catch (OperationCanceledException)
        {
            timer.Stop();
            TransferProgressView.ShowFailure("Đã hủy gửi.", timer.Elapsed);
        }
        catch (Exception exception)
        {
            timer.Stop();
            TransferProgressView.ShowFailure(
                $"Không gửi được {(isText ? "văn bản" : "file")}: {exception.Message}",
                timer.Elapsed);
        }
        finally
        {
            timer.Stop();
            _isSending = false;
            _sendCancellation?.Dispose();
            _sendCancellation = null;
            UpdateQueueSummary();
        }
    }

    private void TransferProgressView_CloseRequested(object? sender, EventArgs e)
    {
        _isReceiving = false;
        _activeReceiveTransferId = null;
        TransferProgressView.Visibility = Visibility.Collapsed;
        HomeView.Visibility = Visibility.Visible;
    }

    private void ChangeFolder_Click(object sender, RoutedEventArgs e)
    {
        var selectedFolder = LegacyFolderPicker.Show(
            this,
            _settings.SaveFolder ?? string.Empty,
            "Chọn thư mục lưu file nhận");
        if (string.IsNullOrWhiteSpace(selectedFolder))
        {
            return;
        }

        try
        {
            Directory.CreateDirectory(selectedFolder);
            _settings.SaveFolder = Path.GetFullPath(selectedFolder);
            _settingsStore.Save(_settings);
            SaveFolderText.Text = _settings.SaveFolder;
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or ArgumentException)
        {
            MessageBox.Show(
                this,
                $"Không đổi được thư mục lưu: {exception.Message}",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
    }

    private void OpenFolder_Click(object sender, RoutedEventArgs e)
    {
        var path = _settings.SaveFolder;
        if (string.IsNullOrWhiteSpace(path))
        {
            return;
        }

        try
        {
            Directory.CreateDirectory(path);
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            MessageBox.Show(
                this,
                $"Không mở được thư mục: {exception.Message}",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
    }

    private async void RefreshPeers_Click(object sender, RoutedEventArgs e)
    {
        RefreshPeersButton.IsEnabled = false;
        NetworkStatusText.Text = "Đang làm mới tìm kiếm thiết bị...";
        NetworkStatusText.Visibility = _peers.Count == 0
            ? Visibility.Visible
            : Visibility.Collapsed;
        try
        {
            await _discovery.RefreshAsync();
            UpdateLocalIpText();
            NetworkStatusText.Text = NoPeersMessage;
            NetworkStatusText.Visibility = _peers.Count == 0
                ? Visibility.Visible
                : Visibility.Collapsed;
        }
        catch (Exception exception) when (
            exception is InvalidOperationException or SocketException or ObjectDisposedException)
        {
            NetworkStatusText.Text = $"Không làm mới được danh sách: {exception.Message}";
            NetworkStatusText.Visibility = Visibility.Visible;
        }
        finally
        {
            RefreshPeersButton.IsEnabled = true;
        }
    }

    private void DeviceTab_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string tab })
        {
            return;
        }

        var showSavedPeers = string.Equals(tab, "saved", StringComparison.Ordinal);
        if (showSavedPeers == _showSavedPeers)
        {
            return;
        }

        _showSavedPeers = showSavedPeers;
        UpdateDevicePanelState(updateTabSelection: true, animateTabSelection: true);
        AnimateDeviceTabContent(showSavedPeers ? SavedDevicesView : NearbyDevicesView);
    }

    private async void ManualAddress_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new ManualAddressWindow { Owner = this };
        if (dialog.ShowDialog() == true && dialog.SelectedPeer is not null)
        {
            await SendQueuedContentToPeerAsync(dialog.SelectedPeer);
        }
    }

    private void SavePeer_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: PeerRowViewModel row })
        {
            return;
        }

        var key = PeerKey(row.Peer);
        var savedRow = _savedPeers.FirstOrDefault(saved => PeerKey(saved.Peer) == key);
        if (savedRow is not null)
        {
            return;
        }

        var setting = new SavedPeerSetting
        {
            Name = row.Name,
            Address = row.Peer.Address.ToString(),
            Port = row.Peer.Port
        };
        _settings.SavedPeers.Add(setting);
        try
        {
            _settingsStore.Save(_settings);
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException)
        {
            _settings.SavedPeers.Remove(setting);
            MessageBox.Show(
                this,
                $"Không lưu được thiết bị: {exception.Message}",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            return;
        }

        row.SetSaved(true);
        _savedPeers.Add(new PeerRowViewModel(row.Peer, isSaved: true));
        UpdateDevicePanelState();
    }

    private void RemoveSavedPeer_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: PeerRowViewModel row })
        {
            return;
        }

        RemoveSavedPeer(row);
    }

    private void RemoveSavedPeer(PeerRowViewModel row)
    {
        var savedIndex = _savedPeers.IndexOf(row);
        var setting = _settings.SavedPeers.FirstOrDefault(item =>
            string.Equals(item.Address, row.Peer.Address.ToString(), StringComparison.OrdinalIgnoreCase) &&
            item.Port == row.Peer.Port);
        if (setting is null)
        {
            return;
        }

        _savedPeers.Remove(row);
        _settings.SavedPeers.Remove(setting);
        try
        {
            _settingsStore.Save(_settings);
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException)
        {
            _settings.SavedPeers.Insert(LegacyMath.Clamp(savedIndex, 0, _settings.SavedPeers.Count), setting);
            _savedPeers.Insert(LegacyMath.Clamp(savedIndex, 0, _savedPeers.Count), row);
            MessageBox.Show(
                this,
                $"Không xóa được thiết bị đã lưu: {exception.Message}",
                "Near Transfer",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            return;
        }

        foreach (var livePeer in _peers.Where(peer => PeerKey(peer.Peer) == PeerKey(row.Peer)))
        {
            livePeer.SetSaved(false);
        }
        UpdateDevicePanelState();
    }

    private Task<bool> ConfirmIncomingOfferAsync(TransferOffer offer)
    {
        var completion = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        _ = Dispatcher.InvokeAsync(() =>
        {
            if (!IsLoaded)
            {
                completion.TrySetResult(false);
                return;
            }
            if (_isSending || _isReceiving || HomeView.Visibility != Visibility.Visible)
            {
                completion.TrySetResult(false);
                return;
            }

            var prompt = new ReceiveOfferWindow(offer, this);
            var accepted = prompt.ShowDialog() == true;
            if (accepted)
            {
                _isReceiving = true;
                _activeReceiveTransferId = offer.TransferId;
                TransferProgressView.StartTransfer(
                    offer.SenderName,
                    offer.Files,
                    isReceiving: true,
                    isText: offer.IsText);
                HomeView.Visibility = Visibility.Collapsed;
                TransferProgressView.Visibility = Visibility.Visible;
            }
            completion.TrySetResult(accepted);
        });
        return completion.Task;
    }

    private void OnPeersChanged(IReadOnlyList<Peer> peers)
    {
        _ = Dispatcher.InvokeAsync(() =>
        {
            var selected = DeviceList.SelectedItem as PeerRowViewModel;
            var selectedKey = selected is null ? null : PeerKey(selected.Peer);
            var incomingKeys = new HashSet<string>(peers.Select(PeerKey), StringComparer.Ordinal);

            for (var index = _peers.Count - 1; index >= 0; index--)
            {
                if (!incomingKeys.Contains(PeerKey(_peers[index].Peer)))
                {
                    _peers.RemoveAt(index);
                }
            }

            foreach (var peer in peers)
            {
                var existingIndex = FindPeerIndex(peer);
                var isSaved = _savedPeers.Any(saved => PeerKey(saved.Peer) == PeerKey(peer));
                if (existingIndex < 0)
                {
                    _peers.Add(new PeerRowViewModel(peer, isSaved));
                }
                else
                {
                    _peers[existingIndex].UpdatePeer(peer);
                    _peers[existingIndex].SetSaved(isSaved);
                }
            }

            foreach (var savedPeer in _savedPeers)
            {
                var livePeer = _peers.FirstOrDefault(
                    peer => PeerKey(peer.Peer) == PeerKey(savedPeer.Peer));
                if (livePeer is null)
                {
                    savedPeer.SetOnline(false);
                    continue;
                }

                savedPeer.UpdatePeer(livePeer.Peer);
                savedPeer.SetOnline(true);
            }

            if (selectedKey is not null)
            {
                DeviceList.SelectedItem = _peers.FirstOrDefault(peer => PeerKey(peer.Peer) == selectedKey);
            }

            UpdateDevicePanelState();
            NetworkStatusText.Text = NoPeersMessage;
            NetworkStatusText.Visibility = _peers.Count == 0
                ? Visibility.Visible
                : Visibility.Collapsed;
            UpdateLocalIpText();
        });
    }

    private void OnDiscoveryError(string message)
    {
        _ = Dispatcher.InvokeAsync(() =>
        {
            NetworkStatusText.Text = message;
            NetworkStatusText.Visibility = Visibility.Visible;
        });
    }

    private void OnTransferProgress(TransferProgress progress)
    {
        _ = Dispatcher.InvokeAsync(() =>
        {
            if (_isReceiving &&
                string.Equals(_activeReceiveTransferId, progress.TransferId, StringComparison.Ordinal) &&
                !TransferProgressView.IsComplete)
            {
                TransferProgressView.UpdateProgress(progress);
            }
        });
    }

    private void OnFileReceived(ReceivedFile file)
    {
        _ = Dispatcher.InvokeAsync(() =>
        {
            if (!_isReceiving ||
                !string.Equals(_activeReceiveTransferId, file.TransferId, StringComparison.Ordinal))
            {
                return;
            }

            TransferProgressView.MarkFileReceived(file.FileNumber);
            if (file.FileNumber >= file.FileCount)
            {
                TransferProgressView.ShowSuccess(TransferProgressView.Elapsed);
                _isReceiving = false;
                _activeReceiveTransferId = null;
            }
        });
    }

    private void OnTextReceived(ReceivedText received)
    {
        _ = Dispatcher.InvokeAsync(() =>
        {
            if (!_isReceiving ||
                !string.Equals(_activeReceiveTransferId, received.TransferId, StringComparison.Ordinal))
            {
                return;
            }

            TransferProgressView.MarkFileReceived(1);
            TransferProgressView.ShowSuccess(TransferProgressView.Elapsed);
            TransferProgressView.Visibility = Visibility.Collapsed;
            _isReceiving = false;
            _activeReceiveTransferId = null;
            ReceivedTextView.SetMessage(received.SenderName, received.Text);
            ReceivedTextView.Visibility = Visibility.Visible;
        });
    }

    private void ReceivedTextView_CopyRequested(object? sender, TextMessageActionEventArgs e)
    {
        try
        {
            Clipboard.SetText(e.Text, TextDataFormat.UnicodeText);
            ShowReceivedTextActionSuccess("Đã sao chép văn bản.", 1200);
        }
        catch (Exception exception) when (
            exception is ExternalException or InvalidOperationException)
        {
            ReceivedTextView.ShowActionStatus(
                $"Không sao chép được văn bản: {exception.Message}",
                isError: true);
        }
    }

    private void ReceivedTextView_SaveRequested(object? sender, TextMessageActionEventArgs e)
    {
        string? path = null;
        try
        {
            var bytes = Encoding.UTF8.GetBytes(e.Text);
            using (var output = FileNameRules.CreateUniqueFile(
                       _settings.SaveFolder ?? string.Empty,
                       "Tin nhắn.txt",
                       out path))
            {
                output.Write(bytes, 0, bytes.Length);
                output.Flush(flushToDisk: true);
            }

            ShowReceivedTextActionSuccess(
                $"Đã lưu {Path.GetFileName(path)}.",
                1500);
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or ArgumentException or NotSupportedException)
        {
            if (path is not null && File.Exists(path))
            {
                try
                {
                    File.Delete(path);
                }
                catch (IOException)
                {
                }
                catch (UnauthorizedAccessException)
                {
                }
            }
            ReceivedTextView.ShowActionStatus(
                $"Không lưu được văn bản: {exception.Message}",
                isError: true);
        }
    }

    private void ShowReceivedTextActionSuccess(string message, int closeDelayMilliseconds)
    {
        ReceivedTextView.ShowActionStatus(message);
        _receivedTextActionTimer.Stop();
        _receivedTextActionTimer.Interval = TimeSpan.FromMilliseconds(closeDelayMilliseconds);
        _receivedTextActionTimer.Start();
    }

    private void LeaveReceivedTextView()
    {
        _receivedTextActionTimer.Stop();
        ReceivedTextView.Visibility = Visibility.Collapsed;
        HomeView.Visibility = Visibility.Visible;
    }

    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape && ReceivedTextView.Visibility == Visibility.Visible)
        {
            LeaveReceivedTextView();
            e.Handled = true;
        }
    }

    private void OnTransferError(string message)
    {
        _ = Dispatcher.InvokeAsync(() =>
        {
            if (_isReceiving)
            {
                TransferProgressView.ShowFailure(message, TransferProgressView.Elapsed);
                _isReceiving = false;
                _activeReceiveTransferId = null;
            }
            else if (!_isSending)
            {
                ReceiveStatusText.Text = "Lỗi khi nhận nội dung";
            }
        });
    }

    private void UpdateQueueSummary()
    {
        var total = _queuedFiles.Sum(file => file.Size);
        var hasText = _queuedText is not null;
        var hasContent = hasText || _queuedFiles.Count > 0;
        QueueSummaryText.Text = hasText
            ? $"1 tin nhắn · {FormatSize(Encoding.UTF8.GetByteCount(_queuedText!))}"
            : _queuedFiles.Count == 0
            ? "Chưa có tệp nào"
            : $"{_queuedFiles.Count} file · {FormatSize(total)}";
        TextQueuePreview.Text = hasText ? CreateTextPreview(_queuedText!) : string.Empty;
        TextQueueCard.Visibility = hasText ? Visibility.Visible : Visibility.Collapsed;
        TextQueueCard.IsEnabled = !_isSending;
        QueueList.Visibility = _queuedFiles.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        ClearQueueButton.IsEnabled = hasContent && !_isSending;
        AddQueuedFilesButton.IsEnabled = !_isSending;
        QueueList.IsEnabled = !_isSending;
        QueueCard.Visibility = hasContent ? Visibility.Visible : Visibility.Collapsed;
        SourceTiles.Visibility = hasContent ? Visibility.Collapsed : Visibility.Visible;
    }

    private static string CreateTextPreview(string text)
    {
        var preview = string.Join(
            " ",
            text.Split(['\r', '\n', '\t'], StringSplitOptions.RemoveEmptyEntries)).Trim();
        if (preview.Length == 0)
        {
            return "(chỉ có khoảng trắng)";
        }
        return preview.Length > 150 ? preview.Substring(0, 147) + "…" : preview;
    }

    private void LoadSavedPeers()
    {
        foreach (var saved in _settings.SavedPeers)
        {
            if (!IPAddress.TryParse(saved.Address, out var address) ||
                address.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork ||
                saved.Port is < 1 or > 65535)
            {
                continue;
            }

            var name = string.IsNullOrWhiteSpace(saved.Name) ? address.ToString() : saved.Name;
            _savedPeers.Add(new PeerRowViewModel(
                new Peer(name, address, saved.Port, DateTimeOffset.UtcNow),
                isSaved: true,
                isOnline: false));
        }
    }

    private void UpdateDevicePanelState(
        bool updateTabSelection = false,
        bool animateTabSelection = false)
    {
        var hasSavedPeers = _savedPeers.Count > 0;
        SavedEmptyText.Visibility = hasSavedPeers ? Visibility.Collapsed : Visibility.Visible;
        SavedPeerList.Visibility = hasSavedPeers ? Visibility.Visible : Visibility.Collapsed;
        NearbyDevicesTabLabel.Text = $"Quanh đây ({_peers.Count})";
        SavedDevicesTabLabel.Text = $"Đã lưu ({_savedPeers.Count})";
        NearbyDevicesView.Visibility = _showSavedPeers ? Visibility.Collapsed : Visibility.Visible;
        SavedDevicesView.Visibility = _showSavedPeers ? Visibility.Visible : Visibility.Collapsed;

        if (updateTabSelection)
        {
            SetDeviceTabButtonStyle(NearbyDevicesTabButton, !_showSavedPeers, animateTabSelection);
            SetDeviceTabButtonStyle(SavedDevicesTabButton, _showSavedPeers, animateTabSelection);
        }
    }

    private void SetDeviceTabButtonStyle(Button button, bool isSelected, bool animate)
    {
        var backgroundColor = ((SolidColorBrush)FindResource(
            isSelected ? "AccentBrush" : "PanelRaisedBrush")).Color;
        var foregroundColor = ((SolidColorBrush)FindResource(
            isSelected ? "AccentTextBrush" : "TextBrush")).Color;

        SetButtonBrushColor(button, Control.BackgroundProperty, backgroundColor, animate);
        SetButtonBrushColor(button, Control.ForegroundProperty, foregroundColor, animate);
    }

    private static void SetButtonBrushColor(
        Button button,
        DependencyProperty property,
        Color targetColor,
        bool animate)
    {
        var currentBrush = button.GetValue(property) as SolidColorBrush;
        var brush = currentBrush?.CloneCurrentValue() ?? new SolidColorBrush(targetColor);
        button.SetValue(property, brush);

        if (!animate)
        {
            brush.BeginAnimation(SolidColorBrush.ColorProperty, null);
            brush.Color = targetColor;
            return;
        }

        brush.BeginAnimation(
            SolidColorBrush.ColorProperty,
            new ColorAnimation
            {
                From = brush.Color,
                To = targetColor,
                Duration = TimeSpan.FromMilliseconds(180),
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
            });
    }

    private void AnimateDeviceTabContent(UIElement view)
    {
        var animationVersion = ++_deviceTabAnimationVersion;
        view.BeginAnimation(UIElement.OpacityProperty, null);
        view.Opacity = 0;

        var translate = new TranslateTransform(0, 7);
        view.RenderTransform = translate;
        var duration = TimeSpan.FromMilliseconds(190);
        var easing = new CubicEase { EasingMode = EasingMode.EaseOut };
        var fadeIn = new DoubleAnimation
        {
            From = 0,
            To = 1,
            Duration = duration,
            EasingFunction = easing
        };
        fadeIn.Completed += (_, _) =>
        {
            if (animationVersion != _deviceTabAnimationVersion)
            {
                return;
            }

            view.BeginAnimation(UIElement.OpacityProperty, null);
            view.Opacity = 1;
            translate.BeginAnimation(TranslateTransform.YProperty, null);
            view.RenderTransform = Transform.Identity;
        };

        view.BeginAnimation(UIElement.OpacityProperty, fadeIn);
        translate.BeginAnimation(
            TranslateTransform.YProperty,
            new DoubleAnimation
            {
                From = 7,
                To = 0,
                Duration = duration,
                EasingFunction = easing
            });
    }

    private void UpdateLocalIpText()
    {
        var addresses = _discovery.GetLocalIpv4Addresses();
        var addressText = addresses.Count == 0
            ? "Chưa có địa chỉ IPv4"
            : string.Join(" · ", addresses.Select(address => address.ToString()));
        LocalIpText.Text = addressText;
    }

    private int FindPeerIndex(Peer peer)
    {
        for (var index = 0; index < _peers.Count; index++)
        {
            if (PeerKey(_peers[index].Peer) == PeerKey(peer))
            {
                return index;
            }
        }
        return -1;
    }

    private static string PeerKey(Peer peer) => $"{peer.Address}:{peer.Port}";

    private static string FormatSize(long bytes)
    {
        if (bytes < 1024)
        {
            return $"{bytes} B";
        }
        if (bytes < 1024L * 1024)
        {
            return $"{bytes / 1024d:0.0} KB";
        }
        if (bytes < 1024L * 1024 * 1024)
        {
            return $"{bytes / (1024d * 1024):0.0} MB";
        }
        return $"{bytes / (1024d * 1024 * 1024):0.00} GB";
    }

    private sealed record QueuedFile(string Path, string Name, long Size, string SizeText)
    {
        public string ExtensionLabel =>
            System.IO.Path.GetExtension(Name).TrimStart('.').ToUpperInvariant();
    }
}