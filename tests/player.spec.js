import { test, expect } from '@playwright/test';
import { createDemo } from '../src/audio.js';

async function sampleFile(name = 'my recording.wav') {
  return { name, mimeType: 'audio/wav', buffer: Buffer.from(await createDemo().arrayBuffer()) };
}

test('initial state is usable and demo really plays, seeks, pauses, and replays', async ({ page }) => {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('button', { name: '재생', exact: true })).toBeDisabled();
  await expect(page.getByRole('slider', { name: '파형 재생 위치' })).toHaveAttribute('aria-disabled', 'true');
  await page.getByRole('button', { name: '샘플 음원 불러오기' }).click();
  await expect(page.locator('#track-name')).toHaveText('Little moments.wav');
  await expect(page.locator('#duration')).toHaveText('0:10');
  await page.getByRole('button', { name: '재생', exact: true }).click();
  await expect(page.getByRole('button', { name: '일시정지' })).toBeEnabled();
  await expect.poll(() => page.locator('#audio').evaluate((el) => el.currentTime)).toBeGreaterThan(0.1);
  await page.getByRole('button', { name: '일시정지' }).click();
  const paused = await page.locator('#audio').evaluate((el) => el.currentTime);
  await expect(page.locator('#audio')).toHaveJSProperty('paused', true);
  await page.waitForTimeout(120);
  expect(await page.locator('#audio').evaluate((el) => el.currentTime)).toBe(paused);
  const wave = page.getByRole('slider', { name: '파형 재생 위치' });
  const box = await wave.boundingBox();
  await wave.click({ position: { x: box.width / 2, y: box.height / 2 } });
  await expect.poll(() => page.locator('#audio').evaluate((el) => el.currentTime)).toBeCloseTo(5, 0);
  await wave.press('Home');
  await expect(page.locator('#current-time')).toHaveText('0:00');
  await wave.press('ArrowRight');
  await expect(page.locator('#current-time')).toHaveText('0:05');
  await wave.press('End');
  await expect(page.locator('#current-time')).toHaveText('0:10');
  await page.getByRole('button', { name: '재생', exact: true }).click();
  await expect.poll(() => page.locator('#audio').evaluate((el) => el.currentTime)).toBeLessThan(2);
  expect(errors).toEqual([]);
});

test('file upload generates a waveform and can be replaced, including the same file', async ({ page }) => {
  await page.goto('/');
  const file = await sampleFile('한글 음원.wav');
  await page.locator('#file-input').setInputFiles(file);
  await expect(page.locator('#track-name')).toHaveText(file.name);
  await expect(page.locator('#track-details')).toContainText('모노');
  await expect(page.locator('#empty-wave')).toBeHidden();
  const hasWave = await page.locator('#waveform').evaluate((el) => {
    const pixels = el.getContext('2d').getImageData(0, 0, el.width, el.height).data;
    let darkPixels = 0;
    for (let i = 0; i < pixels.length; i += 4) if (pixels[i] === 65 && pixels[i + 1] === 73 && pixels[i + 2] === 64) darkPixels++;
    return darkPixels > 500;
  });
  expect(hasWave).toBe(true);
  await page.getByRole('button', { name: '재생', exact: true }).click();
  await expect(page.locator('#audio')).toHaveJSProperty('paused', false);
  await page.locator('#file-input').setInputFiles(file);
  await expect(page.locator('#state-badge')).toHaveText('재생 준비');
  await expect(page.locator('#audio')).toHaveJSProperty('paused', true);
  await expect(page.locator('#current-time')).toHaveText('0:00');
  await page.locator('#file-input').setInputFiles(await sampleFile('second.wav'));
  await expect(page.locator('#track-name')).toHaveText('second.wav');
});

test('invalid, empty, and corrupt files show errors and preserve the usable track', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '샘플 음원 불러오기' }).click();
  await expect(page.locator('#track-name')).toHaveText('Little moments.wav');
  await page.locator('#file-input').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') });
  await expect(page.getByRole('status')).toContainText('오디오 파일을 선택');
  await page.locator('#file-input').setInputFiles({ name: 'empty.wav', mimeType: 'audio/wav', buffer: Buffer.alloc(0) });
  await expect(page.getByRole('status')).toContainText('빈 파일');
  await page.locator('#file-input').setInputFiles({ name: 'corrupt.wav', mimeType: 'audio/wav', buffer: Buffer.from('not an audio file') });
  await expect(page.getByRole('status')).toContainText('이 파일을 읽을 수 없어요');
  await expect(page.locator('#track-name')).toHaveText('Little moments.wav');
  await page.getByRole('button', { name: '재생', exact: true }).click();
  await expect.poll(() => page.locator('#audio').evaluate((el) => el.currentTime)).toBeGreaterThan(0);
});

test('drag and drop loads a real audio file', async ({ page }) => {
  await page.goto('/');
  const file = await sampleFile('dropped.wav');
  const transfer = await page.evaluateHandle(({ bytes, name }) => {
    const data = new DataTransfer();
    data.items.add(new File([new Uint8Array(bytes)], name, { type: 'audio/wav' }));
    return data;
  }, { bytes: Array.from(file.buffer), name: file.name });
  await page.locator('#drop-zone').dispatchEvent('drop', { dataTransfer: transfer });
  await expect(page.locator('#track-name')).toHaveText(file.name);
  await expect(page.getByRole('button', { name: '재생', exact: true })).toBeEnabled();
});

test('volume, mute, skip controls, and spacebar work', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '샘플 음원 불러오기' }).click();
  await expect(page.locator('#state-badge')).toHaveText('재생 준비');
  const volume = page.getByRole('slider', { name: '음량', exact: true });
  await volume.fill('0.3');
  await expect(page.locator('#audio')).toHaveJSProperty('volume', 0.3);
  await page.getByRole('button', { name: '음소거', exact: true }).click();
  await expect(page.locator('#audio')).toHaveJSProperty('muted', true);
  await page.getByRole('button', { name: '음소거 해제' }).click();
  await expect(page.locator('#audio')).toHaveJSProperty('muted', false);
  await expect(volume).toHaveValue('0.3');
  await volume.fill('0');
  await page.getByRole('button', { name: '음소거 해제' }).click();
  await expect(volume).toHaveValue('0.3');
  await page.getByRole('button', { name: '5초 앞으로' }).click();
  await expect(page.locator('#current-time')).toHaveText('0:05');
  await page.getByRole('button', { name: '5초 뒤로' }).click();
  await expect(page.locator('#current-time')).toHaveText('0:00');
  await page.getByRole('slider', { name: '파형 재생 위치' }).focus();
  await page.keyboard.press('Space');
  await expect(page.locator('#audio')).toHaveJSProperty('paused', false);
  await page.keyboard.press('Space');
  await expect(page.locator('#audio')).toHaveJSProperty('paused', true);
});

test('mobile layout fits and the player remains operable', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/');
  await page.getByRole('button', { name: '샘플 음원 불러오기' }).click();
  await expect(page.locator('#state-badge')).toHaveText('재생 준비');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: '재생', exact: true }).click();
  await expect(page.getByRole('button', { name: '일시정지' })).toBeVisible();
});
