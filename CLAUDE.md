# Audiops: notes for Claude Code

@AGENTS.md

## Claude-specific notes

- **JDK:** `/usr/lib/jvm/java-21-openjdk-amd64` on this machine is a JRE without `javac`. Use the Temurin 17 that Gradle provisioned:
  `export JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ANDROID_HOME=$HOME/Android/Sdk`
- **Git:** prefix commands with `GIT_DIR=/home/shaun/audiops/.audiops-git GIT_WORK_TREE=/home/shaun/audiops`.
- **Shell:** zsh does not split unquoted variables. Loop over file lists with `while IFS= read -r f; do …; done`. Never `pkill -f` a pattern that appears in your own command line.
- **Emulator:** `emulator-5554` is usually running; use `ANDROID_SERIAL=emulator-5554`. A physical phone may also be connected; do not install on it unless asked. Leave the user's files in `/sdcard/Download` alone; remove anything you push there.
- **UI automation:** `uiautomator dump` waits for the UI to go idle, so it stalls while the Expressive loading indicator or wavy progress animate. While a job runs, poll with screenshots and fixed coordinates; dump only when idle. Delete `/sdcard/ui.xml` before each dump (a failed dump otherwise leaves a stale file). Wait for scrolling to settle before tapping (a tap during a fling only stops the scroll).
- **Unknown Material 3 Expressive APIs:** names differ between CMP Material 3 `1.12.0-alpha03` and newer Jetpack builds (for example `ToggleButtonDefaults.toggleButtonColors` vs `colors`). Check before using: compile a small probe, or read the jar under `~/.gradle/caches/*/transforms/*/transformed/material3*/jars/classes.jar` with `javap`.
- **Before committing:** run `make check` with the `JAVA_HOME` above exported (the Makefile calls `./gradlew`). If `spotlessCheck` fails, run `make format` and review the diff before staging.
- **Verify UI on the emulator** (`adb exec-out screencap -p`) in light and dark, not only by compiling.
