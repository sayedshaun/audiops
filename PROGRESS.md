# Progress

Snapshot as of 2026-11-18: v0.3.0 is prepared on `release/0.3.0` (all pending features combined), not yet tagged. Updated in place; the history is in `CHANGES.md`.

## Status

Audiops is a Kotlin Multiplatform audio toolkit: eleven tools on Android, nine on Linux/Windows desktop, a job queue with persistent history, and a Material 3 Expressive UI. Built, unit-tested, lint-checked, R8-built, and exercised end to end on an Android emulator.

### Done
- **Licence:** Apache 2.0 (`LICENSE`, `NOTICE`).
- **Moto g96 (8 GB, Android 16):** Enhance speech completes from a fresh install of 0.1.1 (peak 0.8 GB, ~3 min for 11 s); before, the old vocoder reached 4.7 GB and was killed.
- **Released v0.1.2** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.2, from `8a42ec9`): background jobs, Tools tab layout, Settings sub-pages, per-model storage, Omnilingual ASR for Auto with a Whisper Tiny fallback, Urdu on Whisper Tiny. Android files signed locally, as for earlier releases.
- **Released v0.1.1** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.1, from `f1df2e3`): Enhance speech memory fix with the re-exported vocoder, resumable downloads, full third-party notices. Android files signed locally and swapped in, as for v0.1.0.
- **Released v0.1.0** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.0): signed Android APKs and AAB, Linux DEB/RPM, Windows MSI/EXE, SHA256SUMS. Android files were signed locally (the signing secrets are not on GitHub yet) and swapped into the release. Earlier prep: CHANGELOG section cut; release signing key created (kept outside the repo in `~/.audiops-release/`); signed per-ABI APKs (arm64 ≈ 40 MB), universal APK and AAB verified locally; every model pinned and SHA-256 verified; the release workflow has a dry-run mode and writes release notes from CHANGELOG.md.
- **Repository:** public at github.com/sayedshaun/audiops, with README, logo, banner, CONTRIBUTING, SECURITY, issue and PR templates, and a release workflow that builds and attaches Android, Linux and Windows artifacts on every published release.
- **Platforms:** `:shared` (Compose Multiplatform), `:app` (Android), `:desktopApp` (Linux/Windows; DEB/RPM/MSI/EXE packaging).
- **Tools:** Enhance, Transcribe (Android); Noise reduction, Remove silence, Trim, Fade, Volume, Speed and pitch, Equalizer, Convert, Analyze (both platforms).
- **UX:** bottom navigation / rail, Activity tab with queue and history, waveform preview, playback, save via system dialogs, cancel, haptics, auto-scroll to results, settings (theme, dynamic colour, pure black, default language, keep screen on, storage).
- **Transcription models (unreleased, `feature/parakeet-asr`):** Settings → Transcription model: Automatic (default), Parakeet v3, Omnilingual, Whisper Tiny or Bangla Zipformer. Automatic: Bangla → Bangla Zipformer, Urdu → Whisper Tiny; with Auto, Whisper Tiny detects the language from the first 30 s and routes Parakeet's 25 European languages to Parakeet v3 (punctuated), Bangla to Bangla Zipformer, the rest to Omnilingual (Whisper fallback). Verified on the emulator (English → Parakeet, Bangla → Bangla). Not yet timed on the Moto g96.
- **Presets (unreleased, `feature/presets`):** a Presets tool (Clean up) with Podcast, Voice message, Lecture or meeting and Noisy recording; each is a fixed `AudioOperation` chain run as an ordinary processing job. Tests run every preset through the real pipeline and check the loudness target; on the emulator the Voice message preset turned the 11 s test clip into 9.4 s at −13.8 LUFS.
- **Recording (unreleased, `feature/record`):** Record button on every tool's recording card (`Recorder` expect/actual; Android uses `AudioRecord`, mono 44.1 kHz straight into a WAV in `cache/recordings`, stops when the app leaves the foreground). Verified on the emulator: permission prompt, 0:05 timer, Stop gives a valid 18.1 s WAV that becomes the recording. The emulator's microphone is silent, so the level meter and real speech were not exercised; test on the phone.
- **Share target (unreleased, `feature/share-target`):** Share and Open-with for audio files; the shared file is copied to the cache (`cache/shared`, pruned after two days) and becomes the current recording. Verified on the emulator with both intents.
- **Audio export (unreleased, `feature/audio-export`):** result cards offer WAV / M4A (AAC) / Opus. Verified on the emulator through the system save dialog: M4A is AAC-LC 22.05 kHz mono (96 kbit/s), Opus is 48 kHz mono in Ogg; both decode with ffmpeg and the speech is intact.
- **Subtitles (unreleased, `feature/subtitles`):** transcripts keep an SRT next to the text; Save SRT / Save VTT buttons on the transcript card. Verified on the emulator (JFK clip: two cues, 0.24 s to 8.08 s and 8.08 s to 10.96 s).
- **Supertonic 3 (unreleased, `feature/supertonic-tts`):** default text-to-speech engine, 31 languages x female and male style, int8 sherpa-onnx files from `SayedShaun/supertonic-3-sherpa-onnx-int8` (revision `25a63a9f`, OpenRAIL-M; the card has usage examples). Checked on the emulator (download, synthesis, transcribes back); not yet timed on a phone. Known: the Piper Ukrainian voice produces under a second of audio (sample test), to be investigated.
- **Text to speech (shipped in 0.2.0, `feature/text-to-speech`):** new AI tool, 35 voices in 28 languages (PocketTTS through MediaPipe for 7 languages, Piper through sherpa-onnx for 21), speed control, sentence-by-sentence synthesis with progress and Cancel, WAV output. Verified on the emulator: PocketTTS (Alba) and Piper (Cori, English UK) both transcribe back to the typed sentence with Parakeet. No Bangla, Hindi, Urdu, Arabic or Indonesian voice yet (no permissively licensed one in Piper; Meta MMS-TTS, CC BY-NC, needs an ONNX export).
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

0. **Music separation (unreleased, `feature/music-separation`):** works on the emulator; not tested on the Moto g96 (speed, memory), stems are mono, and SCNet's MUSDB18-HQ training data makes commercial use unsettled.

1. **Signing secrets on GitHub:** run `~/.audiops-release/upload-secrets.sh` so future releases sign Android in CI; until then follow "Signing locally" in CONTRIBUTING.md.

2. **Target SDK 37** (lint flags 36): needs a device test pass.
3. **LiteRT 2.3.2** is available (using 2.3.0).
4. **Lighter vocoder export** (fp16/int8 activations) so enhancement fits 4 GB phones.
5. **Before tagging v0.2.0:** rotate the release keystore password (it was printed to a local session log); decide whether Apache-2.0 still fits the app given the eSpeak NG (GPL-3.0) code inside sherpa-onnx; test recording, M4A/Opus export, text to speech and Parakeet on a phone.

## Next steps

- **Enhance Studio speed:** profile Resemble on the phone and try fewer solver steps (it uses 32 evaluations today); Fast mode (DPDFNet) is the quick option.

1. Test background jobs, Transcribe with Omnilingual (including Urdu, which comes out in Devanagari), and a long (3–5 minute) file on the Moto g96 (screen off, swipe away, Android 16).
2. Launch and screenshot the desktop app on Linux and Windows.
3. Faster enhancement (fewer flow steps or a quality setting), after measuring where the time goes.
4. Desktop transcription via sherpa-onnx JVM.
