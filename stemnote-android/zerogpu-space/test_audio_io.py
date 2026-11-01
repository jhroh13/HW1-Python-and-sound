import io
import json
import tempfile
import unittest
import wave
import zipfile
from pathlib import Path

import numpy as np
import soundfile as sf
from audio_io import load_audio, write_archive, SOURCES


class AudioContractTests(unittest.TestCase):
    def test_round_trip_and_shared_gain(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sf.write(root / "input.wav", np.zeros(22050), 22050)
            audio, count = load_audio(root / "input.wav")
            self.assertEqual((audio.shape, count), ((2, 44100), 22050))
            stems = np.zeros((6, 2, 44100), np.float32)
            t = np.arange(44100) / 44100
            stems[0, :, :] = 2 * np.sin(2 * np.pi * 440 * t)
            stems[1, :, :] = .5 * np.sin(2 * np.pi * 220 * t)
            output = write_archive(root / "stems.zip", "htdemucs_6s", SOURCES["htdemucs_6s"], stems, count)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.namelist()[0], "manifest.json")
                metadata = json.loads(archive.read("manifest.json"))
                self.assertLess(metadata["gain"], 1)
                self.assertEqual(len(metadata["stems"]), 6)
                for stem in metadata["stems"]:
                    data = archive.read(stem["file"])
                    with wave.open(io.BytesIO(data)) as pcm:
                        self.assertEqual((pcm.getnchannels(), pcm.getsampwidth(), pcm.getframerate(), pcm.getnframes()), (1, 2, 22050, 22050))
                    import hashlib
                    self.assertEqual(hashlib.sha256(data).hexdigest(), stem["sha256"])

    def test_reject_invalid_duration_and_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "short.wav"
            sf.write(path, np.zeros(100), 22050)
            with self.assertRaises(ValueError):
                load_audio(path)
            with self.assertRaises(ValueError):
                write_archive(Path(directory) / "bad.zip", "htdemucs_6s", ["vocals"], np.zeros((1, 2, 44100)), 22050)


if __name__ == "__main__":
    unittest.main()
