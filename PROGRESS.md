# Progress

Snapshot as of 2026-11-12, branch `feature/production-hardening-material3`. Updated in place; the history is in `CHANGES.md`.

## Status

The app is production-ready in structure and UI. It has been compiled, unit-tested, lint-checked, R8-built and exercised on an emulator. It has not been run on physical hardware or through the enhancement pipeline.

### Done
- Architecture: ViewModel-driven state, pipelines separated from the UI, cooperative cancellation, shared model verification.
- Material 3 UI: home, Enhance and Transcribe screens, light and dark themes, dynamic colour on Android 12+.
- Bugs fixed: non-cancellable processing, lost state on rotation, leaked cache files, cancellation reported as an error, generic output file names.
- Tests: 14 JVM unit tests pass.
- Build: lint has 0 errors. The release build passes R8 with the keep rules in `app/proguard-rules.pro`.
- CI: GitHub Actions workflow in `.github/workflows/android.yml`.
- Docs: README, CLAUDE.md, CHANGES.md, this file.
- Emulator checks: home, Enhance and Transcribe screens render in both themes. Transcription of `mono-16k-test.wav` produced a transcript. Rotation keeps the state.

### Not yet done
- Enhancement pipeline: not run end to end (it downloads about 360 MB on first use).
- Bangla path: not run on the device in this session.
- Physical devices: not tested. The emulator is an x86_64 image; the released app targets arm64.
- Remove noise: planned, not implemented. The tile is disabled.

## Open questions

1. **Licence for AudioOps' own code.** No `LICENSE` file exists yet. Choose one (for example MIT, Apache-2.0 or GPL-3.0) before publishing as open source. Third-party model licences are already in `licenses/` and the README.
2. **Target SDK.** `targetSdk` is 36 and lint flags it against compile SDK 37. Raising it changes runtime behaviour, so it needs a test pass on a device.
3. **LiteRT runtime.** Lint reports LiteRT 2.3.2 available (we use 2.3.0). Bump only after the enhancement pipeline is re-verified.
4. **Enhancement model integrity.** The Resemble model files have no SHA-256 check. Adding one needs the digests from the model repository.
5. **Background processing.** Work stops if the process is killed. A foreground service would fix that, at the cost of a persistent notification.
6. **Localisation.** Strings are ready for translation. Bangla UI text is an obvious candidate.
7. **Release signing.** Decide where the release keystore lives and who holds it.

## Next steps

1. Answer open question 1, then add `LICENSE`.
2. Run the enhancement pipeline and the Bangla transcription on a physical arm64 phone. Record timings for a 1-minute and a 10-minute recording.
3. Decide on target SDK 37 and the LiteRT bump, then re-run the device checks.
4. Add tests for the save paths and the ViewModel state transitions (Robolectric or instrumented).
