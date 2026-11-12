# Audiops: notes for Claude Code

@AGENTS.md

## Claude-specific notes

- **JDK:** `/usr/lib/jvm/java-21-openjdk-amd64` on this machine is a JRE without `javac`. Use the Temurin 17 that Gradle provisioned:
  `export JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ANDROID_HOME=$HOME/Android/Sdk`
- **Git:** prefix commands with `GIT_DIR=/home/shaun/audiops/.audiops-git GIT_WORK_TREE=/home/shaun/audiops`.
- **Shell:** zsh does not split unquoted variables. Loop over file lists with `while IFS= read -r f; do …; done`.
- **Emulator:** an emulator (`emulator-5554`) is often already running; use `ANDROID_SERIAL=emulator-5554`. A physical phone may also be connected; do not install on it unless asked.
- **Unknown Material 3 Expressive APIs:** names changed across the 1.4/1.5 alphas. Check before using: compile a small probe, or read the jar under `~/.gradle/caches/*/transforms/*/transformed/material3/jars/classes.jar` with `javap`.
- **Verify UI on the emulator** (`adb exec-out screencap -p`) in light and dark, not only by compiling.
