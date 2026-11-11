# AudioOps

An Android app foundation for on-device audio enhancement, noise removal, and speech transcription. The app lets you select an audio file with the Android document picker and explore each operation. **No ML models or audio processing are integrated yet.**

## Run

Open the project in Android Studio with Android SDK Platform 37 and JDK 17 or newer, then run the `app` configuration on an Android 8.0 (API 26) or newer device. From a terminal, use `./gradlew assembleDebug` after setting up the Android SDK (`ANDROID_HOME` or `local.properties`).

The selected file is kept only for the current app session; the app does not upload it. A future processing implementation must handle file access and lifetime, decode/resample audio, run an appropriately licensed on-device model, and write results safely. Choose and benchmark models on representative devices before committing to runtimes or formats.
