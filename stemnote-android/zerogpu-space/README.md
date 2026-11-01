---
title: Stemnote ZeroGPU Separation
emoji: 🎼
colorFrom: green
colorTo: blue
sdk: gradio
sdk_version: 5.49.1
python_version: 3.12.12
app_file: app.py
tags:
  - zerogpu
  - music-source-separation
---

Native Android backend: Demucs 6-track / fine-tuned 4-track separation on Hugging Face ZeroGPU.

Upload the files in this folder to a Gradio Space, then select **ZeroGPU in the Space hardware settings**. README metadata alone does not enable ZeroGPU. Account eligibility and GPU quotas apply; see https://huggingface.co/docs/hub/spaces-zerogpu . The app's URL is `https://OWNER-SPACE.hf.space`, not the Hugging Face Space page URL.

`POST /gradio_api/upload` uploads the phone's WAV. `/gradio_api/call/separate` accepts `[FileData, model_name]`; the completion event contains one ZIP FileData. ZIP contract: `manifest.json` first, then named PCM16 mono 22050 Hz WAV stems with equal sample counts and SHA256 checksums. Max section length is 120 seconds. The model is preloaded onto CUDA at module startup as required by current ZeroGPU guidance; CUDA work runs inside `@spaces.GPU`.

Model names: `htdemucs_6s` (vocals, drums, bass, guitar, piano, other) and `htdemucs_ft` (vocals, drums, bass, other). No acoustic/electric guitar split or independent wind/synth identification. Piano and other stems may contain leakage. Separation quality requires listening checks against the original. The Android app runs pitched-note transcription after downloading stems; drum MIDI transcription is not included.

For CPU validation, install the matching CPU Torch/Torchaudio wheels and these dependencies, set `STEMNOTE_DEVICE=cpu`, and import/run the app. This checks the pipeline but does not verify ZeroGPU scheduling. GPU allocation and a real phone-to-Space call require an enabled Space and must be verified after deployment.

Gradio uploads/results and result ZIPs use temporary cache with roughly one-hour cleanup while the Space is running. No raw audio is committed to GitHub. A private Space and a user-provided read token are supported by the Android client. Tokens are not embedded in the APK.

Demucs: https://github.com/facebookresearch/demucs (MIT). Current ZeroGPU: https://huggingface.co/docs/hub/spaces-zerogpu . Gradio API: https://www.gradio.app/guides/querying-gradio-apps-with-curl .
