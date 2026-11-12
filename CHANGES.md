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
