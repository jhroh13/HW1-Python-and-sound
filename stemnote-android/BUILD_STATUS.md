# Android 2.2 / Midigen deployment status

Verified build commit: 8400717150caaab39d6eaf315484034cdf9c4e6f
Workflow: https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/41359339809
Date: 2026-11-01 UTC

## Passed

- Native ARM64 APK assembleDebug and Android Lint.
- Android 11 x86_64 emulator: 8 tests passed. Actual Basic Pitch model/silence/single-note/chord/Activity plus Gradio HTTP upload→named queued call→SSE→download, vocal-excluded mix/WAV-only export, quota failure, archive integrity/path validation and Space URL validation.
- Actual Demucs htdemucs_6s and htdemucs_ft models and Gradio upload/queued call/download on CPU: valid 6/4 non-silent separated WAV tracks. Archive contract tests passed.
- APK v2 signature, ZIP CRC, DEX checksums, manifest, native TFLite runtime and bundled model checks. Local artifact transfer SHA-256 matches CI.
- Public Midigen Space repository cloned in the deployment job before authentication check.

APK: Stemnote-ZeroGPU-arm64.apk, 3717455 bytes.
Version: 2.2.0-zerogpu (versionCode 3), com.stemnote.app, Android 8/API26 minimum, target SDK35.
SHA-256: 204b0f25513098b55bfef53a20236c185330e03cf95d426f4c8bbae9710bc8a9
Signing certificate SHA-256: f91172dcbf647c0e2cf5cd20ef49f287ba24f2e6cf08cbda3fc4888184265dc6
Model SHA-256: 3db297d54af8e01c6e5618245c956b1d71b6a2b978cb2dedb527173186552676
Permissions: RECORD_AUDIO and INTERNET.
APK artifact: https://github.com/jhroh13/HW1-Python-and-sound/actions/runs/41359339809/artifacts/13104488535

## Deployment blocked by missing authentication

The deploy_midigen job (125006866071) stopped at the HF_TOKEN check. The repository Actions secret HF_TOKEN was empty; no remote write was attempted. Midigen was rechecked as sdk=static at commit 0a8d9bccb03419bd2b5179c217fcf14784e5a1ab after the failed job. The prepared Gradio source is not evidence of an applied remote conversion.

To continue, an authorized Hugging Face write token must be configured as repository Actions secret HF_TOKEN. Then rerun only this failed deployment job. It changes the existing Space README SDK to Gradio, uploads the server, preserves existing static files and visibility, requests ZeroGPU only and verifies the real deployed 4/6-track API with generated audio. No paid/dedicated GPU fallback is configured.

Target: https://huggingface.co/spaces/j0ngh0r0h/Midigen
App default endpoint after conversion: https://j0ngh0r0h-midigen.hf.space
Actions secret settings: https://github.com/jhroh13/HW1-Python-and-sound/settings/secrets/actions

Real ZeroGPU scheduling and live phone-to-Space integration are not yet verified. Physical ARM64 microphone/SAF flows and real-song transcription accuracy still require device/music testing. The emulator's network test uses a local protocol fixture; backend model testing uses CPU.

This debug certificate differs from the previous 2.1 preview. Export projects before uninstalling the old preview and installing this APK. Documentation-only commits after the verified build do not change this APK.
