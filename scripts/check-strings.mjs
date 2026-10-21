#!/usr/bin/env node
// Checks that every UI string exists in both languages (FR and EN) and that their placeholders
// (%1$s, %2$d…) match, so no screen falls back to the other language or crashes on formatting.
import { readFileSync } from 'node:fs';

const dir = 'shared/src/commonMain/composeResources';
const files = { en: `${dir}/values/strings.xml`, fr: `${dir}/values-fr/strings.xml` };

/** name → list of placeholders, for one strings.xml. */
function read(file) {
  const out = new Map();
  for (const m of readFileSync(file, 'utf8').matchAll(
    /<string name="([^"]+)">([\s\S]*?)<\/string>/g,
  )) {
    out.set(m[1], [...m[2].matchAll(/%\d+\$[sd]/g)].map((p) => p[0]).sort());
  }
  return out;
}

const en = read(files.en);
const fr = read(files.fr);
const problems = [];
for (const [name, args] of en) {
  if (!fr.has(name)) problems.push(`missing in French: ${name}`);
  else if (fr.get(name).join() !== args.join()) problems.push(`placeholders differ for ${name}`);
}
for (const name of fr.keys()) if (!en.has(name)) problems.push(`missing in English: ${name}`);

if (problems.length) {
  console.error('check-strings: FAILED');
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log(`check-strings: OK (${en.size} strings in FR and EN)`);
