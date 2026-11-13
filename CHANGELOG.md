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

[Unreleased]: https://github.com/sayedshaun/audiops/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/sayedshaun/audiops/releases/tag/v0.1.0
