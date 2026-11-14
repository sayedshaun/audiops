# Progress

Snapshot as of 2026-11-13, after the v0.1.1 release. Updated in place; the history is in `CHANGES.md`.

## Status

Audiops is a Kotlin Multiplatform audio toolkit: eleven tools on Android, nine on Linux/Windows desktop, a job queue with persistent history, and a Material 3 Expressive UI. Built, unit-tested, lint-checked, R8-built, and exercised end to end on an Android emulator.

### Done
- **Licence:** Apache 2.0 (`LICENSE`, `NOTICE`).
- **Moto g96 (8 GB, Android 16):** Enhance speech completes from a fresh install of 0.1.1 (peak 0.8 GB, ~3 min for 11 s); before, the old vocoder reached 4.7 GB and was killed.
- **Released v0.1.1** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.1, from `f1df2e3`): Enhance speech memory fix with the re-exported vocoder, resumable downloads, full third-party notices. Android files signed locally and swapped in, as for v0.1.0.
- **Released v0.1.0** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.0): signed Android APKs and AAB, Linux DEB/RPM, Windows MSI/EXE, SHA256SUMS. Android files were signed locally (the signing secrets are not on GitHub yet) and swapped into the release. Earlier prep: CHANGELOG section cut; release signing key created (kept outside the repo in `~/.audiops-release/`); signed per-ABI APKs (arm64 ≈ 40 MB), universal APK and AAB verified locally; every model pinned and SHA-256 verified; the release workflow has a dry-run mode and writes release notes from CHANGELOG.md.
- **Repository:** public at github.com/sayedshaun/audiops, with README, logo, banner, CONTRIBUTING, SECURITY, issue and PR templates, and a release workflow that builds and attaches Android, Linux and Windows artifacts on every published release.
- **Platforms:** `:shared` (Compose Multiplatform), `:app` (Android), `:desktopApp` (Linux/Windows; DEB/RPM/MSI/EXE packaging).
- **Tools:** Enhance, Transcribe (Android); Noise reduction, Remove silence, Trim, Fade, Volume, Speed and pitch, Equalizer, Convert, Analyze (both platforms).
- **UX:** bottom navigation / rail, Activity tab with queue and history, waveform preview, playback, save via system dialogs, cancel, haptics, auto-scroll to results, settings (theme, dynamic colour, pure black, default language, keep screen on, storage).
- **Transcription models (unreleased):** Auto uses Omnilingual ASR 300M with a Whisper Tiny fallback; Urdu uses Whisper Tiny; Bangla uses Bangla Zipformer (`CHANGES.md`, 2026-11-14). The language choice is Auto, Bangla or Urdu.
- **Background jobs (Android, unreleased):** a foreground service with a progress notification and Cancel keeps jobs running when the app is in the background, the screen is off or the task is swiped away; finished/failed notifications; verified on the Android 17 emulator.
- **Robustness:** constant-memory streaming DSP; staged AI enhancement in an isolated process (a memory kill no longer closes the app); cancellable downloads with byte progress; decode 3× faster; R8 release build verified to run.
- **Tests:** 53 shared JVM tests (DSP, throughput, model serialization, history/settings storage, ViewModel job queue), 3 desktop engine tests, 4 off-screen desktop render tests; all pass. Lint 0 errors.
- **Code quality:** ktlint via Spotless (`make format` / `make check`), a pre-commit hook (`make hooks`), a Makefile, and CI with three checks (Format and style, Unit tests, Android lint and build) on every pull request to `main`; a branch protection rule makes them required to merge. `CHANGELOG.md` holds user-facing release notes.
- **Emulator end to end:** every DSP tool on a 3-minute 48 kHz stereo file; queue; history across restarts; playback; save; cancel during download; staged enhancement memory.

### Not yet done
- **Desktop window not opened interactively** (Wayland session, no capture); the desktop UI is verified by off-screen render tests and the engine by unit tests.
- **Physical devices:** not tested; the emulator is x86_64 while phones are arm64.
- **Desktop AI tools:** need sherpa-onnx's JVM build and a broader decoder (FFmpeg/JavaCV) for MP3/AAC input.

## Open questions

1. **Signing secrets on GitHub:** run `~/.audiops-release/upload-secrets.sh` so future releases sign Android in CI; until then follow "Signing locally" in CONTRIBUTING.md.

2. **Target SDK 37** (lint flags 36): needs a device test pass.
3. **LiteRT 2.3.2** is available (using 2.3.0).
4. **Lighter vocoder export** (fp16/int8 activations) so enhancement fits 4 GB phones.

## Next steps

1. Test background jobs, Transcribe with Omnilingual (including Urdu, which comes out in Devanagari), and a long (3–5 minute) file on the Moto g96 (screen off, swipe away, Android 16).
2. Launch and screenshot the desktop app on Linux and Windows.
3. Faster enhancement (fewer flow steps or a quality setting), after measuring where the time goes.
4. Desktop transcription via sherpa-onnx JVM.
