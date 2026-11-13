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
