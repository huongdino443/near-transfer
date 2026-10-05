using System.Windows;
using System.Windows.Controls;

namespace NearTransfer.Windows;

public partial class ReceivedTextView : UserControl
{
    private string _message = string.Empty;

    public ReceivedTextView()
    {
        InitializeComponent();
    }

    public event EventHandler<TextMessageActionEventArgs>? CopyRequested;
    public event EventHandler<TextMessageActionEventArgs>? SaveRequested;

    public void SetMessage(string senderName, string message)
    {
        SenderText.Text = senderName;
        _message = message;
        MessageText.Text = message;
        ActionStatusText.Text = string.Empty;
        ActionStatusText.Visibility = Visibility.Collapsed;
    }

    public void ShowActionStatus(string message, bool isError = false)
    {
        ActionStatusText.Text = message;
        ActionStatusText.Foreground = (System.Windows.Media.Brush)FindResource(
            isError ? "ErrorBrush" : "AccentBrush");
        ActionStatusText.Visibility = Visibility.Visible;
    }

    private void Copy_Click(object sender, RoutedEventArgs e) =>
        CopyRequested?.Invoke(this, new TextMessageActionEventArgs(_message));

    private void Save_Click(object sender, RoutedEventArgs e) =>
        SaveRequested?.Invoke(this, new TextMessageActionEventArgs(_message));
}

public sealed class TextMessageActionEventArgs(string text) : EventArgs
{
    public string Text { get; } = text;
}