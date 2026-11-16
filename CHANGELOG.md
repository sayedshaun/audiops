# Changelog

All notable changes to Audiops are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This file is for people who use Audiops: what changed in each release. The detailed
development log is in [CHANGES.md](CHANGES.md).

When you make a user-visible change, add a line under **Unreleased** in the matching group
(`Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`). When a release is cut,
rename **Unreleased** to the version and date and start a new empty **Unreleased** section.

## [Unreleased]

### Added
- **Save audio as M4A or Opus.** Processed and generated audio can now be saved as a smaller M4A (AAC) or Opus file as well as WAV. M4A works on every phone; Opus needs Android 10 or newer. There is no MP3 option because Android has no MP3 encoder.
- **Subtitles from transcripts.** After a transcription you can save the result as an SRT or VTT subtitle file, with times for each sentence, ready to use in a video editor or player. Times come from the speech model; Whisper Tiny does not report them, so its subtitles are spread evenly over each 20-second section.
- **Text to speech.** A new AI tool turns text into speech in 28 languages, on your phone: English (US and UK), French, German, Spanish, Italian, Portuguese (Brazil and Portugal), Dutch, Czech, Danish, Greek, Persian, Finnish, Hungarian, Kazakh, Latvian, Nepali, Norwegian, Polish, Romanian, Russian, Slovak, Slovenian, Swedish, Turkish, Ukrainian and Vietnamese. Pick a language and voice, set the speed, and save a WAV file. Each voice downloads once (63 to 163 MB). It uses Kyutai's PocketTTS (through Google MediaPipe) and Piper voices, and adds about 30 MB to the app.
- **Much better transcription for English and European languages.** Automatic now detects the language and sends English and 24 other European languages to NVIDIA's Parakeet v3, which adds punctuation and capital letters. In our test it made 2.5 to 4 times fewer errors than before on English, French, Spanish and German, and was faster. It downloads about 640 MB the first time it is used. Bangla, Urdu and other languages keep their current models.
- **Choose the transcription model** in Settings → Transcription: Automatic (recommended), Parakeet v3, Omnilingual ASR, Whisper Tiny or Bangla Zipformer.

### Changed
- **AI noise reduction on Android.** Noise reduction now uses DPDFNet (Ceva, Apache 2.0), an AI speech enhancer that runs on the phone, instead of spectral subtraction. It downloads once (about 11 MB). Light, Medium and Strong set how much of the removed sound is kept (9 dB, 18 dB, no limit). Desktop still uses spectral subtraction.
- **Settings → Storage has one Downloaded models row.** It shows how many models are downloaded and their total size; tap it to see each model with its size and delete it.

## [0.1.2] - 2026-11-14

### Added
- **Jobs keep running in the background on Android.** Enhance speech and other jobs continue when you switch apps, turn the screen off or swipe Audiops away from Recents. A notification shows the running job and its progress, with a Cancel button, and another tells you when a job finishes or fails while Audiops is closed. Tap it to open the Activity tab. Android 13+ asks for notification permission when you start your first job.

### Changed
- **Better transcription for most languages.** Auto now uses Omnilingual ASR 300M (Meta, Apache 2.0), which detects the language itself and covers over 1,600 languages. In our test on 25 clips per language it was about four times more accurate than Whisper Tiny on Bangla and Hindi, and about as fast. If it finds no speech, Auto retries with Whisper Tiny. **Bangla** keeps its dedicated model, and **Urdu** uses Whisper Tiny, which is less weak on Urdu. The language choice is now Auto, Bangla or Urdu. Omnilingual downloads about 366 MB on first use. Its output has no punctuation or capital letters.
- **Settings is shorter.** About now has two rows: **About Audiops** (version, privacy, source code, the app's license) and **Open-source licenses** (every third-party license). Each opens its own page.
- **Storage lists each downloaded model** (Resemble Enhance, Omnilingual ASR, Whisper Tiny, Bangla Zipformer) with its size, and each can be deleted on its own.
- **Tidier Tools tab.** Removed the "On device" badge from the top bar; it did nothing when tapped. The banner is shorter and now reads "Transform audio, without the cloud." In wide windows (desktop, tablets) the tool cards stretch to fill each row instead of leaving empty space.

## [0.1.1] - 2026-11-13

### Fixed
- **Model downloads survive dropped connections.** On mobile data a 384 MB download could fail with "Connection reset" and start over. Downloads now retry automatically and resume from the bytes already saved, even after the app restarts.
- **Enhance speech no longer gets killed on phones.** The AI vocoder needed about 4.7 GB of memory, so Android stopped it (and closed other apps) even on 8 GB phones. It now uses a re-exported vocoder and a file-backed weight cache: about 1.5 GB at peak, it runs about three times faster, and its output matches the original model far more closely. Updating downloads only the new 384 MB vocoder; the other model files are kept.
- Settings → Open-source licenses now includes the full license text of every bundled and downloaded component ("Third-party notices"), and credits ONNX Runtime, LiteRT, JTransforms, JLargeArrays and Apache Commons Math. Whisper is correctly listed as MIT (OpenAI).

## [0.1.0] - 2026-11-13

First public release.

### Added
- **AI tools (Android):** Enhance speech with Resemble Enhance, and Transcribe with Whisper Tiny (auto, English, Hindi) or a Bangla Zipformer model. Models download once; audio never leaves the device.
- **Clean up:** Noise reduction (spectral subtraction) and Remove silence.
- **Edit:** Trim, Fade in/out, and Volume (peak normalize, loudness normalize to −14/−16/−23 LUFS per ITU-R BS.1770, or fixed gain).
- **Effects:** Speed and pitch (independent, WSOLA) and a six-preset Equalizer.
- **Utilities:** Convert sample rate (8–48 kHz), and Analyze (duration, peak, RMS, loudness, clipping, DC offset, silence).
- **Activity tab:** a job queue that runs one job at a time, with progress, cancel, and a history of finished jobs that survives restarts. Outputs can be played, saved or reopened in their tool.
- **Material 3 Expressive design:** dynamic colour on Android 12+, light, dark and pure-black themes, shape-morphing controls, bottom navigation on phones and a navigation rail on wide screens.
- **Settings:** theme, dynamic colour, pure black, default transcription language, keep screen on, and storage management for models and processed files.
- **Desktop apps for Linux and Windows** (DEB, RPM, MSI and EXE installers) with every DSP tool, analysis, playback, history and settings. AI tools are Android-only for now.
- Audiops is open source under the Apache License 2.0.
- Every downloaded AI model is pinned to a fixed version and verified with SHA-256 before use.
- Smaller downloads: an Android APK for each processor type (about 40 MB for most phones) besides the universal APK.
- Input formats: anything Android can decode (MP3, AAC/M4A, OGG, Opus, FLAC, WAV…); WAV, AIFF and AU on desktop. Output is 16-bit WAV.

[Unreleased]: https://github.com/sayedshaun/audiops/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/sayedshaun/audiops/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/sayedshaun/audiops/releases/tag/v0.1.0
