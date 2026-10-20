#!/usr/bin/env node
// Builds the dev variant, installs it on the phone over wireless ADB and launches it.
//   npm run phone            build + install + launch
//   npm run phone -- --find  only (re)connect to the phone and print it
//   npm run phone -- --logs  then follow the app's log (Ctrl+C to stop)
//
// The phone is found by mDNS (`adb mdns services`), so the port wireless debugging
// picks after a reboot doesn't matter. It must have been paired once (docs/SETUP.md).
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const APP_ID = 'io.github.j3ffx.nuance.dev';
const ACTIVITY = 'io.github.j3ffx.nuance.MainActivity';

/** The Android SDK: ANDROID_HOME, else local.properties, else the default install folder. */
function sdkDir() {
  const candidates = [process.env.ANDROID_HOME];
  if (existsSync('local.properties')) {
    const line = readFileSync('local.properties', 'utf8')
      .split(/\r?\n/)
      .find((l) => l.startsWith('sdk.dir='));
    if (line)
      candidates.push(line.slice('sdk.dir='.length).replace(/\\:/g, ':').replace(/\\\\/g, '\\'));
  }
  if (process.env.LOCALAPPDATA) candidates.push(join(process.env.LOCALAPPDATA, 'Android', 'Sdk'));
  if (process.env.HOME) candidates.push(join(process.env.HOME, 'Library', 'Android', 'sdk'));
  return candidates.find((dir) => dir && existsSync(join(dir, 'platform-tools')));
}

const sdk = sdkDir();
const exe = process.platform === 'win32' ? 'adb.exe' : 'adb';
const adbPath = sdk ? join(sdk, 'platform-tools', exe) : 'adb';

const adb = (...args) => execFileSync(adbPath, args, { encoding: 'utf8' }).trim();

/** Serials of the devices adb is connected to and allowed to use. */
function connected() {
  return adb('devices')
    .split('\n')
    .slice(1)
    .map((l) => l.trim().split(/\s+/))
    .filter(([, state]) => state === 'device')
    .map(([serial]) => serial);
}

/** Connects to the paired phone advertised over mDNS, if adb isn't already. */
function findPhone() {
  let devices = connected();
  if (devices.length) return devices[0];
  const services = adb('mdns', 'services')
    .split('\n')
    .filter((l) => l.includes('_adb-tls-connect._tcp'))
    .map((l) => l.trim().split(/\s+/).at(-1));
  for (const address of services) {
    const out = adb('connect', address);
    if (/connected to/.test(out)) break;
  }
  devices = connected();
  if (devices.length) return devices[0];
  console.error(
    '✖ Phone not found. Check that it is on the same Wi-Fi and that wireless debugging is on\n' +
      '  (Options de développement → Débogage sans fil). If it was never paired: docs/SETUP.md.',
  );
  process.exit(1);
}

const started = Date.now();
const serial = findPhone();
console.log(`phone: ${serial}`);
if (process.argv.includes('--find')) process.exit(0);

const gradlew = join(process.cwd(), process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const build = spawnSync(gradlew, [':androidApp:installDebug', '--console=plain', '-q'], {
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: { ...process.env, ANDROID_SERIAL: serial, ...(sdk && { ANDROID_HOME: sdk }) },
});
if (build.status !== 0) process.exit(build.status ?? 1);

adb('-s', serial, 'shell', 'am', 'start', '-S', '-n', `${APP_ID}/${ACTIVITY}`);
console.log(`✔ installed and launched in ${((Date.now() - started) / 1000).toFixed(1)} s`);

if (process.argv.includes('--logs')) {
  const pid = adb('-s', serial, 'shell', 'pidof', APP_ID);
  spawnSync(adbPath, ['-s', serial, 'logcat', `--pid=${pid}`], { stdio: 'inherit' });
}
