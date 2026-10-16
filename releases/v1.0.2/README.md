# Near Transfer 1.0.2

Changes since Android APK 1.0.1 and Windows installer 1.0.0.

## Changes

### Android APK (1.0.1 → 1.0.2)

- Added an expandable selected-files panel for reviewing and clearing the send queue. Newly selected files append without replacing existing selections.
- Added a receive-location card and full-screen folder picker. The chosen destination is saved and used for incoming files and text.
- Updated received-file handling to use the configured destination. Received APKs now open in Android's package installer, with the required install-permission flow on Android 8 and later.
- Fixed a receive-folder picker crash on Android 2.3/API 10 caused by the unavailable `TextView.getMinWidth()` method.

### Windows installer (1.0.0 → 1.0.2)

- Moved initial receive-folder selection into the installer so the app is ready to use on first launch. Changes made later in the app are preserved.
- Updated the Windows application and installer version metadata to 1.0.2.

## Compatibility

- Android: Android 2.3 (API 9) or later.
- Windows: Windows 7 SP1 or later, x86 or x64. The installer downloads .NET Framework 4.8 from Microsoft if it is not already installed.

See [CHANGELOG.md](CHANGELOG.md) for the detailed change list.
