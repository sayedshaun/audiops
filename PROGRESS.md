# Progress

Snapshot as of 2026-11-27: v0.5.0 is prepared (sentences at pauses, speaker labels and names, sound suggestions, Kokoro voices, language-first tools). Updated in place; the history is in `CHANGES.md`.

## Status

Audiops is a Kotlin Multiplatform audio toolkit: fourteen tools on Android (Android only), a job queue with persistent history, and a Material 3 Expressive UI. Built, unit-tested, lint-checked, R8-built, and exercised end to end on an Android emulator.

### Done
- **0.5.0 (prepared):** transcription cut at pauses with padded VAD utterances (sentences for the many-language and Bangla models; no word split between sections), speaker labels for files, naming speakers and remembered voices (on the device, Settings → Remembered voices), sound recognition with a suggested tool and Analyze's main sounds (Zipformer AudioSet tagger), ten Kokoro English voices, Language and Model sections first, modern selectors. All checked on the emulator, including the R8 build.
- **Live meeting (unreleased, `feature/live-meeting`):** real-time meeting transcription with speakers (Silero VAD + the Settings transcription model + CAM++ speaker embeddings, live labels corrected by a final re-clustering), microphone foreground service, transcript/SRT/recording saved to Activity. Verified on the emulator with a 4-speaker test clip (all utterances labelled correctly).
- **Tools tab redesign (unreleased, `feature/tools-top-bar`):** title bar instead of the hero card, Choose file and Record under the title, compact tiles, Recently used row.
- **Licence:** Apache 2.0 (`LICENSE`, `NOTICE`).
- **Moto g96 (8 GB, Android 16):** Enhance speech completes from a fresh install of 0.1.1 (peak 0.8 GB, ~3 min for 11 s); before, the old vocoder reached 4.7 GB and was killed.
- **Released v0.1.2** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.2, from `8a42ec9`): background jobs, Tools tab layout, Settings sub-pages, per-model storage, Omnilingual ASR for Auto with a Whisper Tiny fallback, Urdu on Whisper Tiny. Android files signed locally, as for earlier releases.
- **Released v0.1.1** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.1, from `f1df2e3`): Enhance speech memory fix with the re-exported vocoder, resumable downloads, full third-party notices. Android files signed locally and swapped in, as for v0.1.0.
- **Released v0.1.0** (https://github.com/sayedshaun/audiops/releases/tag/v0.1.0): signed Android APKs and AAB, Linux DEB/RPM, Windows MSI/EXE, SHA256SUMS. Android files were signed locally (the signing secrets are not on GitHub yet) and swapped into the release. Earlier prep: CHANGELOG section cut; release signing key created (kept outside the repo in `~/.audiops-release/`); signed per-ABI APKs (arm64 ≈ 40 MB), universal APK and AAB verified locally; every model pinned and SHA-256 verified; the release workflow has a dry-run mode and writes release notes from CHANGELOG.md.
- **Repository:** public at github.com/sayedshaun/audiops, with README, logo, banner, CONTRIBUTING, SECURITY, issue and PR templates, and a release workflow that builds and attaches Android, Linux and Windows artifacts on every published release.
- **Platforms:** `:shared` (Compose Multiplatform library, Android target) and `:app` (Android). The desktop app was removed in 0.3.0.
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
- **Tests:** 121 JVM unit tests (DSP, throughput, model serialization, history/settings storage, ViewModel job queue, separation, TTS); all pass. Lint 0 errors.
- **Code quality:** ktlint via Spotless (`make format` / `make check`), a pre-commit hook (`make hooks`), a Makefile, and CI with three checks (Format and style, Unit tests, Android lint and build) on every pull request to `main`; a branch protection rule makes them required to merge. `CHANGELOG.md` holds user-facing release notes.
- **Emulator end to end:** every DSP tool on a 3-minute 48 kHz stereo file; queue; history across restarts; playback; save; cancel during download; staged enhancement memory.

### Not yet done
- **0.5.0 on a phone:** Kokoro speed (5.4 s of speech took ~34 s on the x86 emulator; int8 should be faster on ARM, fp32 would be 325 MB), speaker naming with real voices (threshold 0.6 for remembered voices), sound suggestions on real recordings (brown/low-frequency noise is not noticed).
- **Live meeting on a phone:** not yet tried with a real microphone, real voices across a table, or for an hour (battery, heat, whether Parakeet keeps up on a mid-range CPU). Speaker thresholds were tuned on clean clips; room audio may need a lower value.
- **Physical devices:** not tested; the emulator is x86_64 while phones are arm64.

## Open questions

1. **Music separation (unreleased, `feature/music-separation`):** works on the emulator; not tested on the Moto g96 (speed, memory), stems are mono, and SCNet's MUSDB18-HQ training data makes commercial use unsettled.

2. **Signing secrets on GitHub:** run `~/.audiops-release/upload-secrets.sh` so future releases sign Android in CI; until then follow "Signing locally" in CONTRIBUTING.md.

3. **Target SDK 37** (lint flags 36): needs a device test pass.
4. **LiteRT 2.3.2** is available (using 2.3.0).
5. **Lighter vocoder export** (fp16/int8 activations) so enhancement fits 4 GB phones.
6. **Before tagging v0.2.0:** rotate the release keystore password (it was printed to a local session log); decide whether Apache-2.0 still fits the app given the eSpeak NG (GPL-3.0) code inside sherpa-onnx; test recording, M4A/Opus export, text to speech and Parakeet on a phone.

## Next steps

- **Live meeting:** test a real 30–60 minute meeting on the Moto g96 (screen off), check that transcription keeps up, battery use, and speaker accuracy; consider renaming speakers and a pause button.

- **Enhance Studio speed:** profile Resemble on the phone and try fewer solver steps (it uses 32 evaluations today); Fast mode (DPDFNet) is the quick option.

1. Test background jobs, Transcribe with Omnilingual (including Urdu, which comes out in Devanagari), and a long (3–5 minute) file on the Moto g96 (screen off, swipe away, Android 16).
2. Faster enhancement (fewer flow steps or a quality setting), after measuring where the time goes.
