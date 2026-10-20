#!/usr/bin/env node
// Measures camera-to-display latency on the phone, frame by frame, for the dev variant.
//   npm run phone:latency                       the pipeline currently shown
//   npm run phone:latency -- --pipeline raw     switch first (B direct, A effect, raw), then measure
//   npm run phone:latency -- --fps 60           ask the camera for 60 frames a second first
//
// SurfaceFlinger keeps, for the last 127 frames of each layer, the time the frame wanted to be
// shown (here: its capture time, the sensor timestamp at start of exposure), and the time it
// actually reached the display. Their difference is the whole capture-to-display latency, for
// our pipelines and for the raw PreviewView alike. The dev variant logs the boot-time offset that
// converts camera timestamps (boot time) to SurfaceFlinger's clock (monotonic).
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const APP_ID = 'io.github.j3ffx.nuance.dev';
const ACTIVITY = 'io.github.j3ffx.nuance.MainActivity';
const sdk = [
  process.env.ANDROID_HOME,
  process.env.LOCALAPPDATA && join(process.env.LOCALAPPDATA, 'Android', 'Sdk'),
]
  .filter(Boolean)
  .find((dir) => existsSync(join(dir, 'platform-tools')));
const adbPath = sdk
  ? join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb')
  : 'adb';
const serial = process.env.ANDROID_SERIAL;
const adb = (...args) =>
  execFileSync(adbPath, [...(serial ? ['-s', serial] : []), ...args], {
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
  }).trim();
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const arg = (name) => {
  const at = process.argv.indexOf(name);
  return at === -1 ? undefined : process.argv[at + 1];
};

const pipeline = arg('--pipeline');
const fps = arg('--fps');
if (pipeline || fps) {
  const extras = [
    ...(pipeline ? ['--es', 'pipeline', pipeline] : []),
    ...(fps ? ['--ei', 'fps', fps] : []),
  ];
  adb('shell', 'am', 'start', '-n', `${APP_ID}/${ACTIVITY}`, ...extras.map((e) => `'${e}'`));
  await sleep(4000); // camera restart and a few seconds of steady frames
}
await sleep(5000); // fill SurfaceFlinger's 127-frame history with frames of this pipeline

const layer = adb('shell', 'dumpsys', 'SurfaceFlinger', '--list')
  .split('\n')
  .map((l) => l.trim())
  .map(
    (l) =>
      /^RequestedLayerState\{(.*SurfaceView\[[^\]]*nuance[^\]]*\].*\(BLAST\)#\d+)/.exec(l)?.[1] ??
      l,
  )
  .find((l) => l.includes(`SurfaceView[${APP_ID}`) && l.includes('(BLAST)'));
if (!layer) {
  console.error('✖ no camera SurfaceView layer: is Nuance dev on screen, with the camera allowed?');
  process.exit(1);
}

const offsetLine = adb('logcat', '-d', '-s', 'NuanceStats:I')
  .split('\n')
  .reverse()
  .find((l) => l.includes('bootMinusMonotonicNs='));
const bootOffset = offsetLine ? BigInt(/bootMinusMonotonicNs=(\d+)/.exec(offsetLine)[1]) : 0n;
const stats = offsetLine ? /RenderStats\((.*)\) boot/.exec(offsetLine)?.[1] : undefined;

const [period, ...rows] = adb('shell', `dumpsys SurfaceFlinger --latency '${layer}'`).split('\n');
const latencies = [];
const readyToDisplay = [];
for (const row of rows) {
  const [desired, actual, ready] = row
    .trim()
    .split(/\s+/)
    .map((v) => BigInt(v));
  if (!desired || !actual || actual === 0n || actual >= 2n ** 62n) continue; // pending or dropped
  // From the buffer being ready (GPU done, or camera done for the raw preview) to the display:
  // the compositor's share, comparable across pipelines.
  if (ready && ready < actual) readyToDisplay.push(Number(actual - ready) / 1e6);
  // Boot-time timestamps are ahead of the monotonic clock by the time spent asleep: a capture
  // "after" its display can only be one of those.
  const capture = desired > actual ? desired - bootOffset : desired;
  const ms = Number(actual - capture) / 1e6;
  if (ms > 0 && ms < 1000) latencies.push(ms);
}
readyToDisplay.sort((a, b) => a - b);
if (readyToDisplay.length)
  console.log(
    `ready → display: median ${readyToDisplay[readyToDisplay.length >> 1].toFixed(1)} ms`,
  );
if (latencies.length < 10) {
  console.error(`✖ only ${latencies.length} usable frames on ${layer}`);
  process.exit(1);
}
latencies.sort((a, b) => a - b);
const pct = (p) =>
  latencies[Math.min(latencies.length - 1, Math.floor((p / 100) * latencies.length))];
console.log(`layer: ${layer}`);
if (stats) console.log(`app:   ${stats}`);
console.log(`display refresh: ${(Number(period) / 1e6).toFixed(2)} ms`);
console.log(
  `capture → display over ${latencies.length} frames: ` +
    `median ${pct(50).toFixed(1)} ms, p10 ${pct(10).toFixed(1)}, p90 ${pct(90).toFixed(1)}, ` +
    `min ${latencies[0].toFixed(1)}, max ${latencies.at(-1).toFixed(1)}`,
);
