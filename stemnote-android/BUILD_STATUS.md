# Verified APK build

- Repository: https://github.com/jhroh13/HW1-Python-and-sound
- Branch: codex/stemnote-android-apk
- Build commit: 53f5b5d92533a891ce182d7ce8163679e0f22f29
- Workflow: https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/39599522329
- Result: success (2026-10-21 UTC)
- Commands: Gradle :app:assembleDebug :app:lintDebug, bash tests/run.sh
- APK: app-debug.apk (49,787 bytes)
- Package: com.stemnote.app
- Version: 2.0.0-native-preview (versionCode 1)
- Minimum: Android 8.0 / API 26; target API 35
- Signing: debug certificate, APK Signature Scheme v2 verified by Android SDK apksigner
- SHA-256: decaca623cc3b4d36c1292a022329790856febbbc37d9dbd1a63818bcd1c0a5b
- Only requested permission: android.permission.RECORD_AUDIO
- ZIP CRC, DEX SHA-1/Adler32, manifest and local transfer SHA-256: passed
- Physical device and emulator execution: not tested

GitHub Actions artifacts expire after 14 days. The source and workflow remain in the branch for rebuilding. Debug signing keys generated on different runners may differ; future debug APK updates may require uninstalling the previous preview. No production signing key or Play Store release is provided.
