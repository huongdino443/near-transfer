using NearTransfer.Core;
using System.Windows;
using System.Windows.Threading;

namespace NearTransfer.Windows;

public partial class ReceiveOfferWindow : Window
{
    private readonly DispatcherTimer _timeoutTimer;

    public ReceiveOfferWindow(TransferOffer offer, Window owner)
    {
        InitializeComponent();
        Owner = owner;
        SenderText.Text = offer.SenderName;
        AddressText.Text = offer.SenderAddress.ToString();
        RequestHeading.Text = offer.IsText ? "YÊU CẦU NHẬN VĂN BẢN" : "YÊU CẦU NHẬN FILE";
        AcceptButton.Content = offer.IsText ? "Nhận văn bản" : "Nhận file";
        SummaryText.Text = offer.IsText
            ? $"1 tin nhắn · {FormatSize(offer.TotalBytes)}"
            : $"{offer.Files.Count} file · {FormatSize(offer.TotalBytes)}";
        FilesList.ItemsSource = offer.Files.Select(file => new OfferFileRow(file.Name, FormatSize(file.Size)));
        _timeoutTimer = new DispatcherTimer { Interval = TimeSpan.FromMinutes(2) };
        _timeoutTimer.Tick += (_, _) =>
        {
            _timeoutTimer.Stop();
            DialogResult = false;
        };
        Loaded += (_, _) => _timeoutTimer.Start();
        Closed += (_, _) => _timeoutTimer.Stop();
    }

    private void Accept_Click(object sender, RoutedEventArgs e) => DialogResult = true;

    private void Decline_Click(object sender, RoutedEventArgs e) => DialogResult = false;

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

    private sealed record OfferFileRow(string Name, string SizeText);
}