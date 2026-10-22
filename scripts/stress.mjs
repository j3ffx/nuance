#!/usr/bin/env node
// Switches the dev variant between image sources, modes and views quickly, then fails if the app
// crashed: a regression test for render-thread lifecycle bugs (a camera frame arriving after its
// GL thread was torn down), including the activity being recreated by a change of the app's own
// language. It never shows the camera on screen for long and takes no screenshot; the app's language
// is set back to the phone's at the end.
//   npm run phone:stress            20 rounds
//   npm run phone:stress -- 50      more rounds
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const APP = 'io.github.j3ffx.nuance.dev';
const ACTIVITY = `${APP}/io.github.j3ffx.nuance.MainActivity`;
const sdk = [
  process.env.ANDROID_HOME,
  process.env.LOCALAPPDATA && join(process.env.LOCALAPPDATA, 'Android', 'Sdk'),
]
  .filter(Boolean)
  .find((dir) => existsSync(join(dir, 'platform-tools')));
const adbPath = sdk
  ? join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb')
  : 'adb';
const adb = (...args) => execFileSync(adbPath, args, { encoding: 'utf8' }).trim();

// The same phone as `npm run phone` (local.properties → nuance.phone), else the first one: adb
// refuses to choose when a phone shows up twice (by ip:port and by its mDNS name).
if (!process.env.ANDROID_SERIAL) {
  const line = existsSync('local.properties')
    ? readFileSync('local.properties', 'utf8')
        .split(/\r?\n/)
        .find((l) => l.startsWith('nuance.phone='))
    : undefined;
  const preferred = line?.slice('nuance.phone='.length).trim();
  const devices = adb('devices')
    .split('\n')
    .slice(1)
    .map((l) => l.trim().split(/\s+/))
    .filter(([, state]) => state === 'device')
    .map(([serial]) => serial);
  const phone = devices.find((s) => preferred && s.includes(preferred)) ?? devices[0];
  if (phone) process.env.ANDROID_SERIAL = phone;
}
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const start = (...extras) => adb('shell', 'am', 'start', '-n', ACTIVITY, ...extras);

const rounds = Number(process.argv[2] ?? 20);
const sources = ['camera', "'stripes D'", 'chart', 'camera', 'moving', 'hues'];
const modes = ['correct', 'compare', 'isolate', 'simulate'];

adb('logcat', '-c');
const pid = adb('shell', 'pidof', APP);
for (let i = 0; i < rounds; i++) {
  start(
    '--es',
    'source',
    sources[i % sources.length],
    '--es',
    'mode',
    modes[i % modes.length],
    '--ei',
    'views',
    i % 2 ? '2' : '4',
  );
  await sleep(150 + ((i * 97) % 400));
  // Every fourth round, recreate the activity while the camera runs, as a language change does.
  if (i % 4 === 3)
    adb(
      'shell',
      'cmd',
      'locale',
      'set-app-locales',
      APP,
      '--locales',
      i % 8 === 3 ? 'fr-FR' : 'en-GB',
    );
}
adb('shell', 'cmd', 'locale', 'set-app-locales', APP, '--locales', '""');
await sleep(1500);
start('--es', 'source', "'stripes D'", '--es', 'mode', 'correct'); // leave a synthetic image on screen
const crashes = adb('logcat', '-d', '-s', 'AndroidRuntime:E')
  .split('\n')
  .filter((l) => l.includes('FATAL EXCEPTION'));
const now = adb('shell', 'pidof', APP);
if (crashes.length || now !== pid) {
  console.error(`✖ crashed (${crashes.length} fatal exception(s); pid ${pid} → ${now || 'none'})`);
  console.error(adb('logcat', '-d', '-s', 'AndroidRuntime:E').split('\n').slice(0, 12).join('\n'));
  process.exit(1);
}
console.log(`✔ ${rounds} rounds of source, mode and view switches, no crash`);
