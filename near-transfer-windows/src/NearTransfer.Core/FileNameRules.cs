using System.Text;

namespace NearTransfer.Core;

public static class FileNameRules
{
    private static readonly HashSet<string> ReservedNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    public static string SanitizeFileName(string? requestedName)
    {
        if (string.IsNullOrWhiteSpace(requestedName))
        {
            return "received-file";
        }

        var normalized = requestedName!.Replace('\\', '/');
        var slash = normalized.LastIndexOf('/');
        var leaf = slash >= 0 ? normalized.Substring(slash + 1) : normalized;
        var result = new StringBuilder(Math.Min(leaf.Length, 120));

        foreach (var character in leaf)
        {
            if (char.IsControl(character) || character is ':' or '*' or '?' or '"' or '<' or '>' or '|')
            {
                continue;
            }
            result.Append(character);
        }

        var safe = result.ToString().Trim().TrimEnd('.');
        if (safe.Length == 0 || safe is "." or "..")
        {
            safe = "received-file";
        }

        if (safe.Length > 120)
        {
            safe = safe.Substring(0, 120).TrimEnd('.', ' ');
        }

        var extension = Path.GetExtension(safe);
        var stem = extension.Length > 0 ? safe.Substring(0, safe.Length - extension.Length) : safe;
        if (ReservedNames.Contains(stem))
        {
            safe = "_" + safe;
        }

        return safe;
    }

    public static FileStream CreateUniqueFile(string directory, string requestedName, out string path)
    {
        Directory.CreateDirectory(directory);
        var safeName = SanitizeFileName(requestedName);
        var extension = Path.GetExtension(safeName);
        var baseName = extension.Length > 0
            ? safeName.Substring(0, safeName.Length - extension.Length)
            : safeName;

        for (var suffix = 0; suffix < 10_000; suffix++)
        {
            var candidate = suffix == 0
                ? safeName
                : $"{baseName} ({suffix}){extension}";
            path = Path.Combine(directory, candidate);
            try
            {
                return new FileStream(
                    path,
                    FileMode.CreateNew,
                    FileAccess.Write,
                    FileShare.None,
                    128 * 1024,
                    FileOptions.Asynchronous | FileOptions.SequentialScan);
            }
            catch (IOException) when (File.Exists(path))
            {
                // Try the next numbered name without overwriting an existing file.
            }
        }

        throw new IOException("Không tạo được tên file nhận duy nhất.");
    }
}