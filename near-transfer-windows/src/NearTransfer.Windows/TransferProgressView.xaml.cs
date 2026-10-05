using NearTransfer.Core;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace NearTransfer.Windows;

public partial class TransferProgressView : UserControl
{
    private readonly ObservableCollection<TransferFileRow> _files = [];
    private readonly Stopwatch _stopwatch = new();
    private readonly DispatcherTimer _elapsedTimer;
    private string _peerName = string.Empty;
    private long _totalBytes;
    private long _bytesSent;
    private bool _isComplete;
    private bool _isReceiving;
    private bool _isTextTransfer;

    public TransferProgressView()
    {
        InitializeComponent();
        FilesItemsControl.ItemsSource = _files;
        _elapsedTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromMilliseconds(250)
        };
        _elapsedTimer.Tick += (_, _) => UpdateElapsedText();
    }

    public event EventHandler? CloseRequested;
    public bool IsComplete => _isComplete;
    public TimeSpan Elapsed => _stopwatch.Elapsed;

    public void StartTransfer(
        string peerName,
        IReadOnlyList<TransferFile> files,
        bool isReceiving = false,
        bool isText = false)
    {
        _peerName = peerName;
        _isReceiving = isReceiving;
        _isTextTransfer = isText;
        _totalBytes = files.Sum(file => file.Size);
        _bytesSent = 0;
        _isComplete = false;

        _files.Clear();
        foreach (var file in files)
        {
            _files.Add(new TransferFileRow(file.Name, file.Size));
        }
        FilesScrollViewer.ScrollToTop();

        PageTitle.Text = isText
            ? isReceiving ? "Đang nhận văn bản" : "Đang gửi văn bản"
            : isReceiving ? "Đang nhận tập tin" : "Đang gửi tập tin";
        SummaryHeading.Text = isText
            ? isReceiving ? "VĂN BẢN ĐÃ NHẬN" : "VĂN BẢN ĐÃ GỬI"
            : isReceiving ? "DỮ LIỆU ĐÃ NHẬN" : "DỮ LIỆU ĐÃ GỬI";
        ProgressPercentText.Text = "0%";
        TransferredText.Text = $"{FormatSize(0)} / {FormatSize(_totalBytes)}";
        FileCountText.Text = ContentCount;
        TransferDetailsText.Text = $"{ContentCount} · 00:00";
        CurrentFileText.Text = isReceiving
            ? $"Đã chấp nhận từ {_peerName}. Đang chờ dữ liệu..."
            : $"Đang chờ {_peerName} xác nhận...";
        CloseButton.Visibility = Visibility.Collapsed;
        DrawProgressRing(0);
        _stopwatch.Restart();
        _elapsedTimer.Start();
    }

    public void UpdateProgress(TransferProgress progress)
    {
        if (_isComplete || _files.Count == 0)
        {
            return;
        }

        var index = LegacyMath.Clamp(progress.FileIndex, 0, _files.Count - 1);
        for (var fileIndex = 0; fileIndex < index; fileIndex++)
        {
            MarkFileComplete(_files[fileIndex]);
        }

        var current = _files[index];
        var filePercent = progress.TotalBytes <= 0
            ? 100
            : LegacyMath.Clamp(progress.TransferredBytes * 100d / progress.TotalBytes, 0, 100);
        current.Progress = filePercent;
        current.ProgressVisibility = Visibility.Visible;
        var action = _isReceiving ? "Đang nhận" : "Đang truyền";
        current.StatusText =
            $"{action} · {FormatSize(progress.TransferredBytes)} / {FormatSize(progress.TotalBytes)}";

        _bytesSent = Math.Min(
            _totalBytes,
            _files.Take(index).Sum(file => file.Size) + Math.Max(0, progress.TransferredBytes));
        var totalPercent = _totalBytes == 0
            ? 100
            : LegacyMath.Clamp(_bytesSent * 100d / _totalBytes, 0, 100);
        UpdateSummary(totalPercent);
        CurrentFileText.Text = _isTextTransfer
            ? $"{action} tin nhắn văn bản"
            : $"{action} file {index + 1}/{_files.Count}: {progress.FileName}";
    }

    public void ShowSuccess(TimeSpan duration)
    {
        var heading = _isTextTransfer
            ? _isReceiving ? "Đã nhận văn bản" : "Đã gửi văn bản"
            : _isReceiving ? "Đã nhận xong" : "Đã gửi xong";
        var detail = _isTextTransfer
            ? _isReceiving
                ? $"Đã nhận văn bản từ {_peerName}."
                : $"Đã gửi văn bản đến {_peerName}."
            : _isReceiving
                ? $"Đã nhận {_files.Count} file từ {_peerName}."
                : $"Đã gửi {_files.Count} file đến {_peerName}.";
        CompleteView(heading, detail);
        _bytesSent = _totalBytes;
        UpdateSummary(100);
        TransferDetailsText.Text = $"{ContentCount} · {FormatDuration(duration)}";
        foreach (var file in _files)
        {
            MarkFileComplete(file);
        }
    }

    public void ShowRejected(string message, TimeSpan duration)
    {
        CompleteView("Gửi chưa hoàn tất", message);
        TransferDetailsText.Text = $"{ContentCount} · {FormatDuration(duration)}";
        MarkRemainingFiles("Chưa gửi");
    }

    public void ShowFailure(string message, TimeSpan duration)
    {
        CompleteView(
            _isReceiving ? "Nhận không thành công" : "Gửi không thành công",
            message);
        TransferDetailsText.Text = $"{ContentCount} · {FormatDuration(duration)}";
        MarkRemainingFiles("Chưa hoàn tất");
    }

    public void MarkFileReceived(int fileNumber)
    {
        var index = fileNumber - 1;
        if (index >= 0 && index < _files.Count)
        {
            MarkFileComplete(_files[index]);
        }
    }

    private void CompleteView(string heading, string detail)
    {
        _isComplete = true;
        _stopwatch.Stop();
        _elapsedTimer.Stop();
        PageTitle.Text = "Kết quả truyền";
        SummaryHeading.Text = heading.ToUpperInvariant();
        CurrentFileText.Text = detail;
        CloseButton.Visibility = Visibility.Visible;
    }

    private void UpdateSummary(double percent)
    {
        var roundedPercent = LegacyMath.Clamp((int)Math.Round(percent), 0, 100);
        ProgressPercentText.Text = $"{roundedPercent}%";
        TransferredText.Text = $"{FormatSize(_bytesSent)} / {FormatSize(_totalBytes)}";
        DrawProgressRing(roundedPercent);
    }

    private void UpdateElapsedText()
    {
        TransferDetailsText.Text =
            $"{ContentCount} · {FormatDuration(_stopwatch.Elapsed)}";
    }

    private string ContentCount => _isTextTransfer ? "1 tin nhắn" : FormatCount(_files.Count);

    private void DrawProgressRing(double percent)
    {
        ProgressRingComplete.Visibility = percent >= 99.95
            ? Visibility.Visible
            : Visibility.Collapsed;
        ProgressArc.Visibility = percent >= 99.95
            ? Visibility.Collapsed
            : Visibility.Visible;
        if (percent <= 0 || percent >= 99.95)
        {
            ProgressArc.Data = null;
            return;
        }

        const double center = 52;
        const double radius = 47;
        var angle = percent / 100d * 360d - 90d;
        var radians = angle * Math.PI / 180d;
        var end = new Point(
            center + radius * Math.Cos(radians),
            center + radius * Math.Sin(radians));
        var geometry = new PathGeometry();
        var figure = new PathFigure
        {
            StartPoint = new Point(center, center - radius),
            IsClosed = false
        };
        figure.Segments.Add(new ArcSegment(
            end,
            new Size(radius, radius),
            0,
            percent >= 50,
            SweepDirection.Clockwise,
            true));
        geometry.Figures.Add(figure);
        ProgressArc.Data = geometry;
    }

    private void MarkFileComplete(TransferFileRow file)
    {
        file.Progress = 100;
        file.ProgressVisibility = Visibility.Collapsed;
        file.StatusText = "Đã xong";
    }

    private void MarkRemainingFiles(string status)
    {
        foreach (var file in _files)
        {
            if (file.StatusText == "Đang chờ")
            {
                file.StatusText = status;
            }
            file.ProgressVisibility = Visibility.Collapsed;
        }
    }

    private static string FormatCount(int count) =>
        count == 1 ? "1 mục" : $"{count} mục";

    private static string FormatDuration(TimeSpan duration) =>
        duration.TotalHours >= 1
            ? duration.ToString(@"hh\:mm\:ss")
            : duration.ToString(@"mm\:ss");

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

    private void CloseButton_Click(object sender, RoutedEventArgs e)
    {
        if (_isComplete)
        {
            CloseRequested?.Invoke(this, EventArgs.Empty);
        }
    }
}

public sealed class TransferFileRow : INotifyPropertyChanged
{
    private double _progress;
    private string _statusText = "Đang chờ";
    private Visibility _progressVisibility = Visibility.Collapsed;

    public TransferFileRow(string name, long size)
    {
        Name = name;
        Size = size;
        SizeText = FormatSize(size);
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public string Name { get; }
    public long Size { get; }
    public string SizeText { get; }

    public double Progress
    {
        get => _progress;
        set => SetField(ref _progress, value);
    }

    public string StatusText
    {
        get => _statusText;
        set => SetField(ref _statusText, value);
    }

    public Visibility ProgressVisibility
    {
        get => _progressVisibility;
        set => SetField(ref _progressVisibility, value);
    }

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

    private void SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
        {
            return;
        }
        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }
}