// Mints fresh single-use signup keys directly into the dev MySQL container (via `docker compose
// exec`, so no extra DB-client dependency is needed) and appends the plaintext to a file OUTSIDE
// this repo — signup_keys only ever stores the SHA-256 hash (AuthService checks it the same way
// MachineKeyHasher.hash() does for machine keys), so the plaintext must never be committed.
//
// Usage: node scripts/generate-signup-keys.mjs [count]
// Requires `docker compose up -d mysql` already running (same container the backend itself uses).
//
// This is also how the 20 keys seeded by 015-create-signup-keys.xml were produced: the printed
// "hash" values below were pasted into that migration's <insert> rows; the plaintext for those 20
// went to the same outside-repo file as everything this script ever generates.

import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { appendFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const count = Number(process.argv[2] ?? 20);
const REPO_ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
// Sibling of the repo directory, not inside it — outside git's reach entirely.
const OUTPUT_FILE = path.join(path.dirname(REPO_ROOT), 'uwtracker-signup-keys.txt');

function rawKey() {
  return randomBytes(32).toString('base64url');
}

function sha256Hex(value) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

const keys = Array.from({ length: count }, () => rawKey());
const hashes = keys.map(sha256Hex);

const values = hashes.map((h) => `('${h}')`).join(', ');
execFileSync('docker', ['compose', 'exec', '-T', 'mysql', 'mysql', '-uroot', '-proot', 'uwtracker', '-e',
    `INSERT INTO signup_keys (key_hash) VALUES ${values};`], { cwd: REPO_ROOT, stdio: ['ignore', 'ignore', 'ignore'] });

const stamp = new Date().toISOString();
appendFileSync(OUTPUT_FILE, `\n# ${stamp} — ${count} keys\n${keys.join('\n')}\n`);

console.log(`Inserted ${count} signup keys. Plaintext appended to ${OUTPUT_FILE}`);
console.log('Hashes (for reference / migration seeding):');
hashes.forEach((h) => console.log(h));
