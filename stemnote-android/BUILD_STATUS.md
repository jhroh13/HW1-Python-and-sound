# Android 2.2 ZeroGPU verification

Target Space: https://huggingface.co/spaces/j0ngh0r0h/Midigen

- Actual Demucs htdemucs_6s and htdemucs_ft models, Gradio upload/queued call/download and 6/4 valid WAV stems passed on CPU in GitHub run 41358941502 (2026-11-01).
- Core Java tests and syntax checks passed.
- First Android Lint run found missing includeSubdomains attributes in the loopback test configuration. Those attributes have been corrected; the updated build and emulator checks must be run.
- Production Space conversion and real ZeroGPU verification require HF_TOKEN with write/Space hardware access. Runtime and normal local HF credential checks found no configured HF credentials.
- The existing remote Space was observed as public, sdk=static, commit 0a8d9bccb03419bd2b5179c217fcf14784e5a1ab. Prepared code alone does not mean it has changed remotely.

The workflow builds the native ARM64 APK, tests the actual on-device model and Gradio HTTP transport, then deploys the existing Midigen Space using the authorized HF_TOKEN secret. It uploads Gradio metadata/server code, preserves existing static files/visibility, requests ZeroGPU only and validates the deployed API with generated audio. No paid GPU fallback is configured.
