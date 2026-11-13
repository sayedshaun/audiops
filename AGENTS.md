# Audiops: guide for coding agents

This file is the single source of truth for any agent (Claude, Codex, Copilot, Gemini…) working in this repository. Read it fully before changing code. `CLAUDE.md` imports it.

## Repository rules

- **Branch first.** Before starting each new type of work, create and switch to a new, descriptively named Git branch (`feature/…`, `fix/…`, `docs/…`). Preserve any uncommitted changes when you branch.
- **Git metadata lives in `.audiops-git`, not `.git`.** Run git as `GIT_DIR=$PWD/.audiops-git GIT_WORK_TREE=$PWD git …` from the repo root.
- **Commits list the user as the sole author.** No `Co-Authored-By` lines and no AI attribution in commit messages or pull requests.
- Commit or push only when the user asks.
- Keep `CHANGES.md` (append one entry per meaningful change; never edit old entries) and `PROGRESS.md` (a current snapshot of done / next / open questions, updated in place).
- Keep `CHANGELOG.md` for users: add a line under **Unreleased** for every user-visible change (Keep a Changelog groups). `CHANGES.md` is the detailed development log.
- **Before every commit, run `make check`** (formatting with ktlint via Spotless, then all unit tests) and fix what it reports. Run `make format` to fix formatting, then review the diff. Never commit with `--no-verify` and never disable a ktlint rule or delete a failing test to get a commit through; fix the code, or ask the user. The pre-commit hook (`make hooks`) runs the same checks; CI repeats them and blocks the merge to `main`.

## Product

Audiops is an open-source, on-device audio toolkit. Audio never leaves the device; the network is used only to download AI models once. The name is **Audiops** (not "AudioOps"); the package is `com.audiops.app`.

| Category | Tools | Engine |
| --- | --- | --- |
| AI | Enhance speech, Transcribe | Resemble Enhance (LiteRT INT8, ~360 MB); Whisper Tiny and Bangla Zipformer (sherpa-onnx) |
| Clean up | Noise reduction, Remove silence | Spectral subtraction; frame-based silence gate |
| Edit | Trim, Fade, Volume (peak, loudness BS.1770, gain) | Streaming DSP |
| Effects | Speed and pitch, Equalizer | WSOLA time-stretch + cubic resampler; RBJ biquads |
| Utilities | Convert (sample rate), Analyze | Anti-aliased resampler; loudness/peak/clipping meter |

All DSP is pure Kotlin in `jvmShared` and streams in blocks (constant memory), so it runs on Android and desktop.

### Platforms

- **Android 8.0+** (API 26): every tool. Compile SDK 37, target SDK 36.
- **Desktop (Linux, Windows)**: every DSP tool, analysis, playback, history and settings. Input is WAV/AIFF/AU (Java Sound). AI tools report "not available on this platform yet".
- Kotlin Multiplatform + Compose Multiplatform: one codebase. Platform APIs stay behind `AudioEngine` and the `expect` declarations in `ui/Platform.kt` / `ui/Player.kt`.

## Modules

```
shared/        Kotlin Multiplatform library (Android + desktop JVM)
  commonMain/  UI (Compose Multiplatform), AudiopsViewModel, model, settings, history, strings
  jvmShared/   JVM code shared by Android and desktop: DSP, WAV, PCM, model downloads, JvmAudioEngine
  androidMain/ Android actuals: pickers, MediaPlayer, dynamic colour, back handling, previews
  desktopMain/ Desktop actuals: AWT file dialogs, Java Sound player
  desktopTest/ JVM unit tests (DSP correctness, performance, chunking, WAV, math)
app/           Android application: MainActivity, AudiopsApplication, AndroidAudioEngine,
               MediaCodec decoder, LiteRT enhancer, sherpa-onnx transcribers
desktopApp/    Desktop application: Main.kt, DesktopAudioEngine (+ tests); packages DEB/RPM/MSI/EXE
```

The Android engine lives in `app/` because a library module cannot depend on the local sherpa-onnx `.aar`.

## Build and test

Requirements: JDK 17 with `javac`, Android SDK Platform 37.

Everyday commands are in the `Makefile` (`make help`):

```sh
make format        # format all Kotlin and Gradle files (ktlint via Spotless)
make check         # format check + unit tests: run before every commit (the pre-commit hook does this)
make ci            # everything CI runs before a merge to main (adds Android lint and the R8 build)
make hooks         # install the pre-commit hook once per clone (git config core.hooksPath .githooks)
```

The underlying Gradle tasks:

```sh
./gradlew spotlessCheck              # formatting and style (ktlint 1.8, rules in .editorconfig)
./gradlew spotlessApply              # fix formatting
./gradlew :shared:desktopTest        # DSP, model, repository, ViewModel and performance tests (must pass)
./gradlew :desktopApp:test           # desktop engine tests (must pass)
./gradlew :app:lintDebug             # Android lint (0 errors required)
./gradlew :app:assembleRelease       # R8 + resource shrinking
./gradlew :app:installDebug          # run on a device or emulator
./gradlew :desktopApp:run            # run the desktop app
./gradlew :desktopApp:packageDistributionForCurrentOS   # DEB/RPM on Linux, MSI/EXE on Windows
```

Run `make ci` (or `spotlessCheck` plus the first four) before you call work done. For releases, also install the R8 build on a device and launch it: R8 issues (for example Room/WorkManager reflection, see `app/proguard-rules.pro`) only show at runtime. CI (`.github/workflows/android.yml`) runs the same checks on every pull request to `main` and every push to `main`, as three jobs: **Format and style**, **Unit tests**, and **Android lint and build**.

### Code style and structure

- ktlint (`intellij_idea` style, 140-column lines) enforces formatting, import order, naming and file structure. Composable functions and Compose-style top-level values may be PascalCase; everything else follows Kotlin conventions.
- One public class per file, named after the class (ktlint `filename`). No wildcard imports: import Compose resource accessors one by one (`import com.audiops.shared.resources.app_name`).
- Keep the layering in **Architecture** below: screens are stateless, platform APIs stay behind `AudioEngine` and the `expect` declarations, and DSP stays in `jvmShared`.

### Tests

Unit tests live in `shared/src/desktopTest` (run on the JVM, cover common and `jvmShared` code) and `desktopApp/src/test`.

| Test | Covers |
| --- | --- |
| `DspTest`, `PerformanceTest` | Every DSP processor against reference values; throughput guard |
| `ModelTest` | JSON round trip of jobs and every operation (history file format), progress maths, state helpers |
| `RepositoryTest` | History and settings DataStore persistence, limits, corrupt and unknown values |
| `AudiopsViewModelTest` | Job queue with a fake engine: success, failure, no speech, unsupported tools, ordering, cancel, delete, navigation |
| `AudioChunkingTest`, `ResembleMathTest`, `WavWriterTest` | AI pre/post-processing and WAV output |
| `DesktopAudioEngineTest`, `DesktopRenderTest` | Desktop decoding; off-screen renders of the main screens |

New behaviour needs a test: a new `AudioOperation` gets a `DspTest` case and a `ModelTest` round trip; ViewModel changes get an `AudiopsViewModelTest` case.

## Architecture

Unidirectional data flow: **ViewModel → immutable `AudiopsState` → stateless screens → events (`…Actions`) → ViewModel.**

- **Navigation:** `Tab` (Tools, Activity, Settings) at the top level; an open `Tool` screen shows above the tabs. Back closes the tool, then returns to Tools. Phones use `ShortNavigationBar`, wide windows a `NavigationRail`.
- **Jobs:** `AudiopsViewModel.start(tool, spec)` queues a `Job`. One worker coroutine runs jobs in order through `runOnWorker` (= `runInterruptible(Dispatchers.IO)`). Cancelling interrupts the worker; every loop calls `throwIfCancelled()`, and interrupt-caused I/O errors are reported as cancellation. Finished jobs persist in `HistoryRepository` (DataStore JSON, max 100); their outputs live in the engine's output directory.
- **AI enhancement process:** `AndroidAudioEngine.enhance` runs `AudioPipelines.enhance` inside `EnhanceService` (process `:enhance`) through `EnhanceClient`. The vocoder can need ~3 GB; if the system kills the worker, the app survives and reports it. Cancel kills the worker. Keep heavy native inference out of the main process.
- **Engine:** `AudioEngine` (commonMain) is the only platform boundary for audio. `JvmAudioEngine` (jvmShared) implements DSP tools, analysis, waveform, storage and a reference-counted decode cache; `AndroidAudioEngine` and `DesktopAudioEngine` add decoding and AI tools.
- **DSP:** `AudioProcessor` blocks in `dsp/`; `DspPipeline` chains them over decoded 44.1 kHz mono PCM16, splitting into stages where an operation must measure its input first (normalize, denoise). New operations: add an `AudioOperation` subtype (serializable), map it in `DspPipeline.processorFor`, add UI in `ToolScreen.kt`, and add tests in `DspTest` and `ModelTest`.
- **Errors:** pipelines throw with a clear message; the ViewModel records a `FailureKind` and detail; the UI localizes it. One-shot messages go to the snackbar as `UiMessage`.
- **Strings:** all user-visible text is in `shared/src/commonMain/composeResources/values/strings.xml` (Compose resources: write `%` once, not `%%`). Only the launcher label lives in `app/src/main/res`.
- **Models:** Whisper and Bangla files are verified by SHA-256 (`ModelFiles.ensure`); never change a revision or digest without re-verifying. Resemble Enhance has no digests yet.

## Design system: Material 3 Expressive

Compose Multiplatform 1.12.2 with CMP Material 3 `1.12.0-alpha03` (Jetpack `1.5.0-alpha22`), the release line that contains the Expressive APIs. Do not pin a different Jetpack Material 3 in `app/`: shared code is compiled against this exact API and the Expressive APIs still change between pre-releases (a mismatch crashes at runtime).

- **Theme:** `AudiopsTheme` = `MaterialExpressiveTheme` + `MotionScheme.expressive()` + the expressive shape scale. Dynamic colour on Android 12+ (switchable); brand palette elsewhere; light, dark and pure black.
- **Colour:** only `MaterialTheme.colorScheme` roles. Tool categories map to tonal pairs in `ToolCategory.colors()`.
- **Shape:** every tool has a `MaterialShapes` shape (`Tool.shape`) shown through `ShapedIcon`. Lists use connected groups (`groupShape`).
- **Components:** `LargeFlexibleTopAppBar`; shape-morphing buttons (`ButtonDefaults.shapes()`); connected `ToggleButton` groups (`ConnectedChoice`) instead of chips; `LoadingIndicator` + `LinearWavyProgressIndicator`; continuous sliders that snap in code (discrete sliders draw dense tick rows).
- **Motion:** specs from `MaterialTheme.motionScheme`; never hard-code durations.
- **Icons:** `Icons.Rounded.*` (`Icons.AutoMirrored.Rounded.*` for directional icons).

## UX and accessibility checklist

- Touch targets at least 48 dp; primary actions 56 dp (`PrimaryButtonHeight`).
- Meaningful icons have a `contentDescription`; decorative ones use `null` or `clearAndSetSemantics {}`.
- Live status uses `liveRegion`; headings use `semantics { heading() }`; whole-row toggles use `toggleable(role = Role.Switch)`.
- Haptics: `Confirm` on success, `Reject` on failure, `ToggleOn`/`ToggleOff`, `SegmentTick`.
- New results must become visible without scrolling (the job card is brought into view).
- Content width is capped (640 dp tools, 720 dp settings and activity).
- Check light, dark and pure black, rotation, and a long (3+ minute) file before finishing UI work.

## Licences

Third-party licences are in `licenses/` and listed in Settings → Open-source licenses. Licensing for Audiops' own code has not been chosen yet; do not add a `LICENSE` file without the user's decision.
