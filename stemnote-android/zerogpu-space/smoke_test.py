"""CI: actual Demucs models + Gradio HTTP upload/call/download on CPU. No user audio."""
import json
import os
import tempfile
import zipfile
from pathlib import Path

os.environ["STEMNOTE_DEVICE"] = "cpu"
import numpy as np
import soundfile as sf
from gradio_client import Client, handle_file
from app import demo

with tempfile.TemporaryDirectory() as directory:
    folder = Path(directory)
    t = np.arange(22050, dtype=np.float32) / 22050
    audio = .12 * np.sin(2 * np.pi * 220 * t) + .05 * np.sin(2 * np.pi * 440 * t)
    input_path = folder / "generated.wav"
    sf.write(input_path, audio, 22050, subtype="PCM_16")
    demo.queue().launch(server_name="127.0.0.1", server_port=7865, prevent_thread_lock=True, quiet=True)
    try:
        client = Client("http://127.0.0.1:7865", verbose=False)
        for name, expected in [("htdemucs_6s", 6), ("htdemucs_ft", 4)]:
            result = client.predict(handle_file(str(input_path)), name, api_name="/separate")
            with zipfile.ZipFile(result) as archive:
                metadata = json.loads(archive.read("manifest.json"))
                assert metadata["model"] == name and metadata["sample_count"] == len(audio)
                assert len(metadata["stems"]) == expected
                energy = 0
                for stem in metadata["stems"]:
                    import io
                    pcm, rate = sf.read(io.BytesIO(archive.read(stem["file"])))
                    assert pcm.shape == audio.shape and rate == 22050 and np.isfinite(pcm).all()
                    energy += float(np.sum(pcm * pcm))
                assert energy > .01
                print(f"PASS: actual {name} model, Gradio upload/queued call/download, {expected} valid non-silent stems")
    finally:
        demo.close()
