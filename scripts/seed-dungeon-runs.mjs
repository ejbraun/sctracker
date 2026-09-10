// Seeds a local dev backend with a few Eye of the North dungeon runs (8-man, role-less), via the
// real POST /upload-run / signup / characters endpoints. The dungeon analogue of seed-fow-runs.mjs.
// A multi-level dungeon arrives as one payload under the entry-level map id with "Level 1".."Level N"
// objectives (GWToolboxdll flattens the levels into one ObjectiveSet).
//
// Usage: node scripts/seed-dungeon-runs.mjs
// Requires the backend running against a real MySQL with changeset 055 applied (make db-up +
// mvn spring-boot:run). Point BACKEND_URL elsewhere if not on localhost:8080. Not idempotent —
// wipe first for a clean reseed (see seed-uw-runs.mjs's note).

import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';
const SENTINEL = 4294967295;
// Must be >= the backend's static/SCTracker.version.json "version" or /upload-run returns 426.
const PLUGIN_VERSION = process.env.PLUGIN_VERSION ?? '16';
const END_REASONS = ['completed', 'wipe', 'resign', 'unknown'];

// Profession ids (specs/backend/02) — dungeons are role-less, so the mix is cosmetic.
const WARRIOR = 1, RANGER = 2, MONK = 3, NECRO = 4, MESMER = 5, ELE = 6, RITUALIST = 8, DERVISH = 10;
const PROFS = [WARRIOR, RANGER, MONK, NECRO, MESMER, ELE, RITUALIST, DERVISH];

// A few dungeons across the level-count range, with their entry map id and level count
// (055-seed-dungeons.xml). Objectives are "Level 1".."Level N".
const DUNGEONS = [
  { mapId: 704, name: "Fronis Irontoe's Lair", levels: 1 },
  { mapId: 560, name: 'Cathedral of Flames', levels: 3 },
  { mapId: 570, name: 'Catacombs of Kathandrax', levels: 3 },
  { mapId: 630, name: "Frostmaw's Burrows", levels: 5 },
];

function mintSignupKey() {
  const rawKey = randomBytes(32).toString('base64url');
  const hash = createHash('sha256').update(rawKey, 'utf8').digest('hex');
  execFileSync('docker', ['compose', 'exec', '-T', 'mysql', 'mysql', '-uroot', '-proot', 'uwtracker', '-e',
      `INSERT INTO signup_keys (key_hash) VALUES ('${hash}');`], { stdio: ['ignore', 'ignore', 'ignore'] });
  return rawKey;
}

async function api(path, { method = 'GET', body, cookie } = {}) {
  const res = await fetch(`${BACKEND}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const setCookie = res.headers.get('set-cookie');
  const text = await res.text();
  const json = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    throw new Error(`${method} ${path} -> ${res.status}: ${text}`);
  }
  return { json, cookie: setCookie ? setCookie.split(';')[0] : cookie };
}

// --- Eight guild members, one character each ------------------------------------------------

const ALIASES = ['DgnAsh', 'DgnBex', 'DgnCleo', 'DgnDax', 'DgnEve', 'DgnFin', 'DgnGwen', 'DgnHild'];
const personas = [];
for (const [i, alias] of ALIASES.entries()) {
  const username = alias.toLowerCase() + 'player';
  const signup = await api('/api/signup', {
    method: 'POST',
    body: { username, password: 'seedpassword123', signup_key: mintSignupKey() },
  });
  const cookie = signup.cookie;
  await api('/api/account/alias', { method: 'PATCH', body: { alias }, cookie });
  const key = await api('/api/account/machine-keys', {
    method: 'POST', body: { label: `${alias}'s GWToolbox` }, cookie,
  });
  const character = `${alias} Delver`;
  await api('/api/characters', { method: 'POST', body: { character_name: character }, cookie });
  personas.push({ alias, machineKey: key.json.key, character, primary: PROFS[i], secondary: PROFS[(i + 3) % PROFS.length] });
  console.log(`persona ${i + 1}/${ALIASES.length}: ${alias} — ${character}`);
}

// --- A handful of fuzzed dungeon runs -----------------------------------------------------

function objectives(levels, reachedCount, lastStatus, baseDurationMs) {
  const perLevel = Math.round(baseDurationMs / levels);
  let clock = 0;
  const list = [];
  for (let i = 0; i < levels; i++) {
    const name = `Level ${i + 1}`;
    if (i >= reachedCount) {
      list.push({ name, status: 0, start: SENTINEL, done: SENTINEL, duration: SENTINEL, indent: 0 });
      continue;
    }
    const status = i === reachedCount - 1 ? lastStatus : 2;
    const start = clock;
    const dur = Math.round(perLevel * (0.8 + Math.random() * 0.4));
    clock += dur;
    list.push({
      name, status, start,
      done: status === 2 ? clock : SENTINEL,
      duration: status === 2 ? dur : SENTINEL,
      indent: 0,
    });
  }
  return { list, elapsedMs: clock };
}

function buildRun({ dungeon, uploader, reachedCount, lastStatus, baseDurationMs, daysAgo, endReason }) {
  const utcStartSeconds = Math.floor(Date.now() / 1000) - daysAgo * 86400 - Math.floor(Math.random() * 3600);
  const { list: objs, elapsedMs } = objectives(dungeon.levels, reachedCount, lastStatus, baseDurationMs);
  const completed = objs.length > 0 && objs[objs.length - 1].status === 2;

  const party_members = personas.map((p) => ({
    name: p.character,
    primary: p.primary, secondary: p.secondary,
    is_player: true, is_hero: false, is_henchman: false,
    deaths: Math.floor(Math.random() * (completed ? 2 : 4)),
  }));

  return {
    machineKey: personas[uploader].machineKey,
    payload: {
      party: {
        utc_start: utcStartSeconds,
        map_id: dungeon.mapId,
        character_name: party_members[uploader].name,
        end_reason: endReason,
        party_members,
      },
      objective: {
        name: dungeon.name,
        instance_start: 400000 + Math.floor(Math.random() * 200000),
        utc_start: utcStartSeconds + 2,
        objectives: objs,
        duration: elapsedMs,
      },
    },
  };
}

const runSpecs = [
  { dungeon: DUNGEONS[1], uploader: 0, reachedCount: 3, lastStatus: 2, baseDurationMs: 18 * 60 * 1000, daysAgo: 6, endReason: END_REASONS[0] },
  { dungeon: DUNGEONS[1], uploader: 2, reachedCount: 2, lastStatus: 1, baseDurationMs: 18 * 60 * 1000, daysAgo: 5, endReason: END_REASONS[1] },
  { dungeon: DUNGEONS[2], uploader: 1, reachedCount: 3, lastStatus: 2, baseDurationMs: 15 * 60 * 1000, daysAgo: 4, endReason: END_REASONS[0] },
  { dungeon: DUNGEONS[3], uploader: 3, reachedCount: 5, lastStatus: 2, baseDurationMs: 32 * 60 * 1000, daysAgo: 3, endReason: END_REASONS[0] },
  { dungeon: DUNGEONS[3], uploader: 4, reachedCount: 3, lastStatus: 1, baseDurationMs: 32 * 60 * 1000, daysAgo: 2, endReason: END_REASONS[2] },
  { dungeon: DUNGEONS[0], uploader: 5, reachedCount: 1, lastStatus: 2, baseDurationMs: 6 * 60 * 1000, daysAgo: 1, endReason: END_REASONS[0] },
];

for (const [i, spec] of runSpecs.entries()) {
  const { machineKey, payload } = buildRun(spec);
  const res = await fetch(`${BACKEND}/upload-run`, {
    method: 'POST',
    headers: { 'X-Machine-Key': machineKey, 'X-Plugin-Version': PLUGIN_VERSION, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const body = await res.json();
  console.log(`dungeon run ${i + 1}: ${spec.dungeon.name} end_reason=${payload.party.end_reason} status=${res.status}`, body);
  if (!res.ok) {
    process.exitCode = 1;
  }
}
