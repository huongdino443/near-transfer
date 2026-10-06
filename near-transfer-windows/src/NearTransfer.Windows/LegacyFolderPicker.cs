using System.Windows;
using System.Windows.Interop;
using Forms = System.Windows.Forms;

namespace NearTransfer.Windows;

internal static class LegacyFolderPicker
{
    public static string? Show(Window owner, string initialPath, string description)
    {
        using var dialog = new Forms.FolderBrowserDialog
        {
            Description = description,
            SelectedPath = initialPath,
            ShowNewFolderButton = true
        };

        var ownerHandle = new WindowInteropHelper(owner).Handle;
        var result = dialog.ShowDialog(new WindowHandleOwner(ownerHandle));
        return result == Forms.DialogResult.OK ? dialog.SelectedPath : null;
    }

    private sealed class WindowHandleOwner(IntPtr handle) : Forms.IWin32Window
    {
        public IntPtr Handle { get; } = handle;
    }
}