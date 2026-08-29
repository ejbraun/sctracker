// Seeds a local dev backend with a few Fissure of Woe **duo** runs (party size 2, role =
// primary profession — Ranger / Derv), via the real POST /upload-run / signup / characters
// endpoints. The FoW analogue of seed-uw-runs.mjs, kept deliberately small: FoW support starts as
// duos only (specs/features/fow-and-party-size.md), so there's no 8-role roster to build.
//
// Usage: node scripts/seed-fow-runs.mjs
// Requires the backend running against a real MySQL with changesets 036-039 applied (make db-up +
// mvn spring-boot:run). Point BACKEND_URL elsewhere if not on localhost:8080. Not idempotent —
// wipe first for a clean reseed (see seed-uw-runs.mjs's note).

import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';
const SENTINEL = 4294967295;
const FISSURE_OF_WOE_MAP_ID = 34;
// Must be >= the backend's static/SCTracker.version.json "version" or /upload-run returns 426.
const PLUGIN_VERSION = process.env.PLUGIN_VERSION ?? '9';
const END_REASONS = ['wipe', 'resign', 'unknown'];

// Profession ids (specs/backend/02). A FoW duo is one Ranger-primary + one Dervish-primary.
const RANGER = 2, ASSASSIN = 7, DERVISH = 10, MONK = 3;

// The FoW route, exactly as GWToolboxdll's ObjectiveTimer FoW ObjectiveSet emits it (and as
// changeset 039 seeds role_objectives for). Every run's objectives array carries all 11, in order.
const ROUTE = ['ToC', 'Wailing Lord', 'Griffons', 'Defend', 'Forge', 'Menzies',
  'Restore', 'Khobay', 'ToS', 'Burning Forest', 'The Hunt'];

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

// --- Four duo partners, each with a Ranger and a Derv character ------------------------------

const ALIASES = ['DuoHowl', 'DuoZed', 'DuoNova', 'DuoRook'];

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
  const characters = {
    Ranger: `${alias} Trapper`,
    Derv: `${alias} Scythe`,
  };
  for (const name of Object.values(characters)) {
    await api('/api/characters', { method: 'POST', body: { character_name: name }, cookie });
  }
  personas.push({ alias, machineKey: key.json.key, characters });
  console.log(`persona ${i + 1}/${ALIASES.length}: ${alias} — Ranger + Derv characters created`);
}

// --- A handful of fuzzed FoW duo runs -------------------------------------------------------

function objectives(reachedCount, lastStatus, baseDurationMs) {
  const perStop = Math.round(baseDurationMs / ROUTE.length);
  let clock = 0;
  const list = ROUTE.map((name, i) => {
    if (i >= reachedCount) {
      return { name, status: 0, start: SENTINEL, done: SENTINEL, duration: SENTINEL, indent: 0 };
    }
    const status = i === reachedCount - 1 ? lastStatus : 2;
    const start = clock;
    const stopDuration = Math.round(perStop * (0.8 + Math.random() * 0.4));
    clock += stopDuration;
    return {
      name, status, start,
      done: status === 2 ? clock : SENTINEL,
      duration: status === 2 ? stopDuration : SENTINEL,
      indent: 0,
    };
  });
  return { list, elapsedMs: clock };
}

function buildRun({ rangerPersona, dervPersona, reachedCount, lastStatus, baseDurationMs, daysAgo, endReason }) {
  const utcStartSeconds = Math.floor(Date.now() / 1000) - daysAgo * 86400 - Math.floor(Math.random() * 3600);
  const { list: objs, elapsedMs } = objectives(reachedCount, lastStatus, baseDurationMs);
  const completed = objs.length > 0 && objs[objs.length - 1].status === 2;

  const party_members = [
    {
      name: personas[rangerPersona].characters.Ranger,
      primary: RANGER, secondary: ASSASSIN,
      is_player: true, is_hero: false, is_henchman: false,
      deaths: Math.floor(Math.random() * (completed ? 2 : 4)),
    },
    {
      name: personas[dervPersona].characters.Derv,
      primary: DERVISH, secondary: MONK,
      is_player: true, is_hero: false, is_henchman: false,
      deaths: Math.floor(Math.random() * (completed ? 2 : 4)),
    },
  ];

  return {
    machineKey: personas[rangerPersona].machineKey, // the Ranger's account uploads
    payload: {
      party: {
        utc_start: utcStartSeconds,
        map_id: FISSURE_OF_WOE_MAP_ID,
        character_name: party_members[0].name,
        end_reason: endReason,
        party_members,
      },
      objective: {
        name: 'The Fissure of Woe',
        instance_start: 400000 + Math.floor(Math.random() * 200000),
        utc_start: utcStartSeconds + 2,
        objectives: objs,
        duration: elapsedMs,
      },
    },
  };
}

const runSpecs = [
  { rangerPersona: 0, dervPersona: 1, reachedCount: 11, lastStatus: 2, baseDurationMs: 30 * 60 * 1000, daysAgo: 6, endReason: END_REASONS[2] },
  { rangerPersona: 1, dervPersona: 0, reachedCount: 4, lastStatus: 1, baseDurationMs: 30 * 60 * 1000, daysAgo: 5, endReason: END_REASONS[0] },
  { rangerPersona: 2, dervPersona: 3, reachedCount: 11, lastStatus: 2, baseDurationMs: 24 * 60 * 1000, daysAgo: 3, endReason: END_REASONS[2] },
  { rangerPersona: 3, dervPersona: 2, reachedCount: 8, lastStatus: 1, baseDurationMs: 30 * 60 * 1000, daysAgo: 2, endReason: END_REASONS[1] },
  { rangerPersona: 0, dervPersona: 2, reachedCount: 11, lastStatus: 2, baseDurationMs: 27 * 60 * 1000, daysAgo: 1, endReason: END_REASONS[2] },
  { rangerPersona: 2, dervPersona: 1, reachedCount: 1, lastStatus: 0, baseDurationMs: 30 * 60 * 1000, daysAgo: 0.5, endReason: END_REASONS[0] },
];

for (const [i, spec] of runSpecs.entries()) {
  const { machineKey, payload } = buildRun(spec);
  const res = await fetch(`${BACKEND}/upload-run`, {
    method: 'POST',
    headers: { 'X-Machine-Key': machineKey, 'X-Plugin-Version': PLUGIN_VERSION, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const body = await res.json();
  console.log(`fow run ${i + 1}: end_reason=${payload.party.end_reason} status=${res.status}`, body);
  if (!res.ok) {
    process.exitCode = 1;
  }
}
