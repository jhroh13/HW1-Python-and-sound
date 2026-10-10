import './style.css';
import { createDemo, extractPeaks, formatTime } from './audio.js';

const $ = (id) => document.getElementById(id);
const audio = $('audio');
const canvas = $('waveform');
const context = canvas.getContext('2d');
const controls = ['play', 'rewind', 'forward', 'mute', 'volume'];
let peaks = null;
let duration = 0;
let objectUrl = null;
let loading = false;
let loadVersion = 0;
let animationFrame = null;
let previousVolume = 0.75;
audio.volume = previousVolume;

function message(text, error = false) {
  $('message').textContent = text;
  $('message').classList.toggle('error', error);
}

function setControls() {
  controls.forEach((id) => { $(id).disabled = !peaks || loading; });
  canvas.setAttribute('aria-disabled', String(!peaks || loading));
  $('choose-file').disabled = loading;
  $('load-demo').disabled = loading;
  $('drop-zone').setAttribute('aria-busy', String(loading));
}

function draw() {
  const { width, height } = canvas.getBoundingClientRect();
  const ratio = window.devicePixelRatio || 1;
  if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
  }
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, width, height);
  const center = height / 2;
  context.strokeStyle = '#e9e7e0';
  context.lineWidth = 1;
  context.beginPath(); context.moveTo(0, center); context.lineTo(width, center); context.stroke();
  if (!peaks) return;
  const progress = Math.min(1, audio.currentTime / duration || 0);
  const count = Math.max(1, Math.floor(width / 5));
  for (let bar = 0; bar < count; bar++) {
    const start = Math.floor(bar * peaks.length / count);
    const end = Math.max(start + 1, Math.floor((bar + 1) * peaks.length / count));
    let peak = 0;
    for (let p = start; p < end && p < peaks.length; p++) peak = Math.max(peak, peaks[p]);
    const size = Math.max(2, Math.min(1, peak) * (height - 28));
    context.fillStyle = bar / count < progress ? '#ed5c36' : '#414940';
    context.beginPath(); context.roundRect(bar * width / count, center - size / 2, 2.5, size, 2); context.fill();
  }
  context.fillStyle = '#ed5c36';
  context.fillRect(Math.min(width - 1.5, progress * width), 8, 1.5, height - 16);
}

function updatePosition() {
  $('current-time').textContent = formatTime(audio.currentTime);
  canvas.setAttribute('aria-valuenow', String(Math.floor(audio.currentTime)));
  canvas.setAttribute('aria-valuetext', `${formatTime(audio.currentTime)} / ${formatTime(duration)}`);
  draw();
}

function animate() {
  updatePosition();
  animationFrame = audio.paused ? null : requestAnimationFrame(animate);
}

function updatePlayback() {
  const playing = !audio.paused;
  $('play').setAttribute('aria-label', playing ? '일시정지' : '재생');
  $('play').innerHTML = playing
    ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 4h4v16H6zM14 4h4v16h-4z" /></svg>'
    : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m8 4 13 8-13 8Z" /></svg>';
  $('state-badge').textContent = loading ? '분석 중' : playing ? '재생 중' : peaks ? '재생 준비' : '준비됨';
  $('state-badge').classList.toggle('playing', playing);
  if (animationFrame !== null) cancelAnimationFrame(animationFrame);
  animationFrame = null;
  if (playing) animate(); else updatePosition();
}

async function loadFile(file, demo = false) {
  if (!file) return;
  if (file.size > 100 * 1024 * 1024) return message('100 MB 이하의 음원을 선택해주세요.', true);
  if (!file.size) return message('빈 파일은 불러올 수 없습니다. 다른 음원을 선택해주세요.', true);
  if (!file.type.startsWith('audio/') && !/\.(mp3|wav|ogg|m4a|aac|flac|webm|aiff?|opus)$/i.test(file.name)) {
    return message('오디오 파일을 선택해주세요. MP3, WAV, OGG 등을 사용할 수 있어요.', true);
  }
  const version = ++loadVersion;
  audio.pause();
  loading = true;
  setControls(); updatePlayback();
  message('음원을 불러오고 파형을 분석하고 있어요…');
  let decoder;
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) throw new Error('unsupported');
    decoder = new AudioContextClass();
    const buffer = await decoder.decodeAudioData(await file.arrayBuffer());
    if (version !== loadVersion) return;
    const nextPeaks = extractPeaks(buffer);
    const nextUrl = URL.createObjectURL(file);
    const oldUrl = objectUrl;
    audio.src = nextUrl;
    audio.load();
    objectUrl = nextUrl;
    if (oldUrl) URL.revokeObjectURL(oldUrl);
    peaks = nextPeaks;
    duration = buffer.duration;
    $('track-name').textContent = file.name;
    const size = file.size < 1024 * 1024 ? `${(file.size / 1024).toFixed(0)} KB` : `${(file.size / (1024 * 1024)).toFixed(1)} MB`;
    $('track-details').textContent = `${size} · ${(buffer.sampleRate / 1000).toFixed(1)} kHz · ${buffer.numberOfChannels === 1 ? '모노' : `${buffer.numberOfChannels}채널`}`;
    $('duration').textContent = formatTime(duration);
    $('wave-mid').textContent = formatTime(duration / 2);
    $('wave-end').textContent = formatTime(duration);
    $('empty-wave').hidden = true;
    canvas.setAttribute('aria-valuemax', String(duration));
    $('player-note').textContent = demo ? '샘플 멜로디 · Little moments' : '이 음원은 현재 브라우저에서만 열려 있어요';
    message(demo ? '샘플 음원이 준비됐어요. 재생 버튼을 눌러 들어보세요.' : '음원이 준비됐어요. 재생 버튼을 누르거나 파형을 클릭해보세요.');
  } catch {
    if (version === loadVersion) message('이 파일을 읽을 수 없어요. 손상되지 않은 MP3 또는 WAV 파일로 다시 시도해주세요.', true);
  } finally {
    if (decoder) await decoder.close().catch(() => {});
    if (version === loadVersion) {
      loading = false;
      setControls(); updatePlayback();
    }
  }
}

async function togglePlayback() {
  if (!peaks || loading) return;
  if (!audio.paused) { audio.pause(); return; }
  if (audio.ended || audio.currentTime >= duration) audio.currentTime = 0;
  try { await audio.play(); }
  catch { message('재생을 시작할 수 없어요. 파일 형식을 확인하고 다시 시도해주세요.', true); }
}

function seek(time) {
  if (!peaks || loading) return;
  audio.currentTime = Math.max(0, Math.min(duration, time));
  updatePosition();
}

function updateVolume() {
  const muted = audio.muted || audio.volume === 0;
  $('mute').setAttribute('aria-label', muted ? '음소거 해제' : '음소거');
  $('mute').classList.toggle('muted', muted);
  $('volume').value = audio.muted ? 0 : audio.volume;
  $('volume').style.setProperty('--volume', `${Number($('volume').value) * 100}%`);
}

$('choose-file').addEventListener('click', () => $('file-input').click());
$('file-input').addEventListener('change', (event) => {
  loadFile(event.target.files[0]);
  event.target.value = '';
});
$('load-demo').addEventListener('click', () => loadFile(createDemo(), true));
$('play').addEventListener('click', togglePlayback);
$('rewind').addEventListener('click', () => seek(audio.currentTime - 5));
$('forward').addEventListener('click', () => seek(audio.currentTime + 5));
$('volume').addEventListener('input', (event) => {
  audio.volume = Number(event.target.value);
  audio.muted = false;
  if (audio.volume > 0) previousVolume = audio.volume;
  updateVolume();
});
$('mute').addEventListener('click', () => {
  if (audio.volume === 0) { audio.volume = previousVolume; audio.muted = false; }
  else audio.muted = !audio.muted;
  updateVolume();
});
audio.addEventListener('play', updatePlayback);
audio.addEventListener('pause', updatePlayback);
audio.addEventListener('ended', updatePlayback);
audio.addEventListener('timeupdate', updatePosition);
audio.addEventListener('error', () => {
  if (objectUrl && !loading) message('브라우저가 이 음원을 재생하지 못했어요. MP3 또는 WAV로 다시 시도해주세요.', true);
});
canvas.addEventListener('pointerdown', (event) => {
  const rect = canvas.getBoundingClientRect();
  seek((event.clientX - rect.left) / rect.width * duration);
  canvas.focus();
});
canvas.addEventListener('keydown', (event) => {
  if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
    event.preventDefault();
    seek(event.key === 'Home' ? 0 : event.key === 'End' ? duration : audio.currentTime + (event.key === 'ArrowRight' ? 5 : -5));
  }
});
document.addEventListener('keydown', (event) => {
  if (event.code === 'Space' && !event.repeat && !event.target.closest('button, input, a, textarea, select, [contenteditable="true"]')) {
    event.preventDefault(); togglePlayback();
  }
});
// Prevent the browser navigating away if a file is dropped outside the upload box.
document.addEventListener('dragover', (event) => {
  if (event.dataTransfer.types.includes('Files')) event.preventDefault();
});
document.addEventListener('drop', (event) => {
  if (event.dataTransfer.types.includes('Files')) event.preventDefault();
});
let dragDepth = 0;
$('drop-zone').addEventListener('dragenter', (event) => {
  event.preventDefault(); dragDepth++; $('drop-zone').classList.add('dragging');
});
$('drop-zone').addEventListener('dragleave', () => {
  if (--dragDepth <= 0) { dragDepth = 0; $('drop-zone').classList.remove('dragging'); }
});
$('drop-zone').addEventListener('dragover', (event) => {
  event.preventDefault(); event.dataTransfer.dropEffect = 'copy';
});
$('drop-zone').addEventListener('drop', (event) => {
  event.preventDefault(); dragDepth = 0; $('drop-zone').classList.remove('dragging');
  loadFile(event.dataTransfer.files[0]);
});
new ResizeObserver(draw).observe($('waveform-container'));
window.addEventListener('pagehide', () => { audio.pause(); if (objectUrl) URL.revokeObjectURL(objectUrl); });
updateVolume();
draw();
