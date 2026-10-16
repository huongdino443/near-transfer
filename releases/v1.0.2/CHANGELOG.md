# Changelog

## Near Transfer 1.0.2

### Changes

#### Android APK (1.0.1 → 1.0.2)

- Added an expandable selected-files panel with a queue preview and a Clear All action. Adding files appends unique selections instead of replacing the current queue.
- Added a receive-location card that shows the active folder and opens a full-screen folder browser. The selected location is persisted and used for received files and saved text.
- Replaced the partial-screen receive-folder dialog with a dedicated full-screen picker; removed its redundant title while keeping Cancel, Select This Folder, and back navigation.
- Updated received-file handling to work with the configured receive location. Received APKs launch Android's package installer; Android 8 and later use the required unknown-source install permission flow.

#### Windows installer (1.0.0 → 1.0.2)

- Moved initial receive-folder selection from the app's first launch to the installer. The selected path is applied when the app starts, and later in-app changes are preserved.
- Updated the Windows application and installer version metadata to 1.0.2.

### Compatibility

- Android 2.3 (API 9) or later.
- Windows 7 SP1 or later (x86 and x64). .NET Framework 4.8 is required; the installer downloads it from Microsoft when missing.
