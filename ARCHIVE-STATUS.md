# Project archive status — 2026-10-06

This branch is a recovery snapshot, not an official release. It is based on the current `main` branch and preserves the Windows and Java ME source alongside the Android source already on `main`.

## Android

- Android source and project documentation are inherited from `main`.
- Official Android releases remain available under their existing tags and GitHub Releases.

## Windows

- Source is preserved in `near-transfer-windows/`.
- Windows 1.0.0 is published under tag `windows-v1.0.0`; its installer remains in GitHub Releases.
- The user confirmed the Windows installer and app work. The tested Windows version and architecture were not recorded.

## Java ME — in progress, not released

- Source and build instructions are in `near-transfer-java-me/`; the MIDlet targets Wi-Fi-capable Nokia S40/S60 devices and uses the NWS1 LAN protocol shared with Android and Windows.
- Build with JDK 8 and Maven by running `./build.sh` from that directory. The build creates a CLDC 1.1 preverified JAR and JAD.
- Development JAR/JAD outputs v1 through v34 are preserved in `near-transfer-java-me/dist/`. The current source's default output is v34 (`near-transfer-java-me-auto-folders`). These snapshots are not official releases.
- Before release, continue device/emulator validation of keypad and Nokia softkey behavior, Wi-Fi discovery and transfers in both directions, persisted receive location, and file-write permission behavior. Keep file/network permission declarations optional as requested.
- Verify CLDC 1.1 preverification and class-file version 45.3 for every class in the final JAR.
- Keep the published Android and Windows releases unchanged; hold a combined platform release until the Java ME Wi-Fi edition is ready.
