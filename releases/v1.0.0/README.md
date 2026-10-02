# Near Transfer 1.0.0

## Release files

- [`Near-Transfer-1.0.0.apk`](Near-Transfer-1.0.0.apk) — signed APK for installation and distribution.
- [`CHANGELOG.md`](CHANGELOG.md) — features in this release.
- [`SHA256SUMS.txt`](SHA256SUMS.txt) — SHA-256 checksums for the APK and release notes.

## Version and compatibility

- Version name: `1.0.0`
- Android version code: `19`
- Package: `com.nearbyshare.legacy`
- Minimum Android: Android 2.3 (API 9)
- Target SDK: `28`
- Signing certificate subject: `CN=Near Transfer Release, O=Near Transfer, C=VN`

The APK is release-signed. The private keystore and signing passwords are not
included in this release. Real Android 2.3 / Galaxy Y hardware testing has not
yet been completed.

## Network behavior

Transfers use plain HTTP over the local Wi-Fi network. Receiver approval is a
consent step, not encryption. Use a trusted network and do not send sensitive
information over an untrusted network.