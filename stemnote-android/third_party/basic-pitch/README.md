# Basic Pitch provenance

Official repository: https://github.com/spotify/basic-pitch

Pinned commit: `fa5997af0a8210982619003269994a1be25eddf3`.
Model: `basic_pitch/saved_models/icassp_2022/nmp.tflite`, 204448 bytes.
Git blob SHA-1: `85a41befdd036e9b365a052b7c704c6810288b95`.

The model is downloaded and verified during development/CI using `tools/fetch_model.py`, then packaged inside the APK. There is no model download or audio upload at runtime.

The model and upstream notices are provided under Apache-2.0. `LICENSE` and `NOTICE` are preserved here and in app assets. The upstream NOTICE also mentions Python dependencies; the Android app does not bundle the Python package or those Python runtimes. The Android integration and note decoder are implemented in Java. The model is unchanged.

Reference constants/output signatures: `basic_pitch/constants.py`, `basic_pitch/inference.py` at the pinned commit. Input `[1,43844,1]`, mono float PCM, 22050 Hz; outputs `note`, `onset` `[1,172,88]`, and `contour` `[1,172,264]`. Only note/onset outputs are decoded; pitch bends are not exported.

Basic Pitch estimates pitched notes; it does not separate sources, identify instruments, or transcribe unpitched percussion. Mixed songs can contain missed or extra notes. Evaluate on music with reference MIDI before claiming transcription accuracy.

Android Java inference API: https://developers.google.com/edge/api/tflite/java/org/tensorflow/lite/Interpreter
