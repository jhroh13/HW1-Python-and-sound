#!/usr/bin/env python3
"""Run with an authorized Hugging Face write token in HF_TOKEN or the normal HF login."""
import argparse
from pathlib import Path
from huggingface_hub import HfApi, get_token

parser = argparse.ArgumentParser()
parser.add_argument("repo_id", help="Hugging Face USER/SPACE")
parser.add_argument("--public", action="store_true", help="Create a public Space (default private)")
args = parser.parse_args()
if not get_token():
    parser.error("Configure HF_TOKEN securely or run hf auth login on your own computer; do not paste a token into chat.")
api = HfApi()
api.create_repo(args.repo_id, repo_type="space", space_sdk="gradio", private=not args.public, exist_ok=True)
api.upload_folder(repo_id=args.repo_id, repo_type="space", folder_path=Path(__file__).resolve().parents[1] / "zerogpu-space",
                  ignore_patterns=["__pycache__/*", "*.pyc", "test_*.py", "smoke_test.py"], commit_message="Deploy Stemnote ZeroGPU source separation")
print(f"Uploaded: https://huggingface.co/spaces/{args.repo_id}")
print("Select ZeroGPU in the Space hardware settings. Eligibility/quota rules apply. No paid GPU is requested automatically.")
print("After the Space starts, enter its https://...hf.space runtime address in the Android app.")
