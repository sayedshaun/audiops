# AudioOps: working notes for Claude

AudioOps is an on-device Android app (Kotlin, Jetpack Compose, Material 3). Read `README.md` for the product and the tools.

## Build and test

- Gradle needs a JDK with `javac`. On this machine, `/usr/lib/jvm/java-21-openjdk-amd64` is a JRE only. Use the Temurin 17 that Gradle provisions: `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2`.
- `ANDROID_HOME=$HOME/Android/Sdk`.
- Verify changes with `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`. CI runs the same checks.
- The git metadata is in `.audiops-git`, not `.git`. Prefix git commands with `GIT_DIR=/home/shaun/audiops/.audiops-git GIT_WORK_TREE=/home/shaun/audiops`.

## Architecture rules

- Keep UI state in `AudioOpsViewModel`. Composables render state and call the ViewModel; they do not run audio work.
- Long-running work goes through `AudioPipelines`, which runs on a worker thread via `runInterruptible`. Cancellation depends on `throwIfCancelled()` checks inside every loop, so new loops must call it.
- Report progress through `ProcessingListener`. Do not update UI state from the pipelines directly.
- User-visible strings belong in `res/values/strings.xml`.
- Model files are verified with SHA-256 digests (`ModelFiles`). Do not change a model revision or digest without re-verifying the download.

## Conventions

- Maintain `CHANGES.md` (append one entry per meaningful change; never rewrite old entries) and `PROGRESS.md` (a current snapshot: what is done, what is next, open questions; update it in place).
- Commit messages carry no AI attribution or co-author trailer. Commits list the user as the sole author.
- Create a descriptively named branch before starting each new type of work (see `AGENTS.md`).
