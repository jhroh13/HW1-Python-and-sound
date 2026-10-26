# Verified Android AI APK

- Repository: https://github.com/jhroh13/HW1-Python-and-sound
- Branch: codex/stemnote-ondevice-ai
- Verified build commit: 41949f8a9fac7e643172980821e3bbe6339f4ced
- Workflow: https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/40269815833
- Result: success, 2026-10-26 UTC
- APK artifact: https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/40269815833/artifacts/12627971036
- Test reports artifact: https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/40269815833/artifacts/12627891545
- APK: Stemnote-Android-AI-arm64.apk, 3700391 bytes
- APK SHA-256: 012dfd7a25cb296135f5eba39502d66ea308cb2b413c65fda8c8ef1c6ec0316b
- Package: com.stemnote.app
- Version: 2.1.0-ondevice-ai, versionCode 2
- Minimum API 26 / Android 8.0; target SDK 35; ABI arm64-v8a
- Only requested permission: android.permission.RECORD_AUDIO
- Debug signing certificate SHA-256: 19554d84e86d90339dc79cbee810b695aed6a15d1371606377582910ae38a53c
- APK Signature Scheme v2, ZIP CRC, DEX SHA-1/Adler32, manifest checks: passed
- Official Basic Pitch model: 204448 bytes
- Model SHA-256: 3db297d54af8e01c6e5618245c956b1d71b6a2b978cb2dedb527173186552676
- Model provenance: pinned official Git blob 85a41befdd036e9b365a052b7c704c6810288b95, checked before build
- Gradle assembleDebug and lintDebug: passed
- Core tests: MIDI/WAV/ZIP, resampling, note decoding, polyphony, repeated onsets, dynamics, 120-second sample-aligned window seams, unequal stem lengths: passed
- Android 11 / API 30 x86_64 emulator: 4 instrumented tests passed
  - Native Activity starts
  - Actual bundled model produces no notes for silence
  - Known A4 note is detected across a window seam with onset/duration checks
  - Known C-major triad pitches are detected
- Physical ARM64 device execution, microphone/SAF UI flows and real-song transcription accuracy: not yet verified

The Android tests run the actual TFLite model and production Java transcription pipeline. Synthetic examples do not measure real-song accuracy or prove source separation. This model does not identify instruments, remove vocals, or transcribe unpitched drums.

The APK certificate differs from the 2.0 preview. Export existing projects before uninstalling that app to install this version. This is a debug build, not a Play Store release.

The APK GitHub artifact expires on 2026-11-09 UTC. Source and workflow remain available for rebuilding. Documentation-only commits after the verified build do not change this APK.
