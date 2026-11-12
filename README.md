# AudioOps

An Android app for on-device audio operations. Recordings are processed on the phone; audio you select is never uploaded.

## Tools

- **Enhance audio** (experimental): improves the clarity of a recording with [Resemble Enhance LiteRT](https://huggingface.co/SayedShaun/resemble-enhance-litert) using INT8 weights. The model is by Resemble AI and is MIT licensed; see [its license](licenses/RESEMBLE-ENHANCE-MIT.txt). The first run downloads about 360 MB of model files into app-private storage. The result is saved as a WAV through the system document picker.
- **Transcribe speech**: speech-to-text through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), Apache 2.0 ([license](licenses/SHERPA-ONNX-APACHE-2.0.txt)).
  - *Auto*, *English* and *Hindi* use Whisper Tiny INT8 (about 103 MB on first use; sourced from [ippocode/indic-asr-onnx](https://huggingface.co/ippocode/indic-asr-onnx)).
  - *Bangla* uses [Bangla Zipformer](https://huggingface.co/SayedShaun/bangla-zipformer-onnx) by SayedShaun (about 74 MB). It is licensed under [CC BY-SA 4.0](licenses/BANGLA-ZIPFORMER-CC-BY-SA-4.0.txt); the model card lists the training-data attribution.
- **Remove noise**: planned. Enhancement already includes the model's denoising stage.

Supported input is whatever the device can decode, such as MP3, AAC/M4A, WAV and FLAC. Every recording is converted to mono 44.1 kHz locally. Long recordings are processed in sections; enhancement crossfades the overlaps, and transcription joins section results.

Model files are checked against SHA-256 digests (Whisper and Bangla). Downloads use HTTPS only. The enhancement pipeline is experimental: its speed and output quality need validation on physical devices.

## Requirements

- Android 8.0 (API 26) or newer.
- Internet access on first use, to download model files. Processing itself is offline.

## Build and test

Requirements: Android SDK Platform 37 and a JDK 17 with `javac` (Android Studio's bundled JDK works). The app is written in Kotlin with Jetpack Compose and Material 3.

```sh
./gradlew :app:testDebugUnitTest   # JVM unit tests
./gradlew :app:lintDebug           # Android lint
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:connectedDebugAndroidTest   # instrumented decoder tests (device or emulator)
```

Set `ANDROID_HOME` or `local.properties` so Gradle can find the SDK. To build for one ABI, pass `-PaudioOpsAbi=arm64-v8a`.

### Release builds

`./gradlew :app:assembleRelease` runs R8 and resource shrinking. To sign it, set:

| Variable | Meaning |
| --- | --- |
| `AUDIOOPS_KEYSTORE` | Path to the keystore file |
| `AUDIOOPS_KEYSTORE_PASSWORD` | Keystore password |
| `AUDIOOPS_KEY_ALIAS` | Key alias |
| `AUDIOOPS_KEY_PASSWORD` | Key password |

Without these, the release APK is unsigned. Keep keystores out of the repository; `*.jks` and `*.keystore` are ignored.

## Project layout

```
app/src/main/java/com/audiops/app/
  MainActivity.kt          Edge-to-edge host for the Compose app
  AudioOpsViewModel.kt     UI state, cancellation, saving, messages
  AudioPipelines.kt        Enhance and transcribe pipelines (run off the main thread)
  AudioDecoder.kt          Device decoding to 44.1 kHz mono PCM, chunking, cancellation checks
  ResembleEnhancer.kt      LiteRT inference, DSP, model download
  WhisperTranscriber.kt    Whisper Tiny via sherpa-onnx
  BanglaTranscriber.kt     Bangla Zipformer via sherpa-onnx
  ModelFiles.kt            Verified, resumable model downloads and shared progress listener
  WavAudio.kt              Streaming WAV writer
  ui/                      Material 3 theme, screens and shared components
app/src/test/              JVM unit tests (WAV output, chunk math, DSP shapes, solver)
app/src/androidTest/       Instrumented decoder tests with sample audio
```

## Known limitations

- Processing runs only while the app process is alive. Leaving the app for a long recording is fine, but if the system kills the process, the job stops and must be started again. Keeping work alive in the background would need a foreground service.
- Whisper and Bangla sections are cut at fixed lengths (30 s and 20 s), so a word at a boundary can split across two sections.
- The enhancement models are downloaded without a SHA-256 check. Only their size is verified.
- The UI is English only. Strings are in `res/values/strings.xml` and ready for translation.

## Contributing

Issues and pull requests are welcome. Run the unit tests and lint before opening a pull request. The project keeps `CHANGES.md` (a changelog, appended per change) and `PROGRESS.md` (a current status snapshot); see [CLAUDE.md](CLAUDE.md).

## License

Licensing for AudioOps' own source code has not been chosen yet. Third-party models and runtimes are licensed as described above.
