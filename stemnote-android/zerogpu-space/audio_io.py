"""Bounded PCM input and the versioned archive consumed by the native Android app."""
import hashlib
import io
import json
import math
import time
import wave
import zipfile
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

RATE = 22050
MAX_SECONDS = 120
SOURCES = {
    "htdemucs_6s": ("drums", "bass", "other", "vocals", "guitar", "piano"),
    "htdemucs_ft": ("drums", "bass", "other", "vocals"),
}


def resample(audio, rate_in, rate_out):
    divisor = math.gcd(rate_in, rate_out)
    return resample_poly(audio, rate_out // divisor, rate_in // divisor, axis=-1).astype(np.float32)


def load_audio(path):
    path = Path(path)
    if path.stat().st_size > 50 * 1024 * 1024:
        raise ValueError("WAV must be smaller than 50 MiB.")
    info = sf.info(path)
    if info.format != "WAV" or info.channels not in (1, 2) or not 8000 <= info.samplerate <= 96000:
        raise ValueError("Use a mono/stereo WAV at 8–96 kHz.")
    if not 0.1 <= info.frames / info.samplerate <= MAX_SECONDS:
        raise ValueError("Choose an audio section between 0.1 and 120 seconds.")
    audio, rate = sf.read(path, dtype="float32", always_2d=True)
    if not np.isfinite(audio).all():
        raise ValueError("Non-finite PCM samples.")
    audio = audio.T
    if len(audio) == 1:
        audio = np.repeat(audio, 2, axis=0)
    return resample(audio, rate, 44100), round(info.frames * RATE / rate)


def write_archive(destination, model, sources, audio, sample_count):
    if model not in SOURCES or set(sources) != set(SOURCES[model]) or len(sources) != len(set(sources)):
        raise ValueError("Unexpected source model or labels.")
    if audio.shape[0] != len(sources) or audio.shape[1] != 2 or not np.isfinite(audio).all():
        raise ValueError("Invalid separation output.")
    mono = resample(audio.mean(axis=1), 44100, RATE)
    mono = mono[:, :sample_count]
    if mono.shape[1] < sample_count:
        mono = np.pad(mono, ((0, 0), (0, sample_count - mono.shape[1])))
    peak = float(np.max(np.abs(mono)))
    gain = min(1.0, 0.98 / peak) if peak else 1.0
    mono *= gain  # A common gain keeps the relative level of all stems.
    entries = []
    for label, channel in zip(sources, mono):
        buffer = io.BytesIO()
        with wave.open(buffer, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            pcm = np.clip(np.rint(channel * 32767), -32768, 32767).astype("<i2")
            out.writeframes(pcm.tobytes())
        entries.append((label, buffer.getvalue()))
    manifest = {
        "schema": "stemnote.stems.v1", "model": model, "sample_rate": RATE,
        "sample_count": sample_count, "channels": 1, "gain": gain,
        "stems": [{"source": label, "file": label + ".wav", "sha256": hashlib.sha256(data).hexdigest()}
                  for label, data in entries],
        "limitations": "Guitar is combined. Other includes unclassified winds/synths. Drums are audio only in the Android app.",
    }
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as out:
        out.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False))
        for label, data in entries:
            out.writestr(label + ".wav", data)
    return str(destination)


def clean_old_results(folder, ttl=3600):
    folder.mkdir(parents=True, exist_ok=True)
    for file in folder.glob("*.zip"):
        if time.time() - file.stat().st_mtime > ttl:
            file.unlink(missing_ok=True)
