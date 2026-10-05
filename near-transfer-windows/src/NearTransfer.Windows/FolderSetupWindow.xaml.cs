using System.IO;
using System.Windows;

namespace NearTransfer.Windows;

public partial class FolderSetupWindow : Window
{
    public FolderSetupWindow(string initialFolder)
    {
        InitializeComponent();
        FolderPathBox.Text = initialFolder;
    }

    public string SelectedFolder { get; private set; } = string.Empty;

    private void Browse_Click(object sender, RoutedEventArgs e)
    {
        var selectedFolder = LegacyFolderPicker.Show(
            this,
            FolderPathBox.Text,
            "Chọn thư mục lưu file nhận");
        if (!string.IsNullOrWhiteSpace(selectedFolder))
        {
            FolderPathBox.Text = selectedFolder;
            ErrorText.Text = string.Empty;
        }
    }

    private void Continue_Click(object sender, RoutedEventArgs e)
    {
        var path = FolderPathBox.Text.Trim();
        if (path.Length == 0)
        {
            ErrorText.Text = "Hãy chọn một thư mục lưu.";
            return;
        }

        try
        {
            var fullPath = Path.GetFullPath(path);
            Directory.CreateDirectory(fullPath);
            var testFile = Path.Combine(fullPath, $".near-transfer-check-{Guid.NewGuid():N}");
            using (File.Create(testFile))
            {
            }
            File.Delete(testFile);
            SelectedFolder = fullPath;
            DialogResult = true;
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or ArgumentException or NotSupportedException)
        {
            ErrorText.Text = $"Không thể dùng thư mục này: {exception.Message}";
        }
    }
}