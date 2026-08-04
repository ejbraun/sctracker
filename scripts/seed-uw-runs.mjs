// Seeds a local dev backend with a realistic guild roster (12 accounts x 8 characters, one per
// role) and fuzzed Underworld runs, via the real POST /upload-run / signup / characters endpoints
// (same as the SDK plugin + website would call). Payload shape mirrors specs/backend/00-overview.md
// and 02-ingestion-upload-run.md's real GWToolboxdll sample: utc_start/objective.utc_start as epoch
// seconds, objective.instance_start as a raw non-timestamp ms offset, 4294967295 as the "not reached"
// sentinel on start/done/duration. end_reason values are restricted to the actual documented enum
// (specs/backend/01-schema-and-migrations.md: "end_reason is one of wipe / resign / unknown").
//
// Usage: node scripts/seed-uw-runs.mjs
// Requires the backend running against a real MySQL (e.g. make db-up + mvn spring-boot:run) —
// point BACKEND_URL elsewhere if it's not on the default localhost:8080.
// Not idempotent: re-running against a DB that already has this data will fail on the duplicate
// usernames/aliases/character names. Wipe first for a clean reseed:
//   docker compose down -v && docker compose up -d mysql && mvn spring-boot:run

import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';
const SENTINEL = 4294967295;
const UNDERWORLD_MAP_ID = 72;
const END_REASONS = ['wipe', 'resign', 'unknown'];

/**
 * Signup is invite-gated (see AuthService.signup / scripts/generate-signup-keys.mjs) — mints a
 * throwaway key directly in the dev MySQL container for each persona rather than burning through
 * the finite pool a real deployment hands out. Assumes this script runs from the repo root, same
 * as its own usage note below.
 */
function mintSignupKey() {
  const rawKey = randomBytes(32).toString('base64url');
  const hash = createHash('sha256').update(rawKey, 'utf8').digest('hex');
  execFileSync('docker', ['compose', 'exec', '-T', 'mysql', 'mysql', '-uroot', '-proot', 'uwtracker', '-e',
      `INSERT INTO signup_keys (key_hash) VALUES ('${hash}');`], { stdio: ['ignore', 'ignore', 'ignore'] });
  return rawKey;
}

// Profession ids from specs/backend/02-ingestion-upload-run.md.
const WARRIOR = 1, RANGER = 2, MONK = 3, NECROMANCER = 4, MESMER = 5, ELEMENTALIST = 6, ASSASSIN = 7, RITUALIST = 8, DERVISH = 10;

// RoleDerivation.java's combo table — T1-T3 are positional, T4/LT/spiker/sos/emo resolve by
// (primary, secondary). Two valid combo variants exist for spiker and sos; alternate between them
// across runs for realism.
const ROLE_PROFESSIONS = {
  T1: [RANGER, ASSASSIN], T2: [RANGER, ASSASSIN], T3: [RANGER, ASSASSIN],
  T4: [ELEMENTALIST, MESMER], LT: [MESMER, ASSASSIN], emo: [ELEMENTALIST, MONK],
};
const SPIKER_VARIANTS = [[DERVISH, WARRIOR], [MESMER, RANGER]];
const SOS_VARIANTS = [[RITUALIST, RANGER], [NECROMANCER, RANGER]];
const ROLE_ORDER = ['T1', 'T2', 'T3', 'T4', 'LT', 'spiker', 'sos', 'emo'];

function professionsFor(role, variantIndex) {
  if (role === 'spiker') return SPIKER_VARIANTS[variantIndex % SPIKER_VARIANTS.length];
  if (role === 'sos') return SOS_VARIANTS[variantIndex % SOS_VARIANTS.length];
  return ROLE_PROFESSIONS[role];
}

async function api(path, { method = 'GET', body, cookie } = {}) {
  const res = await fetch(`${BACKEND}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(cookie ? { Cookie: cookie } : {}),
    },
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

// --- 1. Twelve guild-member personas -----------------------------------------------------------

const ALIASES = ['Howl', 'Zed', 'Nova', 'Ashen', 'Rook', 'Vex', 'Talon', 'Wren', 'Frost', 'Ember', 'Sable', 'Onyx'];

// Name generator for 96 unique character names (12 personas x 8 roles) — combines two word pools
// deterministically so every (persona index, role index) pair gets a distinct, stable name.
const GIVEN = ['Iron', 'Shadow', 'Grave', 'Frost', 'Whisper', 'Sable', 'Lunar', 'Holy', 'Quick', 'Silent',
  'Bramble', 'Cinder', 'Pale', 'Doom', 'River', 'Ember', 'Ashen', 'Night', 'Wolfs', 'Star',
  'Thistle', 'Blood', 'Moon', 'Sun', 'Copper', 'Dusk', 'Rust', 'Void', 'Hollow', 'Wraith',
  'Brackon', 'Talon', 'Verdant', 'Pyre', 'Crow', 'Grim', 'Salt', 'Dune', 'Storm', 'Winter',
  'Golden', 'Crimson', 'Obsidian', 'Amber', 'Jade', 'Onyx', 'Ivory', 'Marble', 'Granite', 'Slate',
  'Rowan', 'Cedar', 'Alder', 'Birch', 'Elder', 'Fenwick', 'Garrow', 'Hollis', 'Kestrel', 'Larkin',
  'Marrow', 'Nettle', 'Osprey', 'Pike', 'Quill', 'Reed', 'Sparrow', 'Thorn', 'Umbral', 'Vale',
  'Wight', 'Yew', 'Zephyr', 'Ash', 'Briar', 'Cove', 'Dawn', 'Ebon', 'Fable', 'Glade',
  'Haze', 'Ink', 'Jinx', 'Knell', 'Lore', 'Mire', 'Null', 'Opal', 'Quartz', 'Rune',
  'Soot', 'Torrent', 'Umber', 'Vesper', 'Wisp', 'Xeno'];
const SURNAME = ['Wong', 'Vex', 'Tom', 'Lyra', 'Death', 'Nightshade', 'Howl', 'Mae', 'Dash', 'Finn',
  'Ivy', 'Ashwake', 'Zane', 'Rex', 'Sol', 'Nia', 'Kade', 'Ren', 'Odette', 'Priska',
  'Bea', 'Garrick', 'Tarek', 'Alina', 'Jess', 'Mira', 'Kian', 'Theo', 'Nash', 'Cato',
  'Dorian', 'Sasha', 'Finch', 'Bram', 'Wynn', 'Ozzy', 'Ilsa', 'Petra', 'Cody', 'Wren',
  'Marsh', 'Vale', 'Croft', 'Dane', 'Ellery', 'Fenn', 'Gale', 'Haven', 'Idris', 'Jove',
  'Kestrel', 'Lyle', 'Moss', 'Nyx', 'Orin', 'Pell', 'Quinn', 'Reeve', 'Sage', 'Tam',
  'Ulric', 'Vane', 'Wilder', 'Yara', 'Zephyrine', 'Ainsworth', 'Blackwood', 'Corvin', 'Delacroix', 'Estwood',
  'Foxglove', 'Graystone', 'Hollowell', 'Ironside', 'Jettburn', 'Kestrelwood', 'Lindqvist', 'Moorland', 'Nightingale', 'Oakhurst',
  'Pemberton', 'Quillfeather', 'Ravensworth', 'Stormcrow', 'Thistlewood', 'Underhill', 'Vaneford', 'Wyndham', 'Yorkshire', 'Zellweger',
  'Ashford', 'Brightwater', 'Crestfall'];

function characterName(personaIndex, roleIndex) {
  const i = personaIndex * ROLE_ORDER.length + roleIndex;
  return `${GIVEN[i % GIVEN.length]} ${SURNAME[(i * 7 + 3) % SURNAME.length]}`;
}

const personas = ALIASES.map((alias, i) => ({
  alias,
  username: alias.toLowerCase() + 'player',
  password: 'seedpassword123',
  characters: {}, // role -> character name
  cookie: null,
  machineKey: null,
}));

for (const [i, persona] of personas.entries()) {
  const signup = await api('/api/signup', {
    method: 'POST',
    body: { username: persona.username, password: persona.password, signup_key: mintSignupKey() },
  });
  persona.cookie = signup.cookie;

  await api('/api/account/alias', { method: 'PATCH', body: { alias: persona.alias }, cookie: persona.cookie });

  const key = await api('/api/account/machine-keys', {
    method: 'POST',
    body: { label: `${persona.alias}'s GWToolbox` },
    cookie: persona.cookie,
  });
  persona.machineKey = key.json.key;

  for (const [roleIndex, role] of ROLE_ORDER.entries()) {
    const name = characterName(i, roleIndex);
    await api('/api/characters', {
      method: 'POST',
      body: { character_name: name },
      cookie: persona.cookie,
    });
    persona.characters[role] = name;
  }
  console.log(`persona ${i + 1}/12: ${persona.alias} (${persona.username}) — 8 characters created`);
}

// --- 2. Fuzzed Underworld runs, rosters drawn from 8 different personas per run ----------------

// The full real Underworld route, confirmed against a real GWToolboxdll payload sample (pasted
// directly by the user, superseding specs/backend/02-ingestion-upload-run.md's 2-entry excerpt):
// every run's objectives array always has all 11 of these, in this order, start to finish — stops
// not yet reached stay in the array with status 0 and sentinel start/done/duration, they aren't
// omitted the way an earlier version of this script assumed.
const ROUTE = ['Chamber', 'Restore', 'Escort', 'UWG', 'Vale', 'Waste', 'Pits', 'Planes', 'Mnts', 'Pools', 'Dhuum'];

function pickRoster(seed) {
  // A different 8-of-12 persona subset (one per role) for each run, deterministic per seed.
  const order = [...personas.keys()];
  for (let i = order.length - 1; i > 0; i--) {
    const j = (seed * 2654435761 + i * 97) % (i + 1);
    [order[i], order[j >= 0 ? j : 0]] = [order[j >= 0 ? j : 0], order[i]];
  }
  return order.slice(0, 8).map((personaIndex, slot) => ({ personaIndex, role: ROLE_ORDER[slot] }));
}

/**
 * @param reachedCount how many of ROUTE's 11 stops this run actually got to (1..ROUTE.length)
 * @param lastStatus status of the last-reached stop: 2 = cleared it, 1 = wiped partway through it,
 *   0 = wiped before starting it. Every stop after index reachedCount-1 stays untouched (status 0,
 *   sentinel values) — always present in the array, per the real sample.
 * @returns {{ list: object[], elapsedMs: number }} elapsedMs is total time through every reached
 *   stop (including a partial/failed final one) — this is what the top-level objective.duration
 *   should be, per the real sample: a run where every objective is still status 0 (nothing
 *   completed) still carries a real, non-sentinel `duration` (8450ms in the sample), so it reads as
 *   "elapsed time so far", reported unconditionally — not "time to completion". A completed run's
 *   elapsedMs and its objectives' own start/done/duration values are still internally consistent
 *   with each other either way, since both are built from the same running `clock`.
 */
function objectives(reachedCount, lastStatus, baseDurationMs) {
  const perStop = Math.round(baseDurationMs / ROUTE.length);
  let clock = 0;
  const list = ROUTE.map((name, i) => {
    if (i >= reachedCount) {
      return { name, status: 0, start: SENTINEL, done: SENTINEL, duration: SENTINEL, indent: 0 };
    }
    const isLast = i === reachedCount - 1;
    const status = isLast ? lastStatus : 2;
    const start = clock;
    const stopDuration = Math.round(perStop * (0.8 + Math.random() * 0.4));
    clock += stopDuration;
    const done = status === 2 ? clock : SENTINEL;
    const duration = status === 2 ? stopDuration : SENTINEL;
    return { name, status, start, done, duration, indent: 0 };
  });
  return { list, elapsedMs: clock };
}

function buildRun({ seed, reachedCount, lastStatus, baseDurationMs, daysAgo, endReason }) {
  const roster = pickRoster(seed);
  const utcStartSeconds = Math.floor(Date.now() / 1000) - daysAgo * 86400 - Math.floor(Math.random() * 3600);
  const { list: objs, elapsedMs } = objectives(reachedCount, lastStatus, baseDurationMs);
  // Matches UploadRunWriter.createRun() exactly: completed = the array's last element's status == 2
  // — which, since the array always has all 11 stops in order, means Dhuum specifically.
  const completed = objs.length > 0 && objs[objs.length - 1].status === 2;
  // Reported unconditionally — see objectives()'s doc comment on why this isn't gated on completed.
  const totalDuration = elapsedMs;

  const party_members = roster.map(({ personaIndex, role }, slot) => {
    const [primary, secondary] = professionsFor(role, seed + slot);
    return {
      name: personas[personaIndex].characters[role],
      primary,
      secondary,
      is_player: true,
      is_hero: false,
      is_henchman: false,
      // Fuzzed like everything else here — failed runs skew deathier than clean ones.
      deaths: Math.floor(Math.random() * (completed ? 2 : 4)),
    };
  });

  const uploaderPersona = personas[roster[0].personaIndex]; // T1 slot's account uploads, like a real player would

  return {
    machineKey: uploaderPersona.machineKey,
    payload: {
      party: {
        utc_start: utcStartSeconds,
        map_id: UNDERWORLD_MAP_ID,
        character_name: party_members[0].name,
        end_reason: endReason,
        party_members,
      },
      objective: {
        name: 'The Underworld',
        instance_start: 400000 + Math.floor(Math.random() * 200000),
        utc_start: utcStartSeconds + 2,
        objectives: objs,
        duration: totalDuration,
      },
    },
  };
}

// reachedCount spans the full 11-stop route now — fuzzed via how far each run got, lastStatus
// (0 = wiped before starting that stop, 1 = wiped partway through it, 2 = cleared it) and
// daysAgo/baseDurationMs.
const runSpecs = [
  { seed: 1, reachedCount: 11, lastStatus: 2, baseDurationMs: 21 * 60 * 1000, daysAgo: 6, endReason: END_REASONS[2] }, // full clear (Dhuum down)
  { seed: 2, reachedCount: 2, lastStatus: 1, baseDurationMs: 21 * 60 * 1000, daysAgo: 5, endReason: END_REASONS[1] }, // resign, wiped in Restore
  { seed: 3, reachedCount: 6, lastStatus: 1, baseDurationMs: 21 * 60 * 1000, daysAgo: 4, endReason: END_REASONS[0] }, // wipe, wiped in Waste
  { seed: 4, reachedCount: 11, lastStatus: 2, baseDurationMs: 17 * 60 * 1000, daysAgo: 3, endReason: END_REASONS[2] }, // full clear, faster
  { seed: 5, reachedCount: 1, lastStatus: 0, baseDurationMs: 21 * 60 * 1000, daysAgo: 2, endReason: END_REASONS[0] }, // wipe, before even starting Chamber
  { seed: 6, reachedCount: 11, lastStatus: 2, baseDurationMs: 25 * 60 * 1000, daysAgo: 1, endReason: END_REASONS[2] }, // full clear, slower
  { seed: 7, reachedCount: 9, lastStatus: 1, baseDurationMs: 21 * 60 * 1000, daysAgo: 0.5, endReason: END_REASONS[1] }, // resign, wiped in Pools — almost there
];

for (const [i, spec] of runSpecs.entries()) {
  const { machineKey, payload } = buildRun(spec);
  const res = await fetch(`${BACKEND}/upload-run`, {
    method: 'POST',
    headers: { 'X-Machine-Key': machineKey, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const body = await res.json();
  console.log(`run ${i + 1}: end_reason=${payload.party.end_reason} status=${res.status}`, body);
  if (!res.ok) {
    process.exitCode = 1;
  }
}
