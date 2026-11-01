#!/usr/bin/env python3
"""Convert the authorized existing Midigen Space to Gradio and request ZeroGPU only."""
import argparse
import io
import json
import math
import struct
import tempfile
import time
import wave
import zipfile
from pathlib import Path
from huggingface_hub import HfApi, get_token


def verify_api(host, token):
    from gradio_client import Client, handle_file
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "generated-smoke.wav"
        with wave.open(str(path), "wb") as audio:
            audio.setnchannels(1)
            audio.setsampwidth(2)
            audio.setframerate(22050)
            audio.writeframes(b"".join(struct.pack("<h", int(6000 * math.sin(2 * math.pi * 220 * i / 22050))) for i in range(11025)))
        client = Client(host, hf_token=token, verbose=False)
        for model, count in (("htdemucs_6s", 6), ("htdemucs_ft", 4)):
            result = client.predict(handle_file(str(path)), model, api_name="/separate")
            with zipfile.ZipFile(result) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                assert manifest["schema"] == "stemnote.stems.v1" and manifest["model"] == model
                assert manifest["sample_count"] == 11025 and len(manifest["stems"]) == count
                for stem in manifest["stems"]:
                    with wave.open(io.BytesIO(archive.read(stem["file"]))) as audio:
                        assert (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getnframes()) == (1, 2, 22050, 11025)
            print(f"PASS: deployed ZeroGPU {model}, {count} valid WAV stems")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("repo_id", nargs="?", default="j0ngh0r0h/Midigen")
    parser.add_argument("--verify", action="store_true", help="Wait for startup and run real deployed GPU API tests")
    args = parser.parse_args()
    token = get_token()
    if not token:
        parser.error("HF write authentication is missing. Configure GitHub Actions secret HF_TOKEN; do not paste it into chat or code.")
    api = HfApi(token=token)
    before = api.space_info(args.repo_id)  # Require an existing authorized Space; preserve its visibility.
    print(f"Space: {args.repo_id}; current SDK: {before.sdk}")
    api.upload_folder(repo_id=args.repo_id, repo_type="space", parent_commit=before.sha,
                      folder_path=Path(__file__).resolve().parents[1] / "zerogpu-space",
                      ignore_patterns=["__pycache__/*", "*.pyc", "test_*.py", "smoke_test.py"],
                      commit_message="Switch Midigen from static to Gradio ZeroGPU music separation")
    updated = api.space_info(args.repo_id)
    if updated.sdk != "gradio":
        raise RuntimeError("Uploaded source but Space metadata has not switched to Gradio; check README YAML.")
    print("Converted SDK to Gradio. Existing static files and Space visibility were preserved.")
    api.request_space_hardware(repo_id=args.repo_id, hardware="zero-a10g")
    print("Requested ZeroGPU only. No paid/dedicated GPU fallback is configured.")
    if args.verify:
        deadline = time.monotonic() + 900
        while True:
            runtime = api.get_space_runtime(args.repo_id)
            stage = str(runtime.stage.value if hasattr(runtime.stage, "value") else runtime.stage)
            if stage == "RUNNING" and runtime.hardware == "zero-a10g":
                break
            if stage in ("BUILD_ERROR", "RUNTIME_ERROR", "CONFIG_ERROR"):
                raise RuntimeError(f"Space startup failed: {stage}. Check the Space build/runtime logs.")
            if time.monotonic() > deadline:
                raise RuntimeError(f"Space startup timed out: stage={stage}, hardware={runtime.hardware}")
            print(f"Waiting for Gradio/ZeroGPU: {stage}", flush=True)
            time.sleep(10)
        host = api.space_info(args.repo_id).host
        verify_api(host, token)
        print(f"Ready: {host}")
    else:
        print("Source/hardware request submitted; live GPU verification was not requested.")


if __name__ == "__main__":
    main()
