#!/usr/bin/env python3
"""Download the official, pinned Apache-2.0 Basic Pitch model at development/build time."""
import hashlib
from pathlib import Path
import urllib.request

COMMIT = 'fa5997af0a8210982619003269994a1be25eddf3'
BLOB_SHA1 = '85a41befdd036e9b365a052b7c704c6810288b95'
ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'app/src/main/assets/basic_pitch.tflite'

def verify(data):
    assert len(data) == 204448, 'Model size differs from pinned official source'
    assert data[4:8] == b'TFL3', 'Not a TensorFlow Lite model'
    assert hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest() == BLOB_SHA1, 'Model checksum mismatch'

if TARGET.exists():
    data = TARGET.read_bytes()
    verify(data)
else:
    url = f'https://raw.githubusercontent.com/spotify/basic-pitch/{COMMIT}/basic_pitch/saved_models/icassp_2022/nmp.tflite'
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    verify(data)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
print(f'Verified official Basic Pitch model: {len(data)} bytes, SHA256 {hashlib.sha256(data).hexdigest()}')
