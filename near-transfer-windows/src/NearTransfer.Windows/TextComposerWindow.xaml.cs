using NearTransfer.Core;
using System.Text;
using System.Windows;

namespace NearTransfer.Windows;

public partial class TextComposerWindow : Window
{
    public TextComposerWindow(string? initialText = null)
    {
        InitializeComponent();
        MessageInput.Text = initialText ?? string.Empty;
        MessageInput.CaretIndex = MessageInput.Text.Length;
        UpdateByteCount();
        Loaded += (_, _) => MessageInput.Focus();
    }

    public string SelectedText { get; private set; } = string.Empty;

    private void MessageInput_TextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e) =>
        UpdateByteCount();

    private void UpdateByteCount()
    {
        var byteCount = Encoding.UTF8.GetByteCount(MessageInput.Text);
        var withinLimit = byteCount is > 0 and <= TransferProtocol.MaximumTextBytes;
        ByteCountText.Text = $"{byteCount:N0} / {TransferProtocol.MaximumTextBytes:N0} byte";
        ByteCountText.Foreground = byteCount > TransferProtocol.MaximumTextBytes
            ? (System.Windows.Media.Brush)FindResource("ErrorBrush")
            : (System.Windows.Media.Brush)FindResource("MutedBrush");
        AddButton.IsEnabled = withinLimit;
    }

    private void Add_Click(object sender, RoutedEventArgs e)
    {
        if (Encoding.UTF8.GetByteCount(MessageInput.Text) > TransferProtocol.MaximumTextBytes)
        {
            return;
        }
        SelectedText = MessageInput.Text;
        DialogResult = true;
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => DialogResult = false;
}