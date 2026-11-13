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
- **Jobs keep running in the background on Android.** Enhance speech and other jobs continue when you switch apps, turn the screen off or swipe Audiops away from Recents. A notification shows the running job and its progress, with a Cancel button, and another tells you when a job finishes or fails while Audiops is closed. Tap it to open the Activity tab. Android 13+ asks for notification permission when you start your first job.

### Changed
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
