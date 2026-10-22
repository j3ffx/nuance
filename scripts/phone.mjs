#!/usr/bin/env node
// Builds the dev variant, installs it on the phone over wireless ADB and launches it.
//   npm run phone            build + install + launch
//   npm run phone -- --find  only (re)connect to the phone and print it
//   npm run phone -- --logs  then follow the app's log (Ctrl+C to stop)
//   npm run phone -- --connect 192.0.2.10:37123   connect to that address first (the one shown
//                            under Débogage sans fil, when mDNS advertises a stale one)
//
// The phone is found by mDNS (`adb mdns services`), so the port wireless debugging picks after a
// reboot doesn't matter. It must have been paired once (docs/SETUP.md). With several phones
// paired, `nuance.phone=<its serial number>` in local.properties (ignored by git) picks the test
// phone; otherwise the first one found is used.
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const APP_ID = 'io.github.j3ffx.nuance.dev';
const ACTIVITY = 'io.github.j3ffx.nuance.MainActivity';

/** A key of local.properties, or undefined. */
function localProperty(key) {
  if (!existsSync('local.properties')) return undefined;
  const line = readFileSync('local.properties', 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith(`${key}=`));
  return line?.slice(key.length + 1).trim();
}

/** The Android SDK: ANDROID_HOME, else local.properties, else the default install folder. */
function sdkDir() {
  // local.properties escapes ':' and '\' (C\:\\Android\\Sdk).
  const fromProperties = localProperty('sdk.dir')?.replace(/\\([:\\])/g, '$1');
  const candidates = [process.env.ANDROID_HOME, fromProperties];
  if (process.env.LOCALAPPDATA) candidates.push(join(process.env.LOCALAPPDATA, 'Android', 'Sdk'));
  if (process.env.HOME) candidates.push(join(process.env.HOME, 'Library', 'Android', 'sdk'));
  return candidates.find((dir) => dir && existsSync(join(dir, 'platform-tools')));
}

const sdk = sdkDir();
const preferred = localProperty('nuance.phone');
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

/** The device's own serial number: an ip:port serial doesn't carry it. */
function serialNumber(serial) {
  try {
    return adb('-s', serial, 'shell', 'getprop', 'ro.serialno');
  } catch {
    return '';
  }
}

/** The preferred phone among the connected ones (local.properties → nuance.phone), else the first. */
function pick(devices) {
  if (!preferred) return devices[0];
  return devices.find((s) => s.includes(preferred) || serialNumber(s) === preferred);
}

/** Connects to the paired phone advertised over mDNS, if adb isn't already. */
function findPhone() {
  const at = process.argv.indexOf('--connect');
  if (at !== -1 && process.argv[at + 1]) console.log(adb('connect', process.argv[at + 1]));
  let phone = pick(connected());
  if (phone) return phone;
  const services = adb('mdns', 'services')
    .split('\n')
    .filter((l) => l.includes('_adb-tls-connect._tcp'))
    .filter((l) => !preferred || l.includes(preferred))
    .map((l) => l.trim().split(/\s+/).at(-1));
  for (const address of services) {
    // A refused address (stale mDNS record) is skipped: the next one may work.
    if (/connected to/.test(adb('connect', address))) break;
  }
  phone = pick(connected());
  if (phone) return phone;
  console.error(
    '✖ Phone not found. Check that it is on the same Wi-Fi and that wireless debugging is on\n' +
      '  (Options de développement → Débogage sans fil). If mDNS gives a stale address, pass the\n' +
      '  one shown on that screen: npm run phone -- --connect <ip:port>. Never paired: docs/SETUP.md.',
  );
  process.exit(1);
}

const started = Date.now();
const serial = findPhone();
console.log(`phone: ${serial}`);
if (process.argv.includes('--find')) process.exit(0);

const gradlew = join(process.cwd(), process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const build = spawnSync(gradlew, [':androidApp:assembleDebug', '--console=plain', '-q'], {
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: { ...process.env, ...(sdk && { ANDROID_HOME: sdk }) },
});
if (build.status !== 0) process.exit(build.status ?? 1);
// For the phone's current user only: installDebug installs for every user, so a Samsung with
// "Dual Messenger" got a cloned copy of the app in its second profile.
const apk = join('androidApp', 'build', 'outputs', 'apk', 'debug', 'androidApp-debug.apk');
adb('-s', serial, 'install', '-r', '--user', 'current', apk);

adb('-s', serial, 'shell', 'am', 'start', '-S', '-n', `${APP_ID}/${ACTIVITY}`);
console.log(`✔ installed and launched in ${((Date.now() - started) / 1000).toFixed(1)} s`);

if (process.argv.includes('--logs')) {
  const pid = adb('-s', serial, 'shell', 'pidof', APP_ID);
  spawnSync(adbPath, ['-s', serial, 'logcat', `--pid=${pid}`], { stdio: 'inherit' });
}
