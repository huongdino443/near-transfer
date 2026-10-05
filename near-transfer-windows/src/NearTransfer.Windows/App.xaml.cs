using NearTransfer.Core;
using System.IO;
using System.Windows;

namespace NearTransfer.Windows;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        var settingsStore = new SettingsStore();
        var settings = settingsStore.Load();
        if (string.IsNullOrWhiteSpace(settings.SaveFolder) || !Directory.Exists(settings.SaveFolder))
        {
            var defaultPath = settings.SaveFolder;
            if (string.IsNullOrWhiteSpace(defaultPath))
            {
                defaultPath = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),
                    "Near Transfer");
            }

            var setup = new FolderSetupWindow(defaultPath ?? string.Empty);
            if (setup.ShowDialog() != true)
            {
                Shutdown();
                return;
            }

            settings.SaveFolder = setup.SelectedFolder;
            settingsStore.Save(settings);
        }

        var mainWindow = new MainWindow(settings, settingsStore);
        MainWindow = mainWindow;
        mainWindow.Show();
    }
}