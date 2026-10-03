# Near Transfer 1.0.1

Bug-fix release.

- Keep listening for peer discovery requests after active searching pauses, so
  both devices do not need to scan at the same time.
- Improve bidirectional device discovery on Wi-Fi and hotspot networks while
  excluding cellular-only interfaces.
- Remove redundant success messages after sending or receiving files and text;
  progress screens and error messages remain available.
- Open received files with Android's app chooser through a read-only content URI.
- Preserve compatibility with Android 2.3 (API 9).