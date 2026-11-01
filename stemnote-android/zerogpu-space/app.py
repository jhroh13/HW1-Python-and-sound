"""Gradio ZeroGPU endpoint for the native Stemnote Android client."""
import os
import tempfile
import uuid
from pathlib import Path

import spaces  # Import before torch: ZeroGPU installs its CUDA emulation hooks.
import torch
import numpy as np
import gradio as gr
from demucs.pretrained import get_model
from demucs.apply import apply_model
from demucs.htdemucs import HTDemucs
from demucs.hdemucs import HDemucs
from demucs.demucs import Demucs
from audio_io import SOURCES, load_audio, write_archive, clean_old_results

DEVICE = os.environ.get("STEMNOTE_DEVICE", "cuda")  # Set cpu only for CI/developer smoke tests.
torch.set_num_threads(2)
# PyTorch 2.8 defaults to weights_only=True. Official Demucs packages include the model class.
# Allow only these known constructors; never load a user-supplied checkpoint.
with torch.serialization.safe_globals([HTDemucs, HDemucs, Demucs]):
    MODELS = {name: get_model(name).to(DEVICE).eval() for name in SOURCES}
RESULTS = Path(tempfile.gettempdir()) / "stemnote-results"


def gpu_duration(audio, model_name):
    seconds = audio.shape[-1] / 44100
    return min(120, max(15, int(seconds * (1.0 if model_name == "htdemucs_ft" else .5) + 10)))


@spaces.GPU(duration=gpu_duration)
def infer(audio, model_name):
    model = MODELS[model_name]
    mix = torch.from_numpy(audio).to(DEVICE)
    reference = mix.mean(0)
    mean, std = reference.mean(), reference.std().clamp_min(1e-6)
    with torch.inference_mode():
        result = apply_model(model, ((mix - mean) / std)[None], device=DEVICE,
                             shifts=1, split=True, overlap=.25, segment=7, num_workers=0)[0]
        result = result * std + mean / len(model.sources)
    return result.detach().cpu().numpy()


def separate(audio_file, model_name, progress=gr.Progress()):
    if model_name not in MODELS:
        raise gr.Error("Unknown model.")
    try:
        progress(.02, desc="Reading WAV")
        audio, count = load_audio(audio_file)
        clean_old_results(RESULTS)
        if float(np.max(np.abs(audio))) < 1e-7:
            separated = np.zeros((len(MODELS[model_name].sources), 2, audio.shape[-1]), np.float32)
        else:
            progress(.1, desc="Waiting for ZeroGPU / separating")
            separated = infer(audio, model_name)
        progress(.9, desc="Writing separated tracks")
        return write_archive(RESULTS / (uuid.uuid4().hex + ".zip"), model_name,
                             MODELS[model_name].sources, separated, count)
    except (ValueError, RuntimeError) as error:
        raise gr.Error(str(error)) from error


with gr.Blocks(delete_cache=(600, 3600)) as demo:
    gr.Markdown("# Midigen · ZeroGPU music separation\nNative Android client backend. WAV, 0.1–120 seconds.\n"
                "6 stems: vocals / drums / bass / guitar (combined) / piano / other.\n"
                "4 stems: vocals / drums / bass / other. Separation can contain leakage.\n"
                "Files may remain in this Space's temporary cache for approximately one hour while it is running.")
    audio = gr.File(label="WAV audio", file_types=[".wav"], type="filepath")
    model = gr.Dropdown(choices=list(SOURCES), value="htdemucs_6s", label="Separation model")
    output = gr.File(label="Separated WAV tracks + manifest ZIP")
    gr.Button("Separate").click(separate, [audio, model], output, api_name="separate", concurrency_limit=1)

if __name__ == "__main__":
    demo.queue(max_size=16).launch(max_file_size="50mb", show_error=True)
