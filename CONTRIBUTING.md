# Contributing to Treslibre for Android

## Submitting Patches

Contributions are welcome via GitHub pull requests at
<https://github.com/Treslibre/treslibre-android>.

Before opening a pull request, please ensure:

- Your changes comply with the GNU Affero General Public License v3 (AGPL-3.0).
- `bash scripts/audit-debrand.sh` exits with code `0`.
- Relevant Gradle builds and unit tests pass (`./gradlew :app:assembleTreslibreDebug -PcargoTargets=arm64 --no-daemon`).

## Bug Reports / Feature Requests

Please open an issue at <https://github.com/Treslibre/treslibre-android/issues>.

## Translations

App localization strings live in `app/src/main/res/values*/strings.xml` and can be updated via pull requests.
