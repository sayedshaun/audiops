# Progress

Snapshot as of 2026-11-13, branch `feature/audio-toolkit`. Updated in place; the history is in `CHANGES.md`.

## Status

Audiops is a Kotlin Multiplatform audio toolkit: eleven tools on Android, nine on Linux/Windows desktop, a job queue with persistent history, and a Material 3 Expressive UI. Built, unit-tested, lint-checked, R8-built, and exercised end to end on an Android emulator.

### Done
- **Platforms:** `:shared` (Compose Multiplatform), `:app` (Android), `:desktopApp` (Linux/Windows; DEB/RPM/MSI/EXE packaging).
- **Tools:** Enhance, Transcribe (Android); Noise reduction, Remove silence, Trim, Fade, Volume, Speed and pitch, Equalizer, Convert, Analyze (both platforms).
- **UX:** bottom navigation / rail, Activity tab with queue and history, waveform preview, playback, save via system dialogs, cancel, haptics, auto-scroll to results, settings (theme, dynamic colour, pure black, default language, keep screen on, storage).
- **Robustness:** constant-memory streaming DSP; staged AI enhancement (peak 461 MB, was 2.3 GB and killed); cancellable downloads with byte progress; decode 3× faster.
- **Tests:** 27 shared JVM tests (13 DSP), a throughput test, 3 desktop engine tests; all pass. Lint 0 errors.
- **Emulator end to end:** every DSP tool on a 3-minute 48 kHz stereo file; queue; history across restarts; playback; save; cancel during download; staged enhancement memory.

### Not yet done
- **Desktop GUI not launched** in this session (no headless display); the desktop engine is covered by tests and the app compiles.
- **Physical devices:** not tested; the emulator is x86_64 while phones are arm64.
- **Desktop AI tools:** need sherpa-onnx's JVM build and a broader decoder (FFmpeg/JavaCV) for MP3/AAC input.
- **Background processing:** jobs stop if the process is killed; a foreground service would keep them alive.

## Open questions

1. **Licence** for Audiops' own code (MIT, Apache-2.0, GPL-3.0…). No `LICENSE` file yet.
2. **Target SDK 37** (lint flags 36): needs a device test pass.
3. **APK size** (~148 MB, all ABIs): publish an App Bundle or add ABI splits.
4. **LiteRT 2.3.2** is available (using 2.3.0).
5. **Resemble model digests** for SHA-256 verification.
6. **GitHub repository name:** the remote is still `SayedShaun/audioops`; rename it to `audiops` on GitHub, then update the remote URL.

## Next steps

1. Pick a licence and add `LICENSE`.
2. Test on a physical arm64 phone (enhancement speed, memory, long files).
3. Launch and screenshot the desktop app on Linux and Windows.
4. Foreground service for long jobs; notifications on completion.
5. Desktop transcription via sherpa-onnx JVM.
