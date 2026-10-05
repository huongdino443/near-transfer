#if NET48
using System.Web.Script.Serialization;
#else
using System.Text.Json;
#endif

namespace NearTransfer.Core;

public sealed class AppSettings
{
    public string? SaveFolder { get; set; }
    public List<SavedPeerSetting> SavedPeers { get; set; } = [];
}

public sealed class SavedPeerSetting
{
    public string Name { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public int Port { get; set; } = TransferProtocol.HttpPort;
}

public sealed class SettingsStore
{
    private readonly string _settingsPath;

    public SettingsStore()
    {
        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        _settingsPath = Path.Combine(appData, "Near Transfer", "settings.json");
    }

    public AppSettings Load()
    {
        try
        {
            if (!File.Exists(_settingsPath))
            {
                return new AppSettings();
            }

#if NET48
            return new JavaScriptSerializer().Deserialize<AppSettings>(File.ReadAllText(_settingsPath))
                ?? new AppSettings();
#else
            return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(_settingsPath))
                   ?? new AppSettings();
#endif
        }
#if NET48
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or InvalidOperationException or ArgumentException)
#else
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or JsonException)
#endif
        {
            return new AppSettings();
        }
    }

    public void Save(AppSettings settings)
    {
        var directory = Path.GetDirectoryName(_settingsPath)
            ?? throw new InvalidOperationException("Không xác định được thư mục cấu hình.");
        Directory.CreateDirectory(directory);
        var temporaryPath = _settingsPath + ".tmp";
#if NET48
        File.WriteAllText(temporaryPath, new JavaScriptSerializer().Serialize(settings));
        if (File.Exists(_settingsPath))
        {
            File.Replace(temporaryPath, _settingsPath, null);
        }
        else
        {
            File.Move(temporaryPath, _settingsPath);
        }
#else
        File.WriteAllText(temporaryPath, JsonSerializer.Serialize(settings));
        File.Move(temporaryPath, _settingsPath, overwrite: true);
#endif
    }
}