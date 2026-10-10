export function formatTime(seconds) {
  const value = Math.max(0, Math.floor(Number.isFinite(seconds) ? seconds : 0));
  return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, '0')}`;
}

// Use the loudest channel in each time bucket so stereo cancellation cannot hide sound.
export function extractPeaks(buffer, buckets = 1200) {
  const peaks = new Float32Array(Math.min(buckets, buffer.length));
  for (let channel = 0; channel < buffer.numberOfChannels; channel++) {
    const samples = buffer.getChannelData(channel);
    for (let bucket = 0; bucket < peaks.length; bucket++) {
      const start = Math.floor(bucket * samples.length / peaks.length);
      const end = Math.floor((bucket + 1) * samples.length / peaks.length);
      for (let sample = start; sample < end; sample++) {
        peaks[bucket] = Math.max(peaks[bucket], Math.abs(samples[sample]));
      }
    }
  }
  return peaks;
}

export function createDemo() {
  const rate = 22050;
  const frames = rate * 10;
  const bytes = new ArrayBuffer(44 + frames * 2);
  const view = new DataView(bytes);
  const text = (offset, value) => [...value].forEach((char, i) => view.setUint8(offset + i, char.charCodeAt(0)));
  text(0, 'RIFF'); view.setUint32(4, bytes.byteLength - 8, true); text(8, 'WAVE');
  text(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
  view.setUint16(22, 1, true); view.setUint32(24, rate, true); view.setUint32(28, rate * 2, true);
  view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  text(36, 'data'); view.setUint32(40, frames * 2, true);
  const notes = [261.63, 329.63, 392, 523.25, 440, 392, 329.63, 293.66];
  for (let frame = 0; frame < frames; frame++) {
    const time = frame / rate;
    const phase = time % 0.5;
    const frequency = notes[Math.floor(time * 2) % notes.length];
    const envelope = Math.min(1, phase * 70) * Math.exp(-phase * 5);
    const fade = Math.min(1, time * 2, (10 - time) * 2);
    const sample = (Math.sin(2 * Math.PI * frequency * time) * 0.35 + Math.sin(2 * Math.PI * frequency * 2 * time) * 0.06) * envelope * fade;
    view.setInt16(44 + frame * 2, sample * 32767, true);
  }
  return new File([bytes], 'Little moments.wav', { type: 'audio/wav' });
}
