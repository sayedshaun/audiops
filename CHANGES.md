# Changelog

Entries are appended in order. Newest entries are at the bottom.

## 2026-11-12: Production hardening and Material 3 redesign (`feature/production-hardening-material3`)

### Fixed
- Processing could not be cancelled. Enhancement and transcription now stop between sections when the user presses Cancel or the activity is destroyed (`runInterruptible` plus `throwIfCancelled()` checks in every loop).
- Decoding treated any exception as a decode failure, so a cancellation would have been reported as an error. Cancellation now passes through the decoder unchanged.
- Rotating the device lost the enhanced result, the transcript, and the busy state, so Save could disappear mid-job. State now lives in `AudioOpsViewModel`, and the selected tool, recording and language survive process death through `SavedStateHandle`.
- Enhanced WAV files in the cache were never deleted when replaced or discarded. They are now deleted on discard, on a new recording, and when the ViewModel is cleared.
- Whisper and Bangla each carried a copy of the model download and SHA-256 verification code. The copies now share one `ModelFiles` path. A download that fails verification is deleted and reported.
- Enhancement section progress was reported from many concurrent `launch` calls. Progress now flows through `ProcessingListener` and a single state flow.
- Saved files were named `enhanced.wav` and `transcript.txt` regardless of the input. They now use the recording name, for example `clip-enhanced.wav`.

### Changed
- Redesigned the UI with Material 3: large top app bar on the home screen, tool screens with back navigation, numbered steps, tonal cards, icons instead of text glyphs, and a readable 640 dp content width on tablets and in landscape.
- Live status for downloads and sections (for example "Downloading model 2 of 6…", "Enhancing section 3 of 8…"), with determinate progress where the amount of work is known.
- Transcript actions: copy to clipboard and save as a text file.
- Full light and dark colour schemes. Android 12 and newer use dynamic colour, and older versions use the brand palette.
- Edge-to-edge layout, using `enableEdgeToEdge()`.
- The "Remove noise" tool is shown as disabled with a "Soon" label instead of opening an empty page.
- Moved the hard-coded strings to `res/values/strings.xml`.
- Pipelines moved out of the composable into `AudioPipelines`. `AudioDecoder`, the transcribers and `ResembleEnhancer` report progress through a listener.
- Release builds enable R8 and resource shrinking, with keep rules for the native runtimes. Signing is configured through environment variables.
- Backup is disabled, with data-extraction and full-backup rules for Android 12+ and older versions.

### Added
- JVM unit tests (14 tests) for WAV output, section and overlap arithmetic, cancellation, the ODE solver, DSP output shapes, and model file naming.
- GitHub Actions workflow running unit tests, lint, and debug and release builds.
- `CLAUDE.md`, `PROGRESS.md` and this changelog.

### Dependencies
- `androidx.lifecycle` (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`) 2.11.0.
- `material-icons-extended` (version set by the Compose BOM).
- JUnit 4.13.2 for unit tests.

### Verified
- `./gradlew :app:testDebugUnitTest` passes (14 tests).
- `./gradlew :app:lintDebug` reports 0 errors and 4 warnings: target SDK 36 (compile SDK 37), a newer Gradle wrapper, a newer LiteRT release, and the ChromeOS ABI note. These are intentionally left for a separate decision.
- `./gradlew :app:assembleRelease` succeeds with R8.
- On an Android emulator: the home, Enhance and Transcribe screens render in light and dark themes. Transcription of the sample clip produced a transcript from the cached Whisper model, and the result survives rotation.
- Not verified: the enhancement pipeline end to end (it needs a ~360 MB model download), the Bangla model path, and behaviour on physical devices.

## 2026-11-12: Material 3 Expressive redesign, settings, rename to Audiops (`feature/material3-theme-settings`)

### Added
- Settings screen (Android 16+ grouped style): theme (System / Light / Dark), dynamic colour, pure black dark theme, default spoken language, keep screen on while processing, downloaded-model storage with confirmed delete, version, privacy note, and open-source licence links.
- Settings persist with Jetpack DataStore. The splash screen (core-splashscreen) stays up until they load, so the first frame already uses the chosen theme.
- System bar icons follow the in-app theme. Predictive back is enabled (`enableOnBackInvokedCallback`).
- Haptics: confirm on results, reject on errors, toggle feedback on switches, segment ticks on selections.
- Component previews (`ui/Previews.kt`) in light and dark.
- `AGENTS.md` expanded into the full agent guide; `CLAUDE.md` imports it.

### Changed
- Material 3 Expressive: `MaterialExpressiveTheme` with `MotionScheme.expressive()`, the expressive shape scale, `MaterialShapes` icon containers, `LargeFlexibleTopAppBar`, shape-morphing buttons, connected toggle-button groups, `LoadingIndicator` and wavy progress, emphasized typography, spring-based screen transitions, rounded icons.
- Screens are stateless: they receive state and `…Actions` callbacks instead of the ViewModel.
- Renamed the app from "AudioOps" to **Audiops** (app label, Gradle project, classes, styles, docs). The Gradle property is now `audiopsAbi`.

### Dependencies
- `androidx.compose.material3` 1.4.0 → 1.5.0-rc01 (Expressive APIs).
- Added `androidx.datastore:datastore-preferences` 1.3.0 and `androidx.core:core-splashscreen` 1.2.0.
- Enabled `buildConfig` for the version shown in Settings.

### Verified
- 14 unit tests pass; lint 0 errors (4 warnings, unchanged and intentional); release build passes R8.
- Emulator: home, Transcribe and Settings render in light and dark; theme and pure-black changes apply instantly and survive an app restart; no crashes in logcat.

## 2026-11-13: Kotlin Multiplatform, full audio toolkit, Activity tab (`feature/kotlin-multiplatform`, `feature/audio-toolkit`)

### Added
- **Kotlin Multiplatform.** New `:shared` module (Compose Multiplatform) holds the UI, ViewModel, settings, history and strings (`commonMain`) and the JVM audio core (`jvmShared`). `:app` is the Android host and engine; new `:desktopApp` runs on Linux and Windows (packages DEB/RPM/MSI/EXE).
- **Nine DSP tools**, pure Kotlin and streaming (constant memory): Noise reduction (spectral subtraction with a learned noise profile), Remove silence, Trim, Fade, Volume (peak, BS.1770 loudness with look-ahead limiter, gain), Speed and pitch (WSOLA + cubic resampler), Equalizer (six presets), Convert (anti-aliased resampling), Analyze (loudness, peak, RMS, clipping, silence, DC).
- **Job queue and Activity tab.** Start jobs from any tool and keep working; jobs run in order. The Activity tab shows progress and history (persisted, up to 100), with a detail sheet to play, save, reopen or delete results.
- **Bottom navigation** (Material 3 Expressive short navigation bar; navigation rail on wide windows) with Tools, Activity and Settings tabs; categorised tool grid with a distinct expressive shape per tool.
- **Waveform preview and playback** of the input and every result (MediaPlayer on Android, Java Sound on desktop).
- **Settings:** processed-files storage and clear history.
- **Tests:** 13 DSP correctness tests (filters, fades, limiter ceiling, STFT reconstruction, denoise SNR, stretch keeps pitch, octave shift, resample length, silence removal, BS.1770 reference loudness, end-to-end pipeline), a throughput test, and 3 desktop engine tests. CI runs them.

### Fixed
- **AI enhancement was killed by Android's low-memory killer** on a 4 GB device (2.3 GB resident): all five models were loaded at once. It now runs staged, one model at a time; peak memory measured 461 MB.
- **Decoding was ~10× slower than necessary:** the MediaCodec loop blocked up to 10 ms on every buffer. A 3-minute stereo file went from 45 s to ~16 s on the emulator.
- **Decoder resampled with linear interpolation and no anti-aliasing;** it now uses the shared anti-aliased cubic resampler.
- **Cancelling during a model download** reported "Processing failed: thread interrupted" and waited for the file to finish: downloads now read in 64 KB chunks with cancellation checks, and interrupt-caused I/O errors count as cancellation.
- **Download progress** only moved per file (tens of MB); it now shows byte-level progress.
- **New results appeared below the fold;** the job card now scrolls into view when a job starts and finishes.

### Changed
- Compose Multiplatform 1.12.2 with CMP Material 3 1.12.0-alpha03 (Expressive); Android resolves the matching Jetpack Material 3 1.5.0-alpha22 (the rc01 pin was removed to avoid API mismatches at runtime).
- ViewModel emits typed state, failures and messages; the UI localizes them (Compose resources).
- Settings and history use multiplatform DataStore; existing Android settings carry over.
- Sliders are continuous and snap in code (discrete Expressive sliders drew dense tick rows); cancelled jobs show neutral, not error, styling.

### Verified (Android emulator, x86_64, 4 GB)
- Every DSP tool on a 3-minute 48 kHz stereo WAV: decode ~16 s (cached afterwards); Noise reduction 15 s; others 2–3 s; Speed 1.5× +4 st within ~38 s. Analyze 5 s.
- Saved output checked byte-exact: 1.5× of 3:00 → 2:00.0 (5,292,000 samples).
- Queue, Activity history across restarts, detail sheet playback, save via the system dialog, cancel during download.
- Lint 0 errors (3 known warnings); R8 release build succeeds.

## 2026-11-13 (later): Crash-proofing AI enhancement and the release build (`feature/audio-toolkit`)

### Fixed
- **Release build crashed on launch** (R8): LiteRT pulls in Play asset delivery → WorkManager + Room 2.5, whose generated database is found by reflection. Added keep rules; the R8 build now starts, loads history and runs jobs (verified on the emulator).
- **AI enhancement could still be killed** while loading the vocoder (~2.4–3 GB peak, more than a 4 GB phone can give one app). Enhancement now runs in an isolated `:enhance` process (`EnhanceService`): if the system reclaims it, the app stays open and shows "The AI model ran out of memory on this device…". Cancel stops the worker process immediately; the worker is stopped after every run to return its memory.
- Activity sheet selection is reset in an effect instead of during composition.

### Changed
- On desktop, AI tools show an "Android only" badge and are disabled; Settings hides phone-only sections (keep screen on, transcription default) and words dynamic colour per platform.
- Desktop ViewModel is created directly in `main()` (no ViewModelStoreOwner dependency).

### Added
- Off-screen desktop render tests (`DesktopRenderTest`) write PNGs of the Tools, tool, Settings and narrow layouts to `desktopApp/build/screenshots`.

### Verified
- 28 shared tests, 7 desktop tests, lint 0 errors, androidTest compiles.
- Emulator: transcription of real English speech is accurate (11 s clip in ~26 s); cancel during enhancement kills the worker within 3 s and the app stays alive; an out-of-memory enhancement fails gracefully with the app open; landscape shows the navigation rail; dark mode checked; R8 release build runs.
- Not verified: a completed AI enhancement (needs a device with more free RAM than this 4 GB emulator).

## 2026-11-13: Public repository, README and release pipeline (`docs/readme-release-pipeline`)

### Added
- Repository renamed to `audiops` and made public, with description and topics.
- New README: banner, logo, badges, screenshots, feature and download tables, platform support, build instructions, architecture diagram, roadmap and acknowledgements. Assets in `docs/images/` (SVG logo and banner, screenshots).
- `CONTRIBUTING.md` (setup, conventions, release process, signing secrets), `SECURITY.md`, issue forms and a pull request template.
- `.github/workflows/release.yml`: on a published release (or manually for a tag) it validates the `vX.Y.Z` tag, runs tests, builds the Android APK and AAB (signed when secrets exist, otherwise an unsigned APK), Linux `.deb`/`.rpm` and Windows `.msi`/`.exe`, and attaches them with `SHA256SUMS.txt`.
- Release version flows from the tag: `-PaudiopsVersion`/`-PaudiopsVersionCode` set the Android version and the desktop package version; the desktop app reads it at runtime.
- Desktop installers and window use the Audiops icon.

### Fixed
- Android signing read `AUDIOOPS_*` environment variables (missed by the rename) instead of the documented `AUDIOPS_*`, and treated empty CI secrets as set.

### Verified
- Locally: a `.deb` builds with the icon and metadata; the CI signing path (base64 keystore → env) produces an APK signed with the given key, versionName 1.2.3 / versionCode 1002003, plus an AAB; desktop tests pass; README rendered through GitHub's Markdown API.
- Not yet run on GitHub Actions: pushing workflow files needs a token with the `workflow` scope.

## 2026-11-13: README Models section (`docs/readme-models`)

### Added
- README "Models" section: which model each tool and language option uses (Resemble Enhance for Enhance speech; Whisper Tiny for Auto, English and Hindi; Bangla Zipformer for Bangla; no model for DSP tools), with runtime, files, download size (from the pinned Hugging Face revisions), license, how each pipeline runs, memory needs, verification, storage and deletion.

## 2026-11-13: Tests, formatter, pre-commit hook, Makefile and changelog (`chore/tests-format-hooks`)

### Added
- **Formatter:** Spotless 8.9 with ktlint 1.8 (`intellij_idea` style, 140 columns) for every `.kt` and `.gradle.kts` file; rules in the new `.editorconfig`. The whole codebase was reformatted once (formatting only, no behaviour change).
- **Unit tests** (shared tests 28 → 53):
  - `ModelTest`: JSON round trip of every `AudioOperation` and `JobSpec`, stable stored type names, unknown fields and transient progress; progress maths; `latestJob`; waveform equality.
  - `RepositoryTest`: history save/load order, active jobs excluded, 100-job cap, corrupt history; settings defaults, round trip and unknown enum values.
  - `AudiopsViewModelTest` with a fake engine: success saved to history, waveform, failures, blank transcript → no speech, unsupported tools, one job at a time in queue order, cancelling running and queued jobs, delete and clear history, back navigation, snackbar message.
- **`Makefile`:** `format`, `format-check`, `test`, `lint`, `build`, `release`, `check` (format + unit tests), `ci` (everything CI runs), `hooks`, `run-desktop`, `install`, `clean`, `help`.
- **Pre-commit hook** (`.githooks/pre-commit`, installed with `make hooks`): when Kotlin or Gradle files are staged, runs `spotlessCheck` and the unit tests and blocks the commit on failure (`AUDIOPS_SKIP_TESTS=1` for format only).
- **`CHANGELOG.md`:** user-facing release notes in Keep a Changelog format, with everything so far under Unreleased.

### Changed
- CI (`android.yml`) runs on pull requests to `main` and pushes to `main` as three jobs, Format and style, Unit tests, and Android lint and build, which can be made required checks for merging. Superseded runs of the same PR are cancelled. The release workflow also runs `spotlessCheck`.
- `History.kt` → `HistoryRepository.kt` and `WavAudio.kt` → `WavWriter.kt` (ktlint file naming); Compose resource wildcard imports replaced by explicit imports.
- AGENTS.md: agents must run `make check` before every commit, must not bypass the hook or weaken rules or tests, and must keep `CHANGELOG.md`; new code style and test map sections. CLAUDE.md, CONTRIBUTING.md, README and the PR template point to the Makefile.

### Verified
- `make ci` passes locally (spotlessCheck, 53 shared + 7 desktop tests, lint 0 errors, R8 release build); androidTest sources compile.
- The shared suite passed 25 reruns (one earlier run failed once and did not reproduce; ViewModel scopes are now cancelled after each test).
- The hook passes on clean code and blocks a deliberately misformatted file.

## 2026-11-13: Download table, architecture image and licence (`docs/readme-models`)

### Changed
- README Download table lists file types and install commands instead of full versioned file names (the release page shows those).
- README architecture is an SVG image (`docs/images/architecture-{light,dark}.svg`, switched by GitHub's theme) instead of a Mermaid diagram that rendered poorly.

### Added
- `LICENSE`: Apache License 2.0 (canonical text) for Audiops' own code, and `NOTICE` with the copyright line and third-party attributions. README licence section and badge, AGENTS.md licence rules and `CHANGELOG.md` updated. Desktop installers now pick up `LICENSE` automatically.

### Fixed
- `AudiopsViewModelTest` failed once on CI (`IllegalStateException` in `TestMainDispatcher`): a cancelled ViewModel from the previous test resumed on `Main` while the next test swapped it. Teardown now cancels each ViewModel and joins its coroutines before resetting `Main`. Passed 8 local reruns and 4 full-suite runs pinned to 2 cores.

## 2026-11-13: Prepare release 0.1.0 (`release/prepare-0.1.0`)

### Added
- Resemble Enhance downloads are pinned to commit `d74080f` and every file is verified by SHA-256 through `ModelFiles.ensure` (digests from the Hugging Face API; `mel-filters.f32` also checked by download). `ModelFiles.ensure` deletes older revisions of a model once the new one is complete, so the previous unversioned Resemble cache (~360 MB) is reclaimed. `HubModels.prepare` (unpinned, unverified) removed. New `ModelFilesTest`.
- Per-ABI release APKs (`-PaudiopsSplitApks=true`): arm64-v8a 40 MB, armeabi-v7a 28 MB, x86_64 47 MB, x86 39 MB, plus the 148 MB universal APK.
- Release workflow: builds the AAB and the split APKs in separate Gradle runs (AGP cannot do both at once), names them `audiops-<v>-android-<abi>.apk`, has a dry-run mode (manual run with an empty tag builds everything without publishing), and fills empty release notes from the matching `CHANGELOG.md` section.
- `CHANGELOG.md`: `0.1.0` section cut.
- Release signing key (RSA 4096, valid to 2056, alias `audiops`) created outside the repository in `~/.audiops-release/` with an upload script for the GitHub secrets.

### Verified
- Locally signed release build: every APK and the AAB verify with the new certificate (SHA-256 `c147c216…`); `aapt2` reports versionName 0.1.0 and only arm64-v8a native code in the arm64 APK.
- Compose's Windows version check accepts MAJOR = 0, so `v0.1.0` packages on Windows.
- Not verified on the emulator: it has an older build signed with a different key, and replacing it would wipe its data.

## 2026-11-13: Desktop installer version fix (`fix/desktop-package-version`)

### Fixed
- The release dry run failed in the Android job: `-PaudiopsVersion` applies to every module, and configuring `:desktopApp` rejected `0.0.0-dryrun` because RPM versions cannot contain `-`. A pre-release tag such as `v0.2.0-beta.1` would have failed the same way. Installer versions now drop the suffix (`0.2.0`); the app still shows the full version.

### Verified
- `:desktopApp` configures with `0.0.0-dryrun`; `packageDeb` with `0.2.0-beta.1` produces `audiops_0.2.0-1_amd64.deb`. The dry run's Linux and Windows jobs had already built DEB, RPM, MSI and EXE.

## 2026-11-13: Desktop display name and Linux package test (`fix/desktop-display-name`)

### Fixed
- Desktop installers showed the app as "audiops" (Linux menu entry, Windows Start menu and install folder) because jpackage uses `packageName` as the display name. It is now "Audiops"; the Debian/RPM package name stays `audiops` (`linux.packageName`).

### Verified
- Built the 0.1.0 `.deb` (96 MB; Depends lists ALSA, X11 and GL libraries) and unpacked it: menu entry `Name=Audiops`, launcher `/opt/audiops/bin/Audiops`, icon. The packaged app opens its window (screenshot: Tools tab, dark theme, navigation rail, AI tools marked "Android only") and closes cleanly.
- Ran every DSP tool through the packaged runtime and jars (a test main class swapped into the launcher config, on a 20 s 48 kHz stereo WAV): denoise, remove silence (20 s → 14.5 s), trim (5.000 s), fade, loudness (−16.0 LUFS), peak (−1.0 dB), gain, speed 1.5× (13.3 s), equalizer, resample; waveform and analysis. The trimmed Java runtime has every module the engine needs.
- `packageRpm` cannot run here (no `rpmbuild`); the release dry run builds it on CI.

## 2026-11-13: Release v0.1.0 (`docs/release-0.1.0`)

### Released
- Published https://github.com/sayedshaun/audiops/releases/tag/v0.1.0 from `79069a4`. The release workflow built and attached the Linux DEB/RPM, Windows MSI/EXE, checksums and release notes from `CHANGELOG.md`.
- The signing secrets could not be uploaded to GitHub from this session, so CI produced unsigned APKs. The Android APKs (arm64-v8a, armeabi-v7a, x86_64, x86, universal) and the AAB were built locally from the same commit with the release key, uploaded in their place, and `SHA256SUMS.txt` was regenerated. CONTRIBUTING.md documents this procedure.

### Verified
- Downloaded every asset from the release: all ten checksums match; the APK certificate is the release key (`c147c216…`), versionName 0.1.0, versionCode 1000.
- Installed the released x86_64 APK on the emulator as an update over the locally tested build: launches, history kept, no crashes. Before the release, the same signed build passed English transcription (accurate), noise reduction and the pinned, digest-verified download of all six Resemble files (enhancement itself runs out of memory on the 4 GB emulator and fails gracefully).
- The released `.deb` unpacks with `Name=Audiops` and the app opens.

## 2026-11-13: Secrets and licence audit (`fix/third-party-licenses`)

### Audited
- Secrets: no keystores, `.env`, `local.properties`, tokens or private keys in the working tree or any commit; the keystore password appears nowhere in history, the tree or the 11 published v0.1.0 assets; workflows read secrets only from the GitHub store; `~/.audiops-release` is `700`/`600`. GitHub currently holds no repository secrets.
- Licences: listed every runtime dependency of the Android release and the desktop app and the licence of each Hugging Face model repository.

### Fixed
- Bundled components whose licences require their notice in binary distributions were not credited: ONNX Runtime (MIT, inside the sherpa-onnx AAR), JTransforms and JLargeArrays (BSD 2-Clause), Apache Commons Math (Apache 2.0 NOTICE), LiteRT. Their texts are now in `licenses/`.
- The app now bundles `composeResources/files/third_party_notices.txt` (all licence texts) and shows it under Settings → Open-source licenses → Third-party notices. The list gains Whisper, LiteRT and the DSP libraries; Android-only AI components are hidden on desktop. Whisper was labelled Apache 2.0; the weights are MIT (OpenAI) and only the ONNX export is Apache 2.0 (README Models table fixed too).
- `NOTICE` and the README acknowledgements list every component, including the Play libraries LiteRT pulls in (Android SDK License, not open source) and the desktop OpenJDK runtime (GPL v2 + Classpath Exception, notices shipped in `lib/runtime/legal`).
- New `ThirdPartyNoticesTest` fails if a file in `licenses/` is not bundled verbatim; AGENTS.md documents the steps for a new dependency.

### Verified
- `make ci` passes (64 tests). On the emulator (release-signed build installed over 0.1.0, data kept): the licence list and the notices dialog render and scroll in light and dark.

### Note
- The v0.1.0 binaries predate this fix; a 0.1.1 release would ship the notices.

## 2026-11-13: Prepare release 0.1.1 (`release/0.1.1`)

### Changed
- `CHANGELOG.md`: `0.1.1` section cut (bundled third-party licence notices). Default local version is 0.1.1.

## 2026-11-13: Enhance speech memory fix and vocoder re-export (`fix/enhance-memory`)

### Diagnosed
- Reported as "crashing" on a Moto g96 (8 GB, Android 16, 0.1.0 from the release). Logcat while reproducing: the `:enhance` process went from ~0.3 GB to 3 GB, then 4.7 GB RSS as the vocoder loaded; `lowmemorykiller` killed about 20 other apps (ChatGPT, Chrome, Play Store, the keyboard) and then `com.audiops.app:enhance`. The app reported "ran out of memory" as designed; there was no code crash.
- Desktop LiteRT reproduced it: the INT8 vocoder peaks at 4.5 GB (2.9 GB without XNNPACK). Causes in the conversion: grouped transposed convolutions (anti-aliased upsampling) became one `TRANSPOSE_CONV` per channel (1,156 ops), replicate padding/`unfold` became `GATHER`/`TILE`, and weight-only INT8 dequantizes to float32 at load. That vocoder also differs from PyTorch by 20.6 dB SNR.

### Fixed
- XNNPACK weight cache for every Resemble model (`noBackupFilesDir/xnnpack-cache`, keyed by revision; caches of older revisions are deleted; counted and deleted with the models).
- New vocoder `vocoder-mobile-fp16.tflite`, re-exported from the original PyTorch weights with identical, XNNPACK-friendly ops (zero-stuffing + depthwise conv, edge concatenation, one batched matmul for the location-variable convolution); each replacement checked against PyTorch (< 1e-6). 1.32 GB peak instead of 4.55 GB, 2.5 s instead of 7.1 s per second of audio, 62.2 dB SNR vs PyTorch; full pipeline 63.9 dB vs a float32 vocoder (old INT8: 26.3 dB). Published to the Hugging Face repo as commit `b92eaae` with a `mobile` dtype, an XNNPACK weight cache in `inference.kt`, README corrections and the conversion scripts.
- The app uses the `mobile` set pinned to `b92eaae` (all six digests verified against the Hub). `ModelFiles.ensure` moves unchanged files from the previous revision instead of downloading them, so updating fetches only the vocoder. UI hint, README and AGENTS.md sizes updated (~570 MB).

### Verified
- 4 GB emulator: enhancement completes end to end (it was always killed before); peak anonymous memory 1.5 GB while the vocoder's weight cache is written, then ~0.65 GB. Update path: models 1–5 reused, only the vocoder downloaded and verified.
- Installing the fix on the Moto g96 was blocked (CLAUDE.md: no installs on the phone unless asked), so the phone has not run the new build yet.
- `make ci` passes; new tests for the `mobile` file names and for reusing files across revisions.

## 2026-11-13: Phone verification and resumable downloads (`fix/resumable-downloads`)

### Verified on the Moto g96
- Uninstalled Audiops (all app data) and installed the signed 0.1.1 arm64 APK. A fresh Enhance run downloaded all six files and enhanced the 11-second test clip: "Enhance speech finished". Peak anonymous memory 819 MB (was 4.7 GB); the low-memory killer trimmed only 5 cached background processes (oom_adj 945–985), where the old build killed about 20 apps including the keyboard and Chrome. About 3 minutes of processing for 11 s of audio.
- The first attempt failed with "Connection reset" on LTE during the download; the retry kept the five verified files and fetched the vocoder.
- Cleaned up: test file removed from Downloads, test jobs cleared from Activity.

### Fixed
- `HubModels.download` retries up to five times (growing, interruptible pauses) on I/O errors, HTTP 429 and 5xx, and resumes from a stable `<name>.part` file with HTTP range requests (a 200 reply restarts cleanly, 416 discards the part). Partial downloads survive app restarts; SHA-256 verification still decides. New `DownloadResumeTest` drops a connection halfway through and checks the resumed file is byte-identical.

## 2026-11-13: Release v0.1.1 (`docs/release-0.1.1`)

### Released
- Published https://github.com/sayedshaun/audiops/releases/tag/v0.1.1 from `f1df2e3`. CI built and attached the Linux DEB/RPM and Windows MSI/EXE and wrote the notes from `CHANGELOG.md`. The Android APKs and AAB were built locally from the same commit with the release key (signing secrets are still not on GitHub), replaced the unsigned CI APKs, and `SHA256SUMS.txt` was regenerated (10 files).
- Verified the downloaded arm64 APK: checksum OK, release certificate `c147c216…`, versionName 0.1.1, versionCode 1001. The same build passed Enhance speech on the Moto g96 from a fresh install.

## 2026-11-13: Background jobs on Android (`feature/foreground-service`)

### Added
- `JobService`: a foreground service in the main process that runs while any job is queued or running (type `mediaProcessing` on Android 15+, `dataSync` on 10–14), holds a partial wake lock (6 h cap, matching the platform's daily limit; `onTimeout` cancels the jobs and stops), and shows an ongoing notification: tool name, progress text and bar, "N more waiting", and Cancel / Cancel all. Updates are throttled to two per second.
- `JobNotifications`: follows `AudiopsState.jobs`, publishes the notice and starts the service when work begins, and posts a "finished"/"failed" notification (tap opens the Activity tab) for jobs that end while no Activity is started. Two channels: "Running jobs" (low importance) and "Finished jobs".
- `AudiopsViewModel.cancelAll()` cancels queued jobs and the running one with a single "Cancelled" message; covered by a new `AudiopsViewModelTest` case.
- Notification text in shared Compose resources (`ui/NotificationText.kt`, `Job.progressLabel()` alongside the composable `progressText()`); new strings under "Android notifications".
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `POST_NOTIFICATIONS` (asked once, when the first job starts) and `WAKE_LOCK`. Monochrome `ic_notification`.

### Changed
- The Android `AudiopsViewModel` now belongs to `AudiopsApplication` instead of `MainActivity`, so swiping the task away no longer cancels jobs. Tab, tool, input and language are saved into the Activity's instance state and restored after process death, as before.

### Verified (Android 17 emulator, debug build upgraded over 0.1.0 with data kept)
- Permission prompt on the first job; service foreground with type `0x2000` (media processing); `Audiops:jobs` wake lock held; main process oom_adj 50 and `:enhance` 100 while backgrounded.
- Swiped the task away from Recents during enhancement: same process kept running, progress continued to 99%, "Enhance speech finished." notification posted, service stopped, wake lock released, worker gone. Tapping the notification opened the Activity tab with the result.
- Cancel from the notification stopped the job (service and `:enhance` gone, job shown as Cancelled).
- After `am kill` in the background, relaunching from Recents restored the Remove silence screen and the chosen file.
- `make ci` passes.

## 2026-11-13: Tools tab layout (`fix/home-header`)

### Changed
- Removed the non-interactive "On device" badge from the Tools top bar (and its string).
- Hero banner: the decorative shapes now sit in a `matchParentSize` layer, so they no longer force a 200 dp minimum height. Title `titleLargeEmphasized`, body `bodyMedium`, waveform 20 dp, padding 20/16 dp: 386 px instead of 525 px tall on a 1080 px-wide phone. Copy: "Transform audio, without the cloud." / "Every tool runs on this device. Nothing is uploaded."
- Tools grid: a `LazyColumn` of rows in place of an adaptive `LazyVerticalGrid`. The column count still comes from the 168 dp minimum tile width, but each category is split into balanced rows (`balancedRows`), and the tiles in a row share the width and match heights. Wide windows have no empty cells: on desktop each category fills one row. On phones the third Edit tool spans the full width. New `BalancedRowsTest`.

### Verified
- Android 17 emulator, light and dark; desktop off-screen renders at 1375 px and 520 px.

## 2026-11-13: Settings sections, per-model storage, more languages (`feature/settings-sections`)

### Added
- `SpeechLanguage` gains Arabic, Chinese, French, German, Indonesian, Italian, Japanese, Korean, Persian, Portuguese, Russian, Spanish, Turkish, Urdu and Vietnamese (Whisper codes). The pinned `whisper-tiny` is the multilingual openai/whisper-tiny (checked: the repo README and the 50,257-line `tokens.txt` matching our pinned digest). Bangla still routes to Bangla Zipformer.
- `LanguagePicker.kt`: `LanguageField` (Transcribe) and a shared, scrollable `LanguageDialog` (Transcribe and Settings → Default spoken language), with the main languages first and a divider before the rest.
- `AiModel` (ENHANCE, WHISPER, BANGLA). `AudioEngine.modelStorage(): Map<AiModel, Long>` and `deleteModel(model)` replace `modelStorageBytes()`/`deleteModels()`. `ModelFiles.storage`/`delete` map Enhance to `resemble-enhance` plus `xnnpack-cache`.

### Changed
- Settings → Storage shows one row per downloaded model (name, purpose, size, Delete with its own confirmation), then Processed files. The Enhance row includes its XNNPACK weight cache, so it shows about 1.4 GB once the cache is built.
- Settings → About is two rows that open sub-pages: **About Audiops** (version, privacy, source code, the Apache 2.0 license) and **Open-source licenses** (third-party notices and one row per component). Back (system or the top-bar arrow) returns to the main page at the same scroll position.
- Copy: the Transcribe description and language hint, README tables.

### Tests
- `ModelTest`: every language round-trips and has a unique two-letter Whisper code. `ModelFilesTest`: storage counted and deleted per model. `AudiopsViewModelTest`: models listed and deleted one by one.

### Verified
- Android 17 emulator: the Storage rows, the language dialog (main languages, divider, scrolling), the About and Licenses pages, Back keeping the scroll position, and the Transcribe field in dark mode. `make ci` passes.

## 2026-11-14: Omnilingual ASR for most languages, Whisper fallback and Urdu, Bangla kept (`feature/omnilingual-asr`)

### Decision
- Whisper Tiny is replaced by Omnilingual ASR 300M (CTC, INT8) for every language except Bangla, which keeps Bangla Zipformer. Benchmark on 25 FLEURS clips per language, same clips for every model, 4 threads, desktop CPU (`scratchpad/bakeoff`, not committed):
  - word error rate, Omnilingual v1 file (the one shipped, `results-omnilingual-300m-v1.json`): bn 33%, hi 33%, ur 97%, en 19%, ar 21%, fr 22%, es 10%, id 19%; average 32%; peak RSS 1.2 GB; model 365 MB. Speed was measured while other builds used the same CPU, so the real-time factors (0.18 to 0.46) are pessimistic; the v2 run gave 0.17 to 0.18 across all languages.
  - Whisper Tiny (shipped before): bn 145%, hi 147%, ur 84%, en 18%, ar 67%
  - Bangla Zipformer on Bangla: 4% (Omnilingual: 35% with the v2 file)
  - Whisper Small: best on English, Spanish, French, Indonesian, but 1 to 2 times slower than real time (too slow for a phone) and 375 MB
  - Dolphin Small: fast but returns nothing for English, French, Spanish and Arabic
- Omnilingual is Apache 2.0 (`licenses/OMNILINGUAL-ASR-APACHE-2.0.txt`). The model is `csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12` on Hugging Face at revision `6abf1ece…`, verified with SHA-256 (the v2 file is only on GitHub, so it is not used).

### Changed
- `OmnilingualTranscriber` (sherpa-onnx `OfflineOmnilingualAsrCtcModelConfig`, greedy search, 20-second sections, 4 threads) replaces `WhisperTranscriber`. `AudioPipelines.transcribe` sends `bn` to Bangla and everything else to Omnilingual. The first transcription deletes the old `whisper-tiny` folder.
- `AiModel.WHISPER` becomes `AiModel.OMNILINGUAL` (Settings → Storage shows "Omnilingual ASR"). `ModelFiles` storage folder `omnilingual-asr`.
- The language choice offers only Auto and Bangla (`transcriptionLanguages`). The 17 other `SpeechLanguage` entries are kept so that saved history still decodes; they are treated as Auto. Stored default languages outside the offered set fall back to Auto.
- Licences: `licenses/WHISPER-MIT.txt` removed; `licenses/OMNILINGUAL-ASR-APACHE-2.0.txt` added and bundled in the third-party notices; NOTICE, README and AGENTS updated; architecture diagrams say "Omnilingual ASR / Bangla Zipformer".

### Verified
- `:shared:desktopTest`, `:desktopApp:test` and `:app:compileDebugKotlin` pass after the change (see the verification notes in the PR).

### Routing (final)
- Bangla: Bangla Zipformer.
- Urdu: Whisper Tiny (explicit choice): 84% word error against Omnilingual's 97%; Whisper Small was better (51%) but too large and slow for a phone.
- Auto and all other languages: Omnilingual. If it returns no text for the clip, Auto retries with Whisper Tiny and automatic language detection. Found on device: the JFK clip ("ask not what your country…") gave no Omnilingual output; Whisper Tiny transcribes it correctly.
- Languages not measured (about 1,590 of Omnilingual's 1,600) go through Auto; their quality is unknown.
- Picker: Auto, Bangla, Urdu. Whisper Tiny is downloaded only when first needed (Urdu or fallback).
- Licences: `licenses/WHISPER-MIT.txt` restored, Whisper entries back in Settings, notices and NOTICE.

## 2026-11-14: Release v0.1.2 (`docs/record-0.1.2`)

### Released
- Published https://github.com/sayedshaun/audiops/releases/tag/v0.1.2 from `main` (`8a42ec9`, changelog PR #17). CI built and attached the Linux and Windows packages and wrote the notes from `CHANGELOG.md`. The Android APKs and AAB were built locally from the same commit with the release key (`versionCode` 1002), replaced the unsigned CI files, and `SHA256SUMS.txt` was regenerated (10 files).
- Verified the downloaded arm64 APK: checksum OK, release certificate `c147c216…`, versionName 0.1.2, versionCode 1002.
- Not yet tested on the Moto g96 (the phone was disconnected during this release).

## 2026-11-15: AI noise reduction with DPDFNet (`feature/deepfilternet3-local`)

### Decision
- The request was DeepFilterNet3. Its ONNX export (SayedShaun/DeepFilterNet3-onnx) is verified, but running it on Android needs either ONNX Runtime (which conflicts with sherpa-onnx's bundled copy in the same app) or a LiteRT conversion. onnx2tf could convert the encoder but not the ERB decoder (grouped ConvTranspose and skip connections get the wrong layout; the direct backend cannot read the GRU shapes). sherpa-onnx, already in the app, has a speech denoiser for DPDFNet, an improved DeepFilterNet2 (Ceva; code and weights Apache 2.0) with 48 kHz models. We use `dpdfnet2_48khz_hr` (2.58M parameters).
- Chunking measured on DeepFilterNet3 (ONNX, whole file versus chunks with warm-up): a 15 s body with 5 s warm-up stayed within 39.5 dB waveform SNR on a 32 s signal; longer chunks are closer. DPDFNet uses 20 s bodies with 4 s warm-up and 0.2 s lookahead.

### Added
- `AiDenoiser` (jvmShared): a platform hook; `DspPipeline.run(…, aiDenoiser)` uses it for Noise reduction instead of spectral subtraction. `JvmAudioEngine.aiDenoiser()` is null by default (desktop).
- `ChunkedEnhancer`: runs a whole-buffer enhancer over a stream with bounded memory (history, body, lookahead). `ResampledProcessor`: 44.1 kHz <-> model rate around a processor.
- `DpdfNetDenoiser` (app): downloads `dpdfnet2_48khz_hr.onnx` from the sherpa-onnx `speech-enhancement-models` release (SHA-256 `0b399f8a…`), runs `OfflineSpeechDenoiser` at 48 kHz with the attenuation limit from strength (Light 9 dB, Medium 18 dB, Strong 0 = none), and releases it when the stream ends.
- `AiModel.NOISE` ("DPDFNet" in Settings → Storage), licence in `licenses/DPDFNET-APACHE-2.0.txt`, notices, NOTICE, Settings licence list, README.
- `AiDenoiseTest`: exact identity through chunking for several block sizes, chunk windows, resampling round trip and length, and the pipeline hook.

## 2026-11-15: Settings Models page (`feature/settings-models-page`)

### Changed
- Settings → Storage: the per-model rows are replaced by one **Downloaded models** row (summary: count and total size, Calculating…, or No models downloaded) with a chevron that opens a new `SettingsPage.MODELS` sub-page.
- `ModelsPage` lists every downloaded model (icon, title, purpose · size, Delete; Delete disabled while a job runs) and owns the delete confirmation dialog. With no models it shows an empty-state row.
- New strings: `settings_models_count` (plurals, the first plural in the app) and `settings_models_empty_summary`.
- Checked on the emulator in light and dark; Back returns to the main page.

## 2026-11-16: README without the desktop screenshot (`docs/project-website`)

- Removed the desktop screenshot (and its file) from the README; Audiops is Android-only. The rest of the desktop text goes with the desktop removal.

## 2026-11-15: Parakeet v3 and a transcription model setting (`feature/parakeet-asr`)

### Benchmark (FLEURS test, 25 clips per language, PC CPU, 4 threads)
| Language | Parakeet v3 (sherpa-onnx INT8) | Omnilingual 300M | Whisper Tiny |
| --- | --- | --- | --- |
| English | 6.8% WER, RTF 0.097 | 16.8%, 0.168 | 18.5%, 0.113 |
| French | 8.4% | 21.0% | 48.6% |
| Spanish | 2.2% | 8.3% | 20.3% |
| German | 6.4% | 17.0% | – |
| Hindi (not a Parakeet language) | 99.7% | 29.4% | 147% |

Parakeet peak RSS 1.3 GB (Omnilingual 1.2 GB); model 639 MB. Output is punctuated and capitalised.

### Runtime decision
- MediaPipe `tasks-audio` 1.2.3 (2026-11-12) has `AudioTranscriber` with the same Parakeet v3 model (`.litertlm`, INT8, 615 MB) and a self-contained ~16 MB JNI library. Its `Delegate.NPU` needs SoC-specific builds (litert-community publishes f32 builds of ~1.25 GB for SM8450+ Qualcomm, some MediaTek and Tensor G5/G6); none for the Moto g96's SM7435. The user's rule was "MediaPipe if it supports the NPU, else sherpa-onnx", so the app uses sherpa-onnx (already bundled, measured). The MediaPipe Python benchmark was abandoned (PyPI timeouts); an on-device comparison is still possible.

### Changed
- `TranscriptionModel` (AUTO, PARAKEET, OMNILINGUAL, WHISPER, BANGLA), persisted in `UserSettings.transcriptionModel` (`transcription_model`) and stored in `JobSpec.Transcribe.model` (defaults to AUTO, so older history still loads).
- `AudioEngine.transcribe(source, language, model, listener)`. Android: `ParakeetTranscriber` (csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8 @ `ed5d7456`, SHA-256 pinned, `nemo_transducer`, greedy, 20 s sections, 4 threads), `LanguageDetector` (sherpa `SpokenLanguageIdentification` with the Whisper Tiny files, first 30 s via `AudioChunks.firstChunk`, released after use).
- Automatic routing in `AudioPipelines`: bn → Bangla; ur → Whisper; empty → detect; detected bn → Bangla, ur → Whisper, Parakeet languages → Parakeet, else Omnilingual with the Whisper fallback.
- UI: Settings → Transcription → Transcription model (dialog with what each model covers and its size). The Transcribe screen shows the language field only for Automatic and Whisper, and names the model in use. `AiModel.PARAKEET` appears on the Downloaded models page.
- Licence: `licenses/PARAKEET-TDT-CC-BY-4.0.txt`, notices, NOTICE, Settings → Open-source licenses, README.
- Tests: model round trip and old-history decoding, settings persistence and unknown values, ViewModel passes language and model to the engine, `firstChunk` length.
- Emulator: Automatic on the JFK clip → "And so, my fellow Americans, ask not what your country can do for you. Ask what you can do for your country." (Omnilingual returned nothing for this clip); Bangla clip → Bangla output; light and dark checked.

## 2026-11-15: Text to speech (`feature/text-to-speech`)

### Decisions
- Engines: PocketTTS 100M (Kyutai, CC BY 4.0) through MediaPipe `tasks-audio` 1.2.3 (`TextToSpeech`, CPU, one `.litertlm` per language from litert-community/pocket-tts @ `03f949ec`) for en/fr/de/es/it/pt/nl; Piper VITS voices (csukuangfj/vits-piper-*) through the bundled sherpa-onnx for the other 21 languages. Kokoro through sherpa-onnx 1.13.8 was rejected: it now handles only English and Chinese and skips unknown words.
- Piper needs espeak-ng-data (GPL-3.0). The sherpa-onnx native library already contains espeak-ng, so the licence text (`licenses/ESPEAK-NG-GPL-3.0.txt`), notices, NOTICE and Settings entries were added. The data (9 MB zip, taken from a Piper repo revision) is bundled as an asset and unpacked once to `noBackupFilesDir/espeak-ng-data`. Whether the app's licence should change because of GPL code in the APK is for the user to decide.
- Voices were chosen per locale by licence: CC0 or public domain first, then CC BY; CC BY-SA (Catalan), non-commercial and unclear licences (Arabic, Hindi, Indonesian, Italian Piper, Chinese, Georgian, Icelandic, Swahili, Welsh, Spanish AR/MX, Serbian, Luxembourgish) were left out. PocketTTS covers Italian. Bangla, Urdu, Hindi, Arabic and Indonesian still have no voice; the user approved optional non-commercial voices (Meta MMS-TTS, CC BY-NC 4.0), which needs an ONNX export published to Hugging Face.

### Changed
- `Tool.SPEAK`, `JobSpec.Speak(text, voice, speed)`, `AudioEngine.speak` / `supportsSpeak`, `TtsVoices.kt` (35 voices with pinned revisions and SHA-256), `TextChunks` (sentence groups of at most 400 characters), `SpeechSynthesizer` (Android), `SpeakScreen` (text field up to 10,000 characters, language and voice dialogs, speed 0.5 to 2). A speech job has no input recording: `inputId` is empty and `inputName` is the start of the text.
- `AiModel.POCKET_TTS` and `AiModel.PIPER` appear on Downloaded models (`pocket-tts`; `piper-voices` + `espeak-ng-data`).
- APK: MediaPipe adds `libmediapipe_tasks_audio_jni.so` and `libmediapipe_tasks_jni.so` (about 32 MB per ABI before compression).
- Tests: voice catalogue (unique ids, pinned revisions and digests, 28 languages), text splitting, `JobSpec.Speak` round trip, ViewModel speech job.
- Emulator (Android 17, x86_64): PocketTTS (alba) 3.4 s of audio for the test sentence; Piper (en_GB cori) 3.4 s. Parakeet transcribed both back (Piper: "Audio turns text..." for "Audiops").

## 2026-11-15: Subtitles (`feature/subtitles`, on top of `feature/text-to-speech`)

### Changed
- `Transcript(text, cues)`, `Cue`, `Subtitles.srt` / `toVtt`, and `Cues.forSection` (commonMain, pure Kotlin): cues are cut at sentence ends and at 80 characters, wrapped to two lines of about 42 characters, and timed from the recognizer's token times by cumulative non-space character count; without token times (Whisper) they are spread over the section by length. Cues are kept in order and inside the section.
- The four sherpa-onnx transcribers share `transcribeSections` (app/SherpaTranscription.kt), which returns a `Transcript`; `AudioPipelines.transcribe` returns it too.
- `JvmAudioEngine.storeTranscript` writes `<name>.txt` and `<name>.srt`; `AudioEngine.subtitlesFor(path)` finds the SRT, `deleteFile` removes it with the transcript. `Job.subtitlesPath` (optional, so older history still loads). `AudiopsViewModel.saveSubtitles(job, SRT|VTT, destination)` converts SRT to VTT when saving.
- UI: Subtitles (SRT) and Subtitles (VTT) buttons on the transcript card when subtitles exist; `onSaveSubtitles` threaded through `ToolActions`, `ActivityActions` and `JobCardActions`.
- Tests: SRT/VTT format, token-time placement, fallback timing, ordering, wrapping, blank text, ViewModel keeps `subtitlesPath`.

## 2026-11-16: Save audio as M4A or Opus (`feature/audio-export`, on top of `feature/subtitles`)

### Decisions
- No MP3: Android has no MP3 encoder, and the usual library (LAME) is LGPL. M4A (AAC-LC) and Opus use the device's own `MediaCodec` encoders and `MediaMuxer`, so there is no new dependency or licence.
- Opus needs Android 10+ (`MUXER_OUTPUT_OGG`) and an Opus encoder, so the choice is only shown when `AudioEncoder.formats` contains it. Opus takes 8, 12, 16, 24 or 48 kHz; other rates (for example Piper's 22.05 kHz) are converted to 48 kHz.
- A first Opus export looked 0.08 s shorter than the source. I added 120 ms of end padding, measured again, and found the audible end was identical (2.189 s both times): only trailing silence had been dropped. The padding was removed again.

### Changed
- `SaveFormat` (WAV, M4A, OPUS); `AudioEngine.saveFormats` and `saveAudio` (default: WAV copy, otherwise unsupported); `AudiopsViewModel.saveAudio`.
- `PcmSource` (jvmShared): reads Audiops' mono PCM16 WAV files as little-endian PCM16 blocks, optionally converted to another rate with the existing anti-alias filter and resampler.
- `AudioEncoder` (app): streams `PcmSource` into the encoder and the muxer; the muxer writes straight to the destination file descriptor.
- UI: a WAV / M4A / Opus choice on the audio result card (shown only when there is more than one format); the button reads "Save WAV", "Save M4A (AAC)" or "Save Opus".
- Tests: `PcmSourceTest` (header parsing, bytes, up- and downsampling lengths, 8 kHz to 48 kHz, invalid files) and the ViewModel passes the chosen format to the engine.
- Emulator: both formats saved through the system dialog; ffprobe: AAC 22.05 kHz mono 96 kbit/s, 2.415 s; Opus 48 kHz mono Ogg. Test exports were removed from Downloads.

## 2026-11-16: Share target (`feature/share-target`, on top of `feature/audio-export`)

### Changed
- Manifest: `MainActivity` is `singleTask` (so a share reaches the running instance through `onNewIntent`) with intent filters for `SEND` (`audio/*`, `application/ogg`) and `VIEW` (`content:` and `file:` schemes, same types). Video types were not added: they would put Audiops in every video's share sheet.
- `SharedAudio.import` copies the shared stream to `cacheDir/shared/<millis>-<safe name>` on the IO dispatcher and prunes copies older than two days; `safeFileName` (jvmShared) sanitizes the name (letters, digits, `._-`, 60-character base, short extension kept, `audio` if nothing is left).
- `AudiopsViewModel.inputShared` closes any open tool, selects the Tools tab, sets the input and shows "“name” is ready. Choose a tool."; `inputShareFailed` shows an error message. New `UiMessage.InputReady` and `ImportFailed`.
- Tests: `safeFileName` (ordinary, unsafe, non-Latin, long and empty names) and the ViewModel makes shared audio the current recording on the Tools tab.
- Emulator: `am start` with VIEW and with SEND (content URI plus grant) imported the file; Transcribe showed it as the recording with its 0:11.0 length; `pm query-activities` lists Audiops for `audio/ogg`. The cache copies were removed afterwards.

## 2026-11-16: Record in the app (`feature/record`, on top of `feature/share-target`)

### Changed
- `Recorder` / `RecorderState` / `rememberRecorder` (commonMain `ui/Recorder.kt`): the platform boundary, like the file picker. Android (`Recorder.android.kt`): `AudioRecord` (source `MIC`, mono PCM16 at `PIPELINE_RATE`, so no later conversion) streamed into a `WavWriter`; progress (elapsed, level) is published as Compose state; recordings under half a second are discarded; `RECORD_AUDIO` is requested on first use; `ON_STOP` and leaving composition stop the recording and still deliver the file. Desktop: `NoRecorder` (`available = false`).
- The recording card shows Choose file and Record side by side; while recording it shows "Recording…", the time, a level meter and a Stop button, and the screen stays on (`KeepScreenOn`).
- Manifest: `RECORD_AUDIO` and `<uses-feature android:name="android.hardware.microphone" android:required="false"/>`, so devices without a microphone can still install the app.
- `UiMessage.RecordFailed` / `AudiopsViewModel.recordFailed`; `ToolActions.onRecordFailed`.
- Not covered by unit tests (Android audio hardware); verified on the emulator only: permission dialog, timer, Stop, a valid 44.1 kHz mono WAV of 18.1 s that became the current recording. The emulator microphone is silent, so the level meter and speech capture need a phone test.

## 2026-11-16: Presets (`feature/presets`, on top of `feature/record`)

### Changed
- `Preset` (PODCAST, VOICE_MESSAGE, LECTURE, NOISY) holds a fixed `List<AudioOperation>` made only of existing operations (denoise, equalizer, silence removal, loudness normalization), so a preset needs no new DSP, no new job type and no history migration: it is stored as an ordinary `JobSpec.Process`.
- `Tool.PRESETS` (Clean up, listed first): preset buttons and a one-line description of the selected preset; `ToolOptions.preset` (saved across rotation).
- Tests (`PresetTest`): every preset has 2 to 4 operations and survives JSON; each preset run through `DspPipeline` on 8 s of speech-like bursts over noise gives finite, unclipped audio within 2 LU of its loudness target; the Voice message preset shortens the recording.
- Emulator: Voice message on the 11 s English clip gave 9.39 s at −13.8 LUFS (ffmpeg ebur128; target −14).

## 2026-11-16: Prepare release 0.2.0 (`docs/release-0.2.0`)

- Renamed Unreleased to 0.2.0 in `CHANGELOG.md` and fixed its compare links (0.1.2 was missing). The app version comes from the release tag (`-PaudiopsVersion`), so no source file holds it.
- Website: Text to speech, Presets, subtitles and the new transcription coverage are listed; the roadmap drops what shipped.
- Not done on purpose: the tag and release. They wait for the signing secrets, the keystore password rotation, the licence decision (eSpeak NG, GPL-3.0) and a test on a phone.

## 2026-11-16: Website copy fixes (`docs/website-fixes`)

- Tool count (eleven → thirteen), save formats (WAV, M4A, Opus), record and share mentioned, "speak" added to the hero and meta text, and the model download source now says Hugging Face or GitHub (DPDFNet comes from a GitHub release). Checked the layout in headless Chrome at 1280 and 390 px wide.
- Not changed: the screenshots still show the 0.1.2 UI (old banner, "On device" badge).

## 2026-11-16: Website roadmap (`docs/website-roadmap`)

- Roadmap renamed "Ideas we're exploring" (no dates) with four items and an Issues link. Dropped real-time sound classification (the plan is a file tool), music source separation and translations.

## 2026-11-17: AI noise reduction failed on most files (`fix/denoise-partial-frame`)

- Found on a phone: "enhancer returned 508320 samples for 508592". DPDFNet works in 10 ms frames (480 samples at 48 kHz) and drops a partial last frame, so any recording whose length was not a whole number of frames failed. The emulator tests used lengths that happened to divide evenly.
- `runInWholeFrames` pads the chunk with silence to whole frames and cuts the result back to the input length; `DpdfNetDenoiser` uses it. `AiDenoiseTest` covers awkward lengths (including 508,592) and the chunked path.
- The bug was never in a release (DPDFNet is new in 0.2.0), so the changelog is unchanged.

## 2026-11-17: Global recording card (`feature/global-input`)

- The recording input (Choose file, Record, waveform, player) is now one `InputCard` at the top of the Tools tab (`ToolsTab` owns `rememberAudioPicker` and `rememberRecorder`). Tool screens render the same card read-only (`onChoose`/`recorder` null) with a Go to Tools button when nothing is chosen. `ToolActions` lost `onChoose` and `onRecordFailed`.
- Recording now starts and stops on the Tools tab; opening a tool while recording leaves the tab and stops the recording (the recorder is released with the screen).
- Checked on the emulator in light and dark: Tools tab empty and with a file, a tool page with a file, a tool page with none.

## 2026-11-17: Changelog for 0.2.0 (`docs/changelog-0.2.0`)

- Moved the global recording card entry from Unreleased into 0.2.0 (it merged before the release), updated the Record entry to say the button is on the Tools tab, and set the 0.2.0 date to 2026-11-17.
- Tool pages with no recording chosen now show a Choose file button instead of Go to Tools (the one case where a tool page has a picker); `ToolActions.onChoose` is back for it. Choosing there sets the same global recording.

## 2026-11-17: Fast and Studio modes for Enhance speech (`feature/enhance-fast-mode`)

### Decision
- Resemble Enhance measured about 3 minutes for an 11 s clip on the Moto g96 (~16x slower than real time). The user asked for a faster option; the noise-reduction model (DPDFNet) is far quicker, so Enhance now offers both rather than replacing Resemble.

### Added
- `EnhanceMode { FAST, STUDIO }` and `JobSpec.Enhance(mode)` (was a data object). History saved before modes has no `mode` and decodes as Studio.
- `AudiopsViewModel`: Fast runs `engine.process` with `AudioOperation.Denoise(1f)` (the AI denoiser at full strength on Android); Studio calls `engine.enhance` as before.
- Enhance screen: Fast/Studio toggle (Fast default) with a description of each; strings and hint updated.
- Tests: `enhanceModesUseTheirOwnModel` (ViewModel) and `enhanceSavedBeforeModesLoadsAsStudio` (ModelTest).

### Not done
- Studio is not faster yet. Next: profile it on the phone (denoiser, 32-step solver and vocoder are separate costs) and compare quality at fewer steps.
- Fast mode speed and quality have not been measured on a phone.

## 2026-11-17: Waveform progress for running jobs (`feature/waveform-progress`)

- The spinner plus wavy bar read as a download for every job. `WorkStatus` now keeps that only for model downloads (`downloading`); processing draws `WaveformProgress`: 56 rounded bars shaped from the recording's peaks (`JobCard(peaks = ...)`, only when the recording on screen is the job's own; otherwise a stable shape from the job id), processed bars lit, the few ahead pulsing, and a travelling wave when progress is unknown. Progress uses the existing animation spec; the loop is a plain tween, so it follows the system animation scale.
- Checked on the emulator with a speech clip (bars take the clip's shape and fill to 100%) and a 3 minute test tone (flat envelope, so uniform bars): the front glows. Not yet seen on a phone or in dark mode.

## 2026-11-17: Format menu on Save (`feature/save-format-menu`)

- The WAV/M4A/Opus `ConnectedChoice` was always visible on audio results. `AudioResultCard` now has a single `SaveButton`; with more than one format it opens a `DropdownMenu` under the button (format name and a hint each) and saves in the chosen one. Removed the remembered-format state and the unused `action_save_format` string.
- Checked on the emulator: a Presets result shows player and Save; Save opens the three formats. Choosing one opens the system save dialog as before (not exercised end to end here). Not yet seen on a phone or in dark mode.

## 2026-11-17: Supertonic 3 text to speech (`feature/supertonic-tts`)

### Decision
- The user chose Supertonic 3 as the TTS engine after hearing it next to Kokoro. In a CPU test on a PC it ran about 4x real time against 0.6x for Kokoro. Its licence is OpenRAIL-M (commercial use allowed, use-based restrictions that must be passed on); the user accepted that and asked for the files to be hosted on their Hugging Face account.

### Added
- Hugging Face repo `SayedShaun/supertonic-3-sherpa-onnx-int8` (first revision `bfe4786a22304e1e0672d3d7da90f9e63040e529`, now pinned at `25a63a9f1ba817eff0a1960616dc2e6834b6516b`): the int8 files of the k2-fsa `sherpa-onnx-supertonic-3-tts-int8-2026-05-11` package unmodified, plus Supertone's OpenRAIL-M text, the MIT notice for its code and a model card.
- `TtsEngine.SUPERTONIC`, 62 voices (`supertonic-<language>-<f1|m1>`, 31 languages) sharing one set of 7 files (~145 MB); `supertonicSpeaker` maps the style to the model's speaker id; language goes to the model as `GenerationConfig.extra["lang"]`. Supertonic is listed first in each language and `defaultTtsVoice` is its English female voice. Nine new languages (Korean, Japanese, Arabic, Hindi, Indonesian, Bulgarian, Croatian, Estonian, Lithuanian) take the language count to 37.
- `AiModel.SUPERTONIC` (Settings → Downloaded models), licence entry, `licenses/SUPERTONIC-3-OPENRAIL-M.txt`, third-party notices and NOTICE.
- `TtsTest` covers the Supertonic files, speaker mapping and the language/default rules.

### Verified
- Emulator (x86_64 debug): first run downloaded the 7 files, synthesized an English sentence in the default voice, and Parakeet transcribed it back ("Hello this is Audio. This voice was created on your device, without an internet connection."). Voice styles checked by pitch: F1 about 188 Hz, M1 about 143 Hz.
- Not yet run on a phone, and not yet timed on one.

### Model card and download counting (same day)
- The Hugging Face repo got a full model card (quick start in Python and Kotlin, files with SHA-256, voices, the 31 language codes, licence and responsible-use notes). The Python example was run unchanged against the published files (6.0 s of 44.1 kHz audio that transcribes back correctly), and all ten voice styles were checked by pitch: speaker ids 0 to 4 are female (about 160 to 200 Hz) and 5 to 9 male (about 90 to 120 Hz).
- Hugging Face showed "Downloads are not tracked for this model": it counts requests to `config.json` (and a few similar files). Added a stub `config.json` (as Supertone's own repo has), and the app now fetches it with the other files, so first-time downloads in the app show up on the model page. The revision pin moved to `25a63a9f1ba817eff0a1960616dc2e6834b6516b`; the model files themselves are unchanged.

## 2026-11-17: Music separation with SCNet (`feature/music-separation`)

### Decision
- The user asked for SCNet separation. Licence checked first: code MIT, weights confirmed MIT by the author (issue 35), but trained on MUSDB18-HQ (academic use); the user asked to build it on that basis, with the caveat labelled.

### Added
- `Tool.SEPARATE`, `JobSpec.Separate`, `SeparatedStem` (vocals, instrumental, drums, bass, other), `Job.stemPaths` and `Job.files` (every file a job owns; delete, clear and history pruning now remove all of them, which also stops subtitle files leaking), `AudioEngine.supportsSeparate` / `separate`, `AiModel.SCNET`.
- `ScnetSeparator` (pure Kotlin pipeline), `MusicSeparation` (ONNX Runtime), the `:enhance` worker now takes a task (enhance or separate) and several outputs.
- A stems card on the tool screen: each stem plays and saves on its own as WAV, M4A or Opus.
- Licence entry, notices, `licenses/SCNET-MIT.txt`.
- `tools/patch_onnxruntime_jni.py` and four patched `libonnxruntime4j_jni.so` (see AGENTS.md, "ONNX Runtime on Android").

### Verified
- Python reference (numpy STFT/iSTFT with onnxruntime) on an 11 s clip: the four stems add back up to the input at 40.5 dB; a speech clip lands 99% in the vocals stem; the channel layout is source-major (drums, bass, other, vocals; left real, left imaginary, right real, right imaginary per source).
- Kotlin pipeline with the real model on this PC matches that reference at 87 to 135 dB per stem (`ScnetRealModelTest`); 4.5 to 5.7 s per 11 s segment on 4 CPU threads, about 2.2 GB peak.
- Emulator (x86_64, 4 GB RAM): debug and shrunk release builds ran a full separation; the five files match the reference (vocals 82 dB, instrumental and other 60 dB; drums and bass are near silent in a speech clip, so their 16-bit rounding dominates).

### Not done
- Not run on a phone, so real speed and memory there are unknown; stems are mono; songs over 12 minutes are refused; quality on real music was not listened to (no music clip was available to the test).

## 2026-11-18: release 0.3.0 prepared (combined branch)

### Changed
- Merged the Separate music, waveform progress, Save-format menu, home screen, Supertonic and Fast/Studio Enhance work into `release/0.3.0`; one changelog section, README, website, NOTICE and the licence line clarified (source Apache 2.0, binaries GPL-3.0 because of eSpeak NG).
- Tools tab: tiles keep one width; an incomplete last row is padded with a spacer (`balancedRows` and its test removed).
- Resemble Studio keeps 8 midpoint steps (README now says so).

### Verified
- Emulator (x86_64, 4 GB): Studio Enhance, 11 s clip, 8 steps, ~9 min after the 570 MB download; Parakeet transcribes the result word for word. Supertonic English female created speech. Home and Tools screens checked.

## 2026-11-18: UI polish and explicit transcription model (still in 0.3.0)

### Changed
- Action button moved inside the Options card on every tool; the long text under buttons is gone. Long press shows the description in a small window over a blurred screen (`HintHost`, `HintTip`).
- Transcribe: no automatic language detection. The user picks the model (Parakeet default, Omnilingual, Whisper, Bangla); only Whisper takes a language, from about 60. `TranscriptionModel.AUTO` and `SpeechLanguage.AUTO` stay only so old history decodes. `LanguageDetector` removed.
- Home screen kept as in the first 0.3.0 build (a compact-row redesign was tried and reverted).

### Verified
- `make check`, `make ci`; Studio Enhance on a Moto g96 (arm64) finished an 11 s clip in under 3 minutes at 8 steps; hint window checked on the phone.

## 2026-11-18: README and website refresh

### Changed
- Tagline, tool count (fourteen), platform table and roadmap match 0.3.0; new Tools and Enhance screenshots (all four README images are now from 0.3).
## 2026-11-18: desktop removed (`fix/android-only-release`)

### Changed
- Deleted `:desktopApp` and `shared/src/desktopMain`, the `jvm("desktop")` target, `isDesktop`, the desktop-only strings and settings branches, the desktop job in `release.yml`, and the Linux/Windows rows in README and CONTRIBUTING. Architecture diagrams redrawn without the desktop box.
- Unit tests moved from `shared/src/desktopTest` to `shared/src/androidHostTest` and run with `:shared:testAndroidHostTest`; Makefile, pre-commit hook, android.yml and release.yml updated.

### Verified
- `make ci`: spotless, 121 tests (1 skipped, needs an external model), Android lint, R8 release build.

## 2026-11-18: splash icon (`fix/splash-icon`)

### Fixed
- `windowSplashScreenAnimatedIcon` pointed at `launcher_foreground` (white shapes only); it now uses the adaptive `@mipmap/ic_launcher`, so the splash has the gradient background. Checked on the Android 17 emulator.

## 2026-11-18: Transcribe history summary (`fix/transcribe-summary`)

### Fixed
- Activity showed "Transcribe · Auto" because the summary used the language label. `JobSpec.Transcribe.summary()` now shows the model, plus the language for Whisper only. Checked on the emulator.

## 2026-11-18: Piper Ukrainian removed (`fix/piper-ukrainian`)

### Fixed
- `piper-uk-ukrainian-tts` made under a second of audio; removed from `TtsVoices.kt` (Supertonic covers `uk`). Piper now has 20 voices in 20 languages; the 37-language total is unchanged. README and AGENTS counts updated.

## 2026-11-18: release 0.3.1 prepared (`release/0.3.1`)

### Changed
- CHANGELOG: Unreleased became 0.3.1 (colourful splash icon, Transcribe history names the model, Piper Ukrainian removed, Android only). versionCode 3001.

## 2026-11-26: Tools tab title bar (`feature/tools-top-bar`)

### Changed
- Removed the Tools tab's top card (`HeroBanner` with its animated `Waveform`, and `ProcessingBanner`, which replaced it while jobs ran). The tab now uses a `LargeFlexibleTopAppBar` (app name + `home_subtitle`, exit-until-collapsed) like Activity and Settings; while jobs are active, a small `FilledTonalButton` ("Processing N →") in its actions opens Activity. Deleted the unused `HeroBanner`, `Waveform`, the hero preview and the `home_hero_title` string (`home_hero_body` renamed `home_subtitle`). Checked on the emulator in light and dark; the Processing button was not exercised with a live job.

## 2026-11-26: Choose file and Record under the Tools title (`feature/tools-top-bar`)

### Changed
- With no recording and no recording in progress, the Tools tab shows `InputActions` (filled Choose file + tonal Record, `PrimaryButtonHeight`) under the title bar instead of the empty `InputCard`; `AnimatedContent` cross-fades to `InputCard` once a recording exists or recording starts. `InputCard` hides its "No recording selected" header while the first recording is being made, so only the recording panel shows. Checked on the emulator: empty state in light and dark, recording, and the card after stopping.

## 2026-11-26: compact tool tiles and Recently used (`feature/tools-top-bar`)

### Changed
- `ToolTile` is a 76 dp row (44 dp `ShapedIcon` + `titleSmallEmphasized` name, `shapes.large`); the description is left to the tool screen's subtitle. `TileMinWidth` 160 dp. Rows are drawn by `ToolRow`, headings by `SectionLabel` (now `heading()` semantics).
- New `AudiopsState.recentTools(limit)` (distinct tools of the newest jobs, from history, so nothing new is persisted); the Tools tab shows one row of them under "Recently used" (`home_recent`) when there are any. `ModelTest.recentToolsAreNewestFirstWithoutRepeats` covers it.
- Verified the Processing button with a real Studio enhancement on the emulator: it appears in the title bar and opens Activity.
- Considered a category jump bar; with compact tiles the list fits in about two phone screens, so it was not added.

## 2026-11-27: Live meeting (`feature/live-meeting`)

### Added
- Live meeting tool (AI): real-time meeting transcription with speakers, on device. `LiveMeeting` boundary (commonMain), `AndroidLiveMeeting` + `MeetingService` (FGS type microphone) + `MeetingModels` (Silero VAD v5 at deepghs/silero-vad-onnx `193243f7`; CAM++ zh/en at csukuangfj/speaker-embedding-models `a8055d6d`, both SHA-256 pinned, digests checked against the Hugging Face API). `MeetingTranscriber`, `SpeakerClusters` (live centroid assignment + final average-linkage re-clustering) in jvmShared; `MeetingDocument` (transcript and SRT) in commonMain. Transcribers expose `recognizer()` so a meeting decodes utterance by utterance with the Settings model (`AudioPipelines.recognizer`).
- `MeetingScreen`: intro with model and Start; live header (time, speakers, level meter), transcript by turn with speaker colours, italic partial line, follow-latest with a Latest button; Stop and save / Discard (confirmed); Finishing state; the finished meeting shows first. Tools tab shows a red Live button while a meeting runs. Activity keeps meetings as `JobSpec.Meeting` jobs with `Job.audioPath`; the result card has the styled transcript, subtitles and the recording.
- Licences: `licenses/SILERO-VAD-MIT.txt`, `licenses/3D-SPEAKER-APACHE-2.0.txt`, notices, NOTICE, Settings entries.
- Tests: `MeetingTest` (clustering, transcriber, document), ViewModel meeting test, `ModelTest` round trip, `ModelFilesTest` verification record.

### Changed
- Thresholds 0.5 from measured CAM++ similarities on the Parakeet test clips (same voice ≥ 0.58, different voices ≤ 0.45; the first try at 0.4 merged the German and Spanish speakers).
- `ModelFiles.ensure` records verified files (`.<name>.verified`: digest, size, mtime) and re-hashes only when they change; a meeting with Parakeet started after ~2.5 min of hashing on the emulator before.

### Verified
- Emulator (debug file source, 46 s clip of 4 speakers in 4 languages): live partials, turns and speaker count, Tools Live button, notification with Stop and save, finished transcript with all 8 utterances labelled correctly (1 2 1 3 2 4 3 1), saved to Activity. Not yet tried with a real microphone or on a phone; battery use over a long meeting is unmeasured.

## 2026-11-27: release 0.4.0 prepared (`release/0.4.0`)

### Changed
- CHANGELOG: Unreleased became 0.4.0 (Live meeting; Tools tab title bar, start buttons, compact tiles and Recently used; faster model checks). versionCode 4000 (from the tag).
- R8 release build (0.4.0, signed with the debug key) checked on the emulator: live meeting starts, counts with the real microphone and reports no speech when silent; Discard confirms; text to speech (Supertonic); M4A export (AAC 44.1 kHz mono, checked with ffprobe); Parakeet transcription of the English test clip.

## 2026-11-27: transcription named by language (`fix/language-labels`)

### Changed
- `TranscriptionModel.label` now names what each option listens for (`transcription_choice_*`: English and European languages, Many other languages, A language you choose, Bangla); model names (`model_*`) remain only in Settings → Downloaded models and the licence list. Settings rows: "Languages" and "Language you choose" (was "Transcription model", "Whisper language"). Hints no longer name Whisper Tiny or DPDFNet. Transcribe and Live meeting say "Listens for …"; a Whisper job's Activity summary shows only the chosen language.
- Not checked on the emulator: another session was using it. Build and unit tests pass.

## 2026-11-27: release 0.4.1 prepared (`fix/language-labels`)

### Changed
- CHANGELOG: Unreleased became 0.4.1 (transcription named by language). versionCode 4001 (from the tag).

## 2026-11-27: Language and Model sections on language tools (`feature/language-model-sections`)

### Changed
- Transcribe: 1 Language (`LanguageField`), 2 Model (`TranscriptionModelChoice`, connected radio group, Recommended badge), 3 Recording, then Transcribe. Live meeting: Language, Model, then Start (intro card). Text to speech: Language, Voice (with speed), Text, then Create speech.
- `Model.kt`: `punctuatedModelLanguages`, `TranscriptionModel.supports`, `recommendedTranscriptionModel` (Bangla → Bangla, 25 European → Parakeet, Urdu → Whisper, else Omnilingual), `transcriptionModelsFor`. `AudiopsViewModel.setLanguage` saves the language and switches to the recommended model when the chosen one cannot handle it; live meetings use the language too. `JobSpec.Transcribe` always records the language; `JobSpec.Meeting` gained `language` (defaults to AUTO for 0.4.0 history). Activity summaries show the language.
- Removed Settings → Transcription (model and language rows), `TranscriptionModelDialog` and their strings; `ParakeetTranscriber.languages` moved to `punctuatedModelLanguages`.
- Tests: `ModelTest.eachLanguageOffersItsModelsWithTheRecommendedOneFirst`, meeting round trip with language, ViewModel meeting test records the language.
- Checked on the emulator (light): Transcribe (English → three models; Bangla → Bangla model recommended, punctuated model hidden; back to English → punctuated model chosen again), Live meeting sections shared with Transcribe, Text to speech order. Dark mode not seen (the emulator stayed light).

## 2026-11-27: modern language and voice selectors (`feature/language-model-sections`)

### Changed
- New `Selector.kt`: `SelectorCard` (shaped icon, small label over an emphasised value, optional supporting line, tonal unfold chevron; read-only without `onClick`) and `SelectionSheet` (modal bottom sheet with a shaped-icon header, a pill search field for lists over 8, pill rows with the choice in `secondaryContainer` and a check, opens at the current choice; "Nothing matches." when filtered empty).
- `LanguageField` uses them (searchable list of spoken languages); `LanguageDialog` removed. Text to speech: Language and Voice are `SelectorCard`s (voice card shows licence and size), speed in its own card; the private `PickerField`/`ChoiceDialog` removed. `speak_voice_summary` no longer repeats the voice name.
- Checked on the emulator (light): language card, sheet with search ("eng" → English), selection, Text to speech cards.

## 2026-11-27: title bar reaches behind the status bar on the tabs (`feature/language-model-sections`)

### Fixed
- On Tools, Activity and Settings the scrolled (tinted) title bar stopped below the status bar, which kept the page colour. The navigation `Scaffold` in `Shell` applied the status bar inset as content padding; it now uses `contentWindowInsets = WindowInsets(0, 0, 0, 0)`, so each tab's `LargeFlexibleTopAppBar` draws behind the status bar like the tool screens do. Checked on the emulator: status bar strip and collapsed bar are the same colour (RGB 237, 237, 246).

## 2026-11-27: sentences at pauses, speaker labels for files, naming speakers (`feature/speech-segments`)

### Added
- `SpeechTranscription` (jvmShared): a recording is downsampled to 16 kHz (anti-alias + `Resampler`), cut into utterances by a `SpeechDetector` (Silero VAD on Android: `VadDetector`) and transcribed by `MeetingTranscriber`, the same path a live meeting takes. `AndroidAudioEngine.transcribe` uses it for transcripts with speakers and for the models that write no punctuation (`TranscriptionModel.punctuates`: Omnilingual and Bangla); Parakeet and Whisper without speakers keep the 20/30 s sections.
- `SpeechSentences`: each utterance becomes a sentence (capital letter, the language's full stop: । for bn/as/hi/mr/ne/sa/pa, ۔ for Urdu, 。 for zh/ja, ። ။ ។ for am/my/km, none for th/lo, else "."), utterances cut at the 12 s VAD limit run on, a 2 s pause starts a paragraph, Turkish/Azerbaijani capital İ. Applied to live meetings with those models too. No punctuation model was added: sherpa-onnx's only offline one is the 294 MB CT-Transformer for Chinese and English; the Bangla MuRIL restorer is 778 MB with Kaggle-competition weights of unclear licence.
- `JobSpec.Transcribe.speakers` and a "Tell speakers apart" switch on Transcribe. The engine gets a `speakerName` (the ViewModel's, set from the localized "Speaker N").
- Speaker naming: `SpeakerTranscript` (`<transcript>.speakers.json`: lines, each speaker's mean voice embedding, names) is written for meetings and transcripts with speakers; `AudioEngine.speakersFile/speakers/nameSpeakers` rewrite the text and SRT from it. `VoiceBook` (`filesDir/voices.json`) keeps named voices (refined each time a name is given again, shared lock) and names speakers in new transcripts and meetings (one-to-one, closest first, cosine ≥ 0.6). `Job.speakersPath` (in `Job.files`), `Job.revision` (re-reads the transcript after naming), `MeetingResult.speakersPath`, `AudiopsState.rememberedVoices`. UI: Name speakers button and `SpeakerNamesSheet` (first words of each speaker, name field, remember switch), Settings → Remembered voices with Forget.
- Tests: `SpeakersTest` (sentences, cut utterances, paragraphs, the utterance pipeline with a one-second fake detector, speaker voices, `VoiceBook` matching, naming through a `JvmAudioEngine`, speakers file round trip), ViewModel naming test, Transcribe-with-speakers round trip.
- Not checked on the emulator (another session is using it); the VAD path was not run on a device yet.

## 2026-11-27: what a recording sounds like (`feature/sound-tags`)

### Added
- `SoundTagger` (app): k2-fsa's small Zipformer audio tagger (AudioSet, 527 classes, int8, 27 MB, Apache 2.0; `k2-fsa/sherpa-onnx-zipformer-small-audio-tagging-2024-04-15` at `c24f9d0f`, model and label list SHA-256 pinned) through sherpa-onnx `AudioTagging`. CED was considered first but is GPL-3.0.
- `SoundTags` (jvmShared): up to three 10 s windows (start, middle, end) at 16 kHz, averaged scores, and a `SoundProfile`: `SoundKind` (speech, noisy speech, music, other) and up to four main sounds. Thresholds from host runs of the model: speech ≥ 0.9 on speech clips, music/singing ≥ 0.5 on music; added white noise or hum scores 0.15–0.5 across the noise classes (brown noise was not noticed). Speech over music below 0.6 stays speech.
- `AudioEngine.supportsIdentify`/`identify`; the ViewModel identifies each chosen recording after its waveform loads (`AudiopsState.sounds`; failures, such as no network for the first download, just mean no suggestion). Tools tab: `SoundSuggestion` card under the recording with the suggested tool (`SoundKind.suggestedTool`: Transcribe, Enhance speech, Separate music). Analyze: `AudioReport.sounds` ("Main sounds" row). Settings → Downloaded models lists it (`AiModel.SOUNDS`).
- Licence: `licenses/ICEFALL-AUDIO-TAGGING-APACHE-2.0.txt` (icefall licence, with the AudioSet CC BY 4.0 attribution), notices, NOTICE and the licence list.
- Tests: `SoundTagsTest` (classification with real model scores, averaging, windows), ViewModel identification test.
- Not checked on the emulator (another session is using it).

## 2026-11-27: Kokoro English voices (`feature/kokoro-voices`)

### Added
- `TtsEngine.KOKORO`: Kokoro v1.0 int8 (hexgrad, Apache 2.0) from `csukuangfj/kokoro-int8-multi-lang-v1_0` at `57adb37f` (model, voices.bin, tokens and the US and UK lexicons, every file SHA-256 pinned, ~155 MB in `kokoro/<revision>`). Ten voices: en-US Heart, Bella, Nicole, Michael, Fenrir, Puck; en-GB Emma, Isabella, George, Fable; speaker ids from the model's `speaker_names` metadata (`kokoroSpeaker`). `SpeechSynthesizer.kokoro` passes the variety's lexicon and the bundled espeak-ng data; with no `lang` set, sherpa-onnx 1.13.8 looks words up in the lexicon and spells missing ones with espeak-ng (checked in `kokoro-multi-lang-lexicon.cc` at v1.13.8).
- Measured on a laptop CPU (Ryzen 5 4650U, 4 threads, host sherpa-onnx 2.0.3): the int8 model needs about 1.5 s per second of speech, fp32 (325 MB) about 0.6 s. int8 was kept for the download size; ARM phones run int8 kernels well, but this still needs timing on a phone.
- Settings → Downloaded models (`AiModel.KOKORO`), licence list, `licenses/KOKORO-APACHE-2.0.txt`, notices, NOTICE. `TtsTest` checks the Kokoro voices and their speaker ids.

## 2026-11-27: utterance padding and emulator checks (`feature/kokoro-voices`)

### Fixed
- `VadDetector` fed sherpa-onnx's VAD 10 s blocks at once and lost most utterances (one of eight came through on the emulator); it now feeds 0.1 s blocks, as a live meeting does.
- Silero VAD starts an utterance a few hundred ms late. Parakeet coped, but Omnilingual turned the clipped English utterances of the test clip into Arabic-script text (reproduced on the host with the same segments). `UtterancePadding` (jvmShared) widens every utterance by 0.3 s before and 0.2 s after from the surrounding audio, for file transcription and live meetings; `Cues.sequential` keeps subtitles from overlapping. Tests: `SpeakersTest.utterancesAreWidenedWithTheAudioAroundThem`.
- Settings → Downloaded models: "Zipformer sound recognition"; the voice detection model's purpose now names speaker labels and sentences too.

### Checked on the emulator (debug, then the R8 release build signed with the debug key)
- Tools tab: "Sounds like speech" with Transcribe for the 4-voice test clip (also tagged "Speech synthesizer", which it is).
- Transcribe with speakers (Parakeet): all eight utterances, four speakers right; Name speakers rewrote the transcript; a new transcript of the same clip named Ana and Ben by itself; Forget removed Ben (the next transcript said Speaker 2 again).
- Transcribe with the many-language model: correct text in English, German, Spanish and French, one capitalised sentence per utterance.
- Live meeting (test file, many-language model): correct text, sentences, Ana and Ben named.
- Kokoro: Heart (US) and Emma (UK) synthesize; the Heart output transcribes back to the typed text ("Audiops", not in the lexicon, spelled by espeak-ng). 5.4 s of speech took about 34 s on the x86_64 emulator including loading; a phone needs timing.
- Dark theme: Settings → Remembered voices, Downloaded models, the suggestion card. `make ci` passes.
