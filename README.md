# Nord Pass (Android)

Digital gym carnet for Estació del Nord: logs in to MICLUB, shows the member
photo/name and a QR pass, and refreshes the QR before it expires.

- Download page: <https://velkan14.github.io/nord-pass/>
- Source and releases: <https://github.com/velkan14/nord-pass>
- Original design: [`../ANDROID_APP_DESIGN.md`](../ANDROID_APP_DESIGN.md)

## Toolchain

- Gradle wrapper included (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`), pinned to Gradle 9.6.0.
- JDK 17 for builds.
- Android SDK platform **37** (compileSdk) and Build Tools 36.0.0+; targetSdk remains 36.
- AGP 9.5.2 with **built-in Kotlin** (no `org.jetbrains.kotlin.android` plugin), Kotlin 2.2.21, Compose BOM 2026.09.00.
- Android package identity: `dev.damado.nordpass`.

## What is implemented

- Gradle project, manifest, theme, `MainActivity` and `Application`.
- Keystore AES-256-GCM encryptor + DataStore-backed `SecretsStore`
  (cookie and username always persisted; password only when opted in).
- SettingsStore with server/local QR mode.
- OkHttp client with an encrypted `SSESS` cookie jar.
- `MiclubApi` implementing the login form scrape/POST, carnet page JSON parsing
  (token, lifetime, member name and Base64 photo), `/qrcode` PNG fetch and a
  best-effort `/user/logout` GET. It never calls `/user-active/...` and never
  persists or logs the member details.
- `SessionManager` state machine: cookie first, silent re-login only when a
  password was saved, otherwise login form.
- Compose screens: Login, Carnet (member photo/name above a centered
  high-brightness QR with a thin Material linear progress bar, manual refresh
  plus foreground auto-refresh before token expiry) and Settings (QR mode
  toggle, confirmed logout that clears cookie + saved password, read-only base
  URL).

## What is intentionally not here

- No background refresh, alarms or WorkManager; auto-refresh only while the QR screen is in the foreground.
- No multi-account support, no editable base URL.
- No `FLAG_SECURE`, no biometric gate.
- No HTTP mocking framework or HTML fixtures yet.

## Build and release

Debug build (local):

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Releases are built by GitHub Actions. Push a tag `vX.Y.Z`:

```sh
git tag v0.1.0
git push origin v0.1.0
```

The workflow sets `versionName` from the tag, `versionCode` from the Actions run
number, builds a signed APK, attaches it (plus its SHA-256) to a GitHub Release,
and updates `docs/release.json` on `main` for the download page.

The signing keystore must stay out of this repo. CI receives it through these
repository secrets:

- `RELEASE_KEYSTORE_BASE64` — base64 of the JKS file
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_PASSWORD`
- `RELEASE_KEY_ALIAS`

## Security notes

- The QR token is effectively a temporary gym key. Treat any captured QR as
  sensitive, but the app deliberately does not block screenshots.
- The password is encrypted at rest only when saved; nothing is logged.
- The Keystore key is not user-authentication-bound, per the design assumption
  that the device is secure and only its unlock holder can use it.
- Losing the signing keystore means future APKs cannot be installed as updates
  over existing ones — keep an offline backup.
