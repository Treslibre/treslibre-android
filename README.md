<div align="center">
  <!-- Centered README header hack -->
  <!-- Note: Do not replace the obsolete align attribute with inline style, as GitHub may strip it. -->
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="logo_dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="logo_light.svg">
    <img width="500" src="logo_light.svg" alt="Treslibre Logo">
  </picture>
  <br><br>
</div>

---

> **Notice (AGPL-3.0 Section 5a):** Modified by Treslibre Project (2026) as part of Treslibre, an independent fork of Threema. Not affiliated with or endorsed by Threema AG.

# Treslibre for Android

This repository contains the source code of **Treslibre for Android**, an independent, FOSS-first fork of Threema for Android licensed under GNU AGPL-3.0.

Treslibre targets self-hosted / custom server deployments with Trust-On-First-Use (TOFU) server provisioning, the Lavender (`#D0CFEC` / `#58559A`) brand palette, and zero proprietary Google Play, Huawei HMS, Sentry, or upstream Threema cloud dependencies.

## Table of Contents

- [Bug Reports / Feature Requests / Security Issues](#issues)
- [Build Variant](#build-variant)
- [Building](#building)
- [Testing & Audits](#testing)
- [Code Organization / Architecture](#architecture)
- [Contributions](#contributions)
- [Translating](#translating)
- [License](#license)

## <a name="issues"></a>Bug Reports / Feature Requests / Security Issues

To report bugs, request features, or report security issues in Treslibre, please open an issue at
<https://github.com/Treslibre/treslibre-android/issues>. For coordinated vulnerability disclosure, see [SECURITY.md](SECURITY.md).

## <a name="build-variant"></a>Build Variant

Treslibre for Android builds a single FOSS product flavor:

| Flavor      | Application ID      | Description                                                    |
|-------------|---------------------|----------------------------------------------------------------|
| `treslibre` | `org.treslibre.app` | Self-hosted / custom server build with FOSS push/services only |

## <a name="building"></a>Building

### Prerequisites

- JDK 21 (e.g., Temurin or Amazon Corretto 21)
- Android SDK (`compileSdk = 37`, `buildToolsVersion = "35.0.0"`)
- Android NDK (`28.2.13676358`)
- `bash` shell
- `protoc` (Protocol Buffers compiler)
- Rust toolchain `1.97` (see `domain/libthreema/rust-toolchain.toml`) with Android targets:

```bash
TOOLCHAIN=$(grep channel domain/libthreema/rust-toolchain.toml | cut -d'"' -f2)
rustup target add --toolchain "$TOOLCHAIN" aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

Ensure `JAVA_HOME` points to JDK 21 and `ANDROID_HOME` (or `sdk.dir` in `local.properties`) points to your Android SDK.

### Via Command Line

```bash
# Build all ABIs (debug APK)
./gradlew :app:assembleTreslibreDebug --no-daemon

# Fast single-ABI build for arm64 devices
./gradlew :app:assembleTreslibreDebug -PcargoTargets=arm64 --no-daemon

# Release APK
./gradlew :app:assembleTreslibreRelease --no-daemon
```

By default, ABI-split APKs are built. Use `-PbuildUniversalApk` to also build a universal APK, or `-PnoAbiSplits` to build only a universal APK.

### Via Android Studio

Import the repository into [Android Studio](https://developer.android.com/studio/), select the `treslibreDebug` build variant, and run it on a device or emulator.

### Troubleshooting

**NDK is not installed**

> Cause 1: org.gradle.api.InvalidUserDataException: NDK is not installed

Install the NDK version defined by `ndkVersion` in `app/build.gradle.kts` (`28.2.13676358`) via *Settings > Languages & Frameworks > Android SDK > SDK Tools*.

## <a name="testing"></a>Testing & Audits

Run domain unit tests:

```bash
./gradlew :domain:test -PcargoTargets=arm64 --no-daemon
```

Run Android app unit tests:

```bash
./gradlew :app:testTreslibreDebugUnitTest -PcargoTargets=arm64 --no-daemon
```

Run the automated debranding audit:

```bash
bash scripts/audit-debrand.sh
```

## <a name="architecture"></a>Code Organization / Architecture

- `domain/` — Core protocol, state, and Rust `libthreema` UniFFI bindings (`domain/libthreema/`)
- `app/` — Android UI, services, resources, and `treslibre` flavor configuration
- `common/` & `commonAndroid/` — Shared utilities and Android helpers

## <a name="contributions"></a>Contributions

Contributions are welcome via GitHub pull requests at
<https://github.com/Treslibre/treslibre-android>. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## <a name="translating"></a>Translating

App localization strings live in `app/src/main/res/values*/strings.xml`.

## <a name="license"></a>License

Treslibre for Android (forked from Threema for Android) is licensed under the GNU Affero General Public License v3.

    Modified 2026 by Treslibre Project (Treslibre), an independent fork of Threema.
    Original work: Copyright (c) Threema AG

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License, version 3,
    as published by the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program. If not, see <https://www.gnu.org/licenses/>.

The full license text can be found in [`LICENSE.txt`](LICENSE.txt). Source code is available at
<https://github.com/Treslibre/treslibre-android>.
