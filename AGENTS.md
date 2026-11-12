# Audiops: guide for coding agents

This file is the single source of truth for any agent (Claude, Codex, Copilot, Gemini…) working in this repository. Read it fully before changing code. `CLAUDE.md` imports it.

## Repository rules

- **Branch first.** Before starting each new type of work, create and switch to a new, descriptively named Git branch (`feature/…`, `fix/…`, `docs/…`). Preserve any uncommitted changes when you branch.
- **Git metadata lives in `.audiops-git`, not `.git`.** Run git as `GIT_DIR=$PWD/.audiops-git GIT_WORK_TREE=$PWD git …` from the repo root.
- **Commits list the user as the sole author.** No `Co-Authored-By` lines and no AI attribution in commit messages or pull requests.
- Commit or push only when the user asks.
- Keep `CHANGES.md` (append one entry per meaningful change; never edit old entries) and `PROGRESS.md` (a current snapshot of done / next / open questions, updated in place).

## Product

Audiops is an open-source app for on-device audio processing. Audio never leaves the device; the network is used only to download model files once.

| Tool | Status | Engine |
| --- | --- | --- |
| Enhance audio | Experimental | Resemble Enhance, LiteRT INT8 (~360 MB) |
| Transcribe speech | Working | Whisper Tiny INT8 via sherpa-onnx (~103 MB); Bangla Zipformer (~74 MB) |
| Remove noise | Planned | Disabled tile on the home screen |

The name is **Audiops** (not "AudioOps"). The package is `com.audiops.app`.

### Platforms

- **Now:** Android 8.0+ (API 26). Compile SDK 37, target SDK 36.
- **Planned:** Linux and Windows desktop from the same codebase, using Kotlin Multiplatform and Compose Multiplatform.
- Keep code portable. Platform APIs (MediaCodec, Context, LiteRT, Android views) stay behind small interfaces in platform-specific code. Shared logic (state, pipelines, DSP, WAV, chunking, settings, UI) must not import `android.*`.

## Build and test

Requirements: JDK 17 with `javac`, Android SDK Platform 37.

```sh
./gradlew :app:testDebugUnitTest      # JVM unit tests (must pass)
./gradlew :app:lintDebug              # lint (0 errors required)
./gradlew :app:assembleRelease        # R8 + resource shrinking
./gradlew :app:installDebug           # run on a device or emulator
./gradlew :app:connectedDebugAndroidTest   # instrumented decoder tests
```

Run the first three before you call work done. CI (`.github/workflows/android.yml`) runs the same checks.

## Architecture

Unidirectional data flow: **ViewModel → immutable state → stateless screens → events → ViewModel.**

```
app/src/main/java/com/audiops/app/
  MainActivity.kt         Splash, edge-to-edge, system bars follow the in-app theme
  AudiopsViewModel.kt     AudiopsState, work control, saving, settings, model storage
  UserSettings.kt         DataStore-backed settings (theme, dynamic colour, pure black, …)
  AudioPipelines.kt       Enhance and transcribe pipelines (blocking, worker thread)
  AudioDecoder.kt         Decode any device format to 44.1 kHz mono PCM16; chunking
  ResembleEnhancer.kt     LiteRT inference, DSP, Hub model download
  WhisperTranscriber.kt   Whisper Tiny via sherpa-onnx
  BanglaTranscriber.kt    Bangla Zipformer via sherpa-onnx
  ModelFiles.kt           Verified downloads, model storage, ProcessingListener
  WavAudio.kt             Streaming WAV writer
  ui/
    Theme.kt              MaterialExpressiveTheme, colour schemes, shape scale
    Components.kt         Shared composables (ShapedIcon, WorkStatus, ResultCard, ToolScaffold…)
    AudiopsScreen.kt      Navigation root (AnimatedContent) and HomeScreen
    ToolScreens.kt        EnhanceScreen, TranscribeScreen (+ *Actions)
    SettingsScreen.kt     Settings (+ SettingsActions)
    Previews.kt           @Preview for components, light and dark
```

### Rules

- **State:** all screen state lives in `AudiopsViewModel`. Selections that must survive process death go in `SavedStateHandle`; preferences go in `SettingsRepository` (DataStore).
- **Screens are stateless.** They take state plus an `…Actions` data class of callbacks, never the ViewModel. Only transient UI state (an open dialog) may use `rememberSaveable`.
- **Collect flows with `collectAsStateWithLifecycle()`.**
- **Long work** runs through `AudioPipelines` inside `runInterruptible(Dispatchers.IO)`. Every loop must call `throwIfCancelled()` so Cancel works.
- **Progress** goes through `ProcessingListener`. Pipelines never touch UI state.
- **Errors:** pipelines throw with a clear message; the ViewModel maps them to `AudiopsState.error`. One-shot messages go to the snackbar via `messages`.
- **Models** are verified with SHA-256 (`ModelFiles.ensure`). Never change a revision or digest without re-verifying the download. Resemble Enhance has no digests yet.
- **Files:** temporary audio goes in `cacheDir` and must be deleted by its owner; models go in `noBackupFilesDir`.

## Design system: Material 3 Expressive

The app follows Material 3 Expressive (Android 16+ design language). Material 3 is pinned to `1.5.0-rc01` because Expressive is not in a stable release yet.

- **Theme:** `AudiopsTheme` wraps `MaterialExpressiveTheme` with `MotionScheme.expressive()`. Dynamic colour is used on Android 12+ unless the user turns it off. Light, dark and pure-black schemes are defined.
- **Colour:** use `MaterialTheme.colorScheme` roles only, never raw colours in screens. Cards use `surfaceContainer*`, accents use `primary` / `*Container`.
- **Shape:** use the shape scale (`MaterialTheme.shapes.extraLarge`, `extraLargeIncreased`, …). Icon containers use `MaterialShapes` (Cookie9Sided, Sunny, SoftBurst, Cookie4Sided) via `ShapedIcon`.
- **Lists:** use connected groups (`groupShape(index, count)`): 2 dp gaps, 28 dp outer and 6 dp inner corners.
- **Buttons:** primary actions use `shapes = ButtonDefaults.shapes()` (shape morphing) with `PrimaryButtonHeight`. Use connected `ToggleButton` groups (`ButtonGroupDefaults.connected…ButtonShapes()`) instead of chips or segmented buttons.
- **Progress:** `LoadingIndicator` plus `LinearWavyProgressIndicator`.
- **Type:** prefer the `*Emphasized` styles for headings.
- **Motion:** take specs from `MaterialTheme.motionScheme`; never hard-code durations.
- **Top bars:** `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior`.
- **Icons:** `Icons.Rounded.*` (`Icons.AutoMirrored.Rounded.*` for directional icons).

## UX and accessibility checklist

- Every user-visible string is in `res/values/strings.xml`.
- Touch targets are at least 48 dp; primary actions are 56 dp.
- Icons that carry meaning have a `contentDescription`; decorative ones use `null` or `clearAndSetSemantics {}`.
- Status changes use `liveRegion`; headings use `semantics { heading() }`; whole-row toggles use `toggleable(role = Role.Switch)`.
- Haptics: `Confirm` on success, `Reject` on failure, `ToggleOn`/`ToggleOff` on switches, `SegmentTick` on selections.
- Content width is capped (640 dp for tools, 720 dp for settings) for tablets and landscape.
- Check light, dark and pure black, and rotate the device, before finishing UI work.

## Licences

Third-party licences are in `licenses/` and listed in Settings → Open-source licenses. Licensing for Audiops' own code has not been chosen yet; do not add a `LICENSE` file without the user's decision.
