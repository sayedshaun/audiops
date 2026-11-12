# Audiops

An open-source, on-device audio toolkit for Android, with Linux and Windows desktop builds from the same Kotlin Multiplatform codebase. Every tool runs locally; audio you select is never uploaded. The network is used only to download AI models once.

## Tools

| | Tool | What it does |
| --- | --- | --- |
| AI | **Enhance speech** | Restores clarity in voice recordings with [Resemble Enhance](https://huggingface.co/SayedShaun/resemble-enhance-litert) (LiteRT, INT8; ~360 MB download). MIT licensed by Resemble AI. |
| AI | **Transcribe** | Speech to text. Auto, English and Hindi use Whisper Tiny INT8 (~103 MB, [ippocode/indic-asr-onnx](https://huggingface.co/ippocode/indic-asr-onnx)); Bangla uses [Bangla Zipformer](https://huggingface.co/SayedShaun/bangla-zipformer-onnx) (~74 MB, CC BY-SA 4.0). Runs on [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). |
| Clean up | **Noise reduction** | Learns the noise from the quietest parts of the recording and removes it (spectral subtraction with smoothing). Light, medium or strong. |
| Clean up | **Remove silence** | Shortens pauses longer than a chosen length, keeping natural padding. |
| Edit | **Trim** | Keep a range, chosen on the waveform. |
| Edit | **Fade** | Smooth fade in and fade out. |
| Edit | **Volume** | Peak normalization, loudness normalization to −14/−16/−23 LUFS (ITU-R BS.1770 with a look-ahead limiter), or gain. |
| Effects | **Speed and pitch** | 0.5×–2× tempo without pitch change (WSOLA), ±12 semitones without tempo change. |
| Effects | **Equalizer** | Voice clarity, bass boost, treble boost, warm, remove rumble, telephone. |
| Utilities | **Convert** | Resample to 8, 16, 22.05, 44.1 or 48 kHz (anti-aliased). |
| Utilities | **Analyze** | Integrated loudness, peak, RMS, clipping, silence and DC offset. |

Any format the device can decode is accepted (MP3, AAC/M4A, WAV, FLAC, OGG…); results are mono 16-bit WAV files. Jobs run in a queue: start several tools, keep working, and follow progress in the **Activity** tab, where finished results stay available to play, save or delete.

All DSP is written in Kotlin and streams audio in blocks, so memory use stays constant for long recordings. On a desktop JVM the slowest operation (pitch shift) runs at about 45× real time; most run at several hundred times real time.

### Platform support

| | Android 8.0+ | Linux / Windows desktop |
| --- | --- | --- |
| DSP tools, Analyze, playback, history, settings | Yes | Yes |
| AI Enhance and Transcribe | Yes | Not yet |
| Input formats | Anything the device decodes | WAV, AIFF, AU |

## Design

Material 3 Expressive throughout: dynamic colour (Android 12+), light, dark and pure-black themes, expressive shapes and spring motion, shape-morphing buttons, connected button groups, a short navigation bar on phones and a navigation rail on wide windows. Predictive back, edge-to-edge, haptics, screen-reader labels and large touch targets are built in.

## Build

Requirements: JDK 17 with `javac` (Android Studio's bundled JDK works) and Android SDK Platform 37.

```sh
./gradlew :app:installDebug                  # Android, on a device or emulator
./gradlew :desktopApp:run                    # desktop
./gradlew :desktopApp:packageDistributionForCurrentOS   # DEB/RPM on Linux, MSI/EXE on Windows
```

Set `ANDROID_HOME` or `local.properties` so Gradle can find the SDK. To build Android for one ABI, pass `-PaudiopsAbi=arm64-v8a`.

### Tests

```sh
./gradlew :shared:desktopTest   # DSP correctness and performance, chunking, WAV, model math
./gradlew :desktopApp:test      # desktop engine: decoding, processing, errors
./gradlew :app:lintDebug        # Android lint
./gradlew :app:connectedDebugAndroidTest   # instrumented decoder tests
```

### Release builds

`./gradlew :app:assembleRelease` runs R8 and resource shrinking. To sign it, set `AUDIOPS_KEYSTORE`, `AUDIOPS_KEYSTORE_PASSWORD`, `AUDIOPS_KEY_ALIAS` and `AUDIOPS_KEY_PASSWORD`; without them the APK is unsigned. Keystores are ignored by Git.

## Project layout

```
shared/      Kotlin Multiplatform: Compose UI, ViewModel, settings, history (commonMain);
             DSP, WAV, model downloads, JvmAudioEngine (jvmShared); platform actuals
app/         Android app and engine: MediaCodec decoder, LiteRT enhancer, sherpa-onnx
desktopApp/  Desktop app and engine (Java Sound)
```

See [AGENTS.md](AGENTS.md) for architecture, conventions and the design system.

## Known limitations

- Jobs run while the app process is alive. If the system stops the app in the background, a running job ends and must be started again.
- AI enhancement is heavy. It loads one model at a time, but the vocoder alone needs up to ~3 GB, so phones with little free memory cannot finish it; it runs in a separate process, so the app stays open and explains the failure. It also takes several times longer than the recording on most phones.
- Transcription cuts sections at fixed lengths (30 s, 20 s for Bangla), so a word at a boundary can split.
- The enhancement models are downloaded without a SHA-256 check; Whisper and Bangla models are verified.
- Results are mono. Stereo input is mixed down.
- The UI is English only; strings are ready for translation.

## Contributing

Issues and pull requests are welcome. Run the tests and lint before opening a pull request. The project keeps `CHANGES.md` (a changelog) and `PROGRESS.md` (a status snapshot).

## License

Licensing for Audiops' own source code has not been chosen yet. Third-party models and runtimes are licensed as described above and in `licenses/`.
