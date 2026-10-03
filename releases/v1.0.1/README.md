# Near Transfer 1.0.1

## Release files

- [Near-Transfer-1.0.1.apk](https://github.com/huongdino443/near-transfer/releases/download/v1.0.1/Near-Transfer-1.0.1.apk) — signed APK for installation and distribution.
- [`CHANGELOG.md`](CHANGELOG.md) — changes in this release.
- [`SHA256SUMS.txt`](SHA256SUMS.txt) — SHA-256 checksums for the APK and release notes.

## Version and compatibility

- Version name: `1.0.1`
- Android version code: `20`
- Package: `com.nearbyshare.legacy`
- Minimum Android: Android 2.3 (API 9)
- Target SDK: `28`
- License: GPL-3.0-or-later

The APK is release-signed. The private keystore and signing passwords are not
included. This release was tested on the latest Android version available at
the time of testing. Android 2.3 (API 9) is the minimum supported version, but
testing on Android 2.3 hardware has not been completed.

This release is intended for direct APK distribution. Target SDK 28 does not
meet current Google Play target API requirements.

## Network and security

Transfers use plain HTTP over the local Wi-Fi network. Receiver approval is a
consent step, not encryption. Use a trusted network and do not send sensitive
information over an untrusted network.