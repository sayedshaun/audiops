# Contributing to Audiops

Thanks for helping. Bug reports, ideas, translations and code are all welcome.

## Reporting a bug or idea

Open an [issue](https://github.com/sayedshaun/audiops/issues/new/choose) and pick a template. For bugs, include the app version (Settings → About), your device or OS, the tool you used and the input format. Please don't attach recordings that contain private speech.

## Development setup

You need JDK 17 (with `javac`) and the Android SDK with platform 37. Android Studio provides both.

```sh
./gradlew :app:installDebug     # Android
./gradlew :desktopApp:run       # desktop
```

Install the pre-commit hook once per clone. It checks formatting and runs the unit tests before each commit:

```sh
make hooks
make help          # every developer command
```

Read [AGENTS.md](AGENTS.md) before changing code. It covers the module layout, the architecture rules, the Material 3 Expressive design system and the accessibility checklist.

## Making a change

1. Create a branch from `main` (`feature/…`, `fix/…`, `docs/…`).
2. Keep changes focused. Put user-visible text in `shared/src/commonMain/composeResources/values/strings.xml`.
3. Add or update tests. DSP changes need a test in `DspTest`; ViewModel changes a case in `AudiopsViewModelTest`.
4. Format and run the checks:
   ```sh
   make format      # ktlint via Spotless; rules in .editorconfig
   make ci          # format check, unit tests, Android lint, R8 release build
   ```
   Pull requests to `main` run the same checks in CI (Format and style, Unit tests, Android lint and build) and can merge only when they pass.
5. For UI changes, check light, dark and pure-black themes and rotation, and attach screenshots to the pull request.
6. Add a line to `CHANGELOG.md` under **Unreleased** for user-visible changes, and an entry to `CHANGES.md`.

## Releasing (maintainers)

Releases are built and published by [`.github/workflows/release.yml`](.github/workflows/release.yml).

1. Make sure `main` is green. In `CHANGELOG.md`, rename **Unreleased** to the new version and date, add a fresh **Unreleased** section, and merge that change.
2. Create a GitHub Release with a new tag `vMAJOR.MINOR.PATCH` (for example `v0.2.0`). A `-beta.1` suffix marks a pre-release.
3. Publishing the release starts the workflow. It runs the tests, then attaches these files to the release:
   - Android: `audiops-<version>-android.apk` and `.aab` (signed), or `…-android-unsigned.apk`
   - Linux: `.deb` and `.rpm`
   - Windows: `.msi` and `-setup.exe`
   - `SHA256SUMS.txt`
4. To rebuild an existing release, run the workflow manually (Actions → Release → Run workflow) with its tag.

The version name comes from the tag, and the Android version code is `MAJOR×1000000 + MINOR×1000 + PATCH`, so every new tag must be higher than the last.

### Signing the Android release

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Without them the workflow still publishes an unsigned APK. Keep the keystore safe and backed up: updates must be signed with the same key.
