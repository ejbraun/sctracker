import type { Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Backend usernames are globally unique and this app has no test-DB reset between e2e runs. */
export function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

// Two levels up from frontend/e2e/ — where docker-compose.yml lives.
const REPO_ROOT = path.dirname(path.dirname(path.dirname(fileURLToPath(import.meta.url))));

/**
 * Signup is invite-gated (see AuthService.signup / scripts/generate-signup-keys.mjs) — a real
 * deployment hands out a finite pool of single-use keys, which e2e shouldn't burn through. Mints
 * a throwaway key directly in the dev MySQL container instead, the same way
 * AbstractIntegrationTest.freshSignupKey() does for the backend suite.
 */
function mintSignupKey(): string {
  const rawKey = randomBytes(32).toString('base64url');
  const hash = createHash('sha256').update(rawKey, 'utf8').digest('hex');
  execFileSync(
    'docker',
    ['compose', 'exec', '-T', 'mysql', 'mysql', '-uroot', '-proot', 'uwtracker', '-e', `INSERT INTO signup_keys (key_hash) VALUES ('${hash}');`],
    { cwd: REPO_ROOT, stdio: ['ignore', 'ignore', 'ignore'] },
  );
  return rawKey;
}

export async function signUp(page: Page, username: string, password = 'password123'): Promise<void> {
  const signupKey = mintSignupKey();
  await page.goto('/signup');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByLabel('Confirm password').fill(password);
  await page.getByLabel('Signup key').fill(signupKey);
  await page.getByRole('button', { name: 'Sign up' }).click();
  await page.waitForURL('/');
}

/**
 * `getByRole('link', { name: 'Account' })` alone is ambiguous whenever UpdateBanner is showing
 * (every fresh e2e signup has never downloaded the plugin, so it always is) — "Download it from
 * your Account page" matches by substring too, since Playwright's role-name matching isn't exact
 * by default.
 */
export async function goToAccount(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Account', exact: true }).click();
}

// The only currently-supported map (specs/backend/01 — maps is a curated, well-defined set seeded
// by migration; /upload-run rejects anything else).
export const UNDERWORLD_MAP_ID = 72;
export const UNDERWORLD_MAP_NAME = 'Underworld';

// Mirrors specs/backend/02-ingestion-upload-run.md's profession ids and RoleDerivation's T1 combo.
const RANGER = 2;
const ASSASSIN = 7;
const MONK = 3;
const MESMER = 5;
const ELEMENTALIST = 6;
const RITUALIST = 8;
const WARRIOR = 1;
const DERVISH = 10;

const BACKEND_ORIGIN = 'http://localhost:8080';

// The backend now reads the required version from the plugin manifest in its GCS bucket
// (PluginArtifactCache). `make backend-up` runs with no PLUGIN_STORAGE_BUCKET, so
// requireCurrentVersion fails open and this value is currently inert — but it WILL start 426-ing
// every e2e upload if a bucket is ever wired to the e2e backend. Keep it at the real current
// version (the plugin's kPluginVersion) so that day isn't a surprise.
const CURRENT_PLUGIN_VERSION = '10';

// The 7 non-hero slots below — fixed names, shared/reused across every e2e run (no DB reset between
// runs; see uniqueName's note). uploadRun registers these as characters before uploading so the
// party clears UploadRunService's "at least 4 registered characters" minimum without registering
// heroName itself, which several tests (run-flow.spec.ts) deliberately upload unregistered first,
// to exercise retroactive character backfill.
const FIXED_PARTY_MEMBER_NAMES = ['T2', 'T3', 'T4', 'LT', 'Spiker', 'SoS', 'Emo'];

/** A valid 8-slot /upload-run payload — party_index 0 (heroName) resolves to role T1. */
export function buildUploadPayload(heroName: string, utcStartSeconds: number) {
  return {
    party: {
      utc_start: utcStartSeconds,
      map_id: UNDERWORLD_MAP_ID,
      character_name: heroName,
      end_reason: 'victory',
      party_members: [
        // role_hint required for RoleDerivation to resolve T1 — there's no positional fallback for
        // Ranger/Assassin members (see RoleDerivation's class doc), and it only trusts the hint on
        // whichever member matches party.character_name (heroName here) from any single upload.
        {
          name: heroName,
          primary: RANGER,
          secondary: ASSASSIN,
          is_player: true,
          is_hero: false,
          is_henchman: false,
          role_hint: 't1',
        },
        { name: 'T2', primary: RANGER, secondary: ASSASSIN, is_player: true, is_hero: false, is_henchman: false },
        { name: 'T3', primary: RANGER, secondary: ASSASSIN, is_player: true, is_hero: false, is_henchman: false },
        { name: 'T4', primary: ELEMENTALIST, secondary: MESMER, is_player: true, is_hero: false, is_henchman: false },
        { name: 'LT', primary: MESMER, secondary: ASSASSIN, is_player: true, is_hero: false, is_henchman: false },
        { name: 'Spiker', primary: DERVISH, secondary: WARRIOR, is_player: true, is_hero: false, is_henchman: false },
        { name: 'SoS', primary: RITUALIST, secondary: RANGER, is_player: true, is_hero: false, is_henchman: false },
        { name: 'Emo', primary: ELEMENTALIST, secondary: MONK, is_player: true, is_hero: false, is_henchman: false },
      ],
    },
    objective: {
      name: UNDERWORLD_MAP_NAME,
      instance_start: 555_000,
      utc_start: utcStartSeconds + 2,
      objectives: [
        { name: 'Vale', status: 2, start: 1000, done: 5000, duration: 4000, indent: 0 },
        { name: 'Final Trial', status: 2, start: 9000, done: 15000, duration: 6000, indent: 0 },
      ],
      duration: 15_000,
    },
  };
}

/**
 * Registers FIXED_PARTY_MEMBER_NAMES as characters via the page's own (already-authenticated)
 * session — idempotent across the whole suite: a 409 just means an earlier test run already
 * registered that name (see FIXED_PARTY_MEMBER_NAMES' note), anything else is a real failure.
 */
async function ensureFixedPartyCharactersRegistered(page: Page): Promise<void> {
  for (const name of FIXED_PARTY_MEMBER_NAMES) {
    const response = await page.request.post('/api/characters', { data: { character_name: name } });
    if (!response.ok() && response.status() !== 409) {
      throw new Error(`registering character "${name}" failed: ${response.status()} ${await response.text()}`);
    }
  }
}

/**
 * Uploads as the SDK plugin would: machine-key header, no session cookie, straight to the backend
 * (the frontend dev server only proxies /api, not the unprefixed /upload-run endpoint) — the
 * frontend never calls this itself, only the plugin does, so there's no api client wrapper for it.
 * Asserts success itself (rather than returning the raw response) since every caller wants the same
 * thing: the parsed body, or a loud failure with the response text if the upload was rejected.
 */
export async function uploadRun(
  page: Page,
  rawKey: string,
  heroName: string,
  utcStartSeconds: number,
): Promise<{ run_id: number; created: boolean }> {
  await ensureFixedPartyCharactersRegistered(page);
  const response = await page.request.post(`${BACKEND_ORIGIN}/upload-run`, {
    headers: { 'X-Machine-Key': rawKey, 'X-Plugin-Version': CURRENT_PLUGIN_VERSION, 'Content-Type': 'application/json' },
    data: buildUploadPayload(heroName, utcStartSeconds),
  });
  if (!response.ok()) {
    throw new Error(`/upload-run failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

/**
 * Directly inserts a run_mvp_awards row (bypassing the real 60s vote-and-tally window entirely —
 * same "seed it directly in MySQL" approach mintSignupKey uses above) crediting rawName's
 * participant, or null for a "Nobody" award. Looks the participant up by (runId, rawName) since
 * that's all callers have on hand — see RunHistoryService/MvpPersister on the backend for the shape
 * this mirrors.
 */
export function seedMvpAward(runId: number, rawName: string | null): void {
  const participantIdExpr =
    rawName === null
      ? 'NULL'
      : `(SELECT id FROM run_participants WHERE run_id = ${runId} AND raw_name = '${rawName}')`;
  execFileSync(
    'docker',
    [
      'compose',
      'exec',
      '-T',
      'mysql',
      'mysql',
      '-uroot',
      '-proot',
      'uwtracker',
      '-e',
      `INSERT INTO run_mvp_awards (run_id, run_participant_id) VALUES (${runId}, ${participantIdExpr});`,
    ],
    { cwd: REPO_ROOT, stdio: ['ignore', 'ignore', 'ignore'] },
  );
}
