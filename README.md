# Near Transfer - Fast file sharing over Wi-Fi

Near Transfer is a native Android app for sending files and text messages between
nearby devices on the same Wi-Fi network, without a cloud relay.

## Features

- Discover nearby devices over the local Wi-Fi network.
- Send up to 20 files in one batch.
- The receiver reviews and accepts the file list before any file data is sent.
- Send one text message separately from file and media transfers.
- Save received files to `Download/Near Transfer/`.
- Open received files with another installed app using a read-only content URI.

## Compatibility

- Minimum Android version: Android 2.3 (API 9).
- Release target SDK: API 28.
- Designed for direct APK distribution and legacy Android devices.
- The current target SDK does not meet Google Play's current target API
  requirements.
- A real Android 2.3 / Galaxy Y device test is still required.

## Build

Requirements:

- JDK 21
- Android SDK Platform 35
- Gradle Wrapper (included)

From the repository root:

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleUnsigned
```

Official signed releases are built by the maintainer. The release keystore and
its passwords are private and must never be committed. Signed APKs are published
as assets on the original repository's GitHub Releases page; APK build outputs
are intentionally excluded from the source repository.

## Network and security

Transfers use plain HTTP over the local network. Receiver approval is a consent
step, not encryption; another device or operator on the same network may be able
to inspect traffic. Use a trusted Wi-Fi network and do not send sensitive
information over untrusted networks.

Near Transfer does not use a cloud relay. Local-network discovery and transfer
still require the Wi-Fi network to allow device-to-device traffic.

## Project notes

See [`PROTOCOL.md`](PROTOCOL.md) for the local transfer protocol.

Only releases published in the original repository's **Releases** section are
official Near Transfer releases. Forks may build and distribute their own
versions under the MIT license, but those versions are not official releases.

## License

This project is distributed under the MIT License. See [`LICENSE`](LICENSE).