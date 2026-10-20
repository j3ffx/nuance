#!/usr/bin/env node
// Runs the Gradle wrapper with the given tasks, on Windows and elsewhere alike.
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

const gradlew = join(process.cwd(), process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const run = spawnSync(gradlew, [...process.argv.slice(2), '--console=plain'], {
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
process.exit(run.status ?? 1);
