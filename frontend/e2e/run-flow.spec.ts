import { expect, test } from '@playwright/test';
import { signUp, uniqueName } from './helpers';

const BACKEND_ORIGIN = 'http://localhost:8080';

// The only currently-supported map (specs/backend/01 — maps is a curated, well-defined set seeded
// by migration; /upload-run rejects anything else). No per-test map name to key off of anymore, so
// this suite disambiguates "its" run by character name / run id instead.
const UNDERWORLD_MAP_ID = 72;
const UNDERWORLD_MAP_NAME = 'Underworld';

// Mirrors specs/backend/02-ingestion-upload-run.md's profession ids and RoleDerivation's T1 combo.
const RANGER = 2;
const ASSASSIN = 7;
const MONK = 3;
const MESMER = 5;
const ELEMENTALIST = 6;
const RITUALIST = 8;
const WARRIOR = 1;
const DERVISH = 10;

function buildUploadPayload(heroName: string, utcStartSeconds: number) {
  return {
    party: {
      utc_start: utcStartSeconds,
      map_id: UNDERWORLD_MAP_ID,
      character_name: heroName,
      end_reason: 'victory',
      party_members: [
        { name: heroName, primary: RANGER, secondary: ASSASSIN, is_player: true, is_hero: false, is_henchman: false },
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
 * The end-to-end proof point: a real /upload-run call (as the GW1 SDK plugin would make, machine-key
 * authenticated, no browser session) followed by driving the actual React app to confirm the run
 * surfaces everywhere it should — run history (including lookup by alias and by character, not a raw
 * id — see helpers.ts's note on why every field on this page is a dropdown now), run detail, and
 * leaderboards, including the retroactive character backfill (specs/backend/04-characters.md) feeding
 * into a personal best (specs/backend/05-leaderboards.md) that didn't exist until the character was
 * created.
 */
test('an uploaded run surfaces in run history (by alias/character lookup), run detail, and leaderboards', async ({ page }) => {
  const username = uniqueName('runflow');
  const heroName = uniqueName('E2EHero');
  const alias = uniqueName('E2EAlias');
  const utcStartSeconds = Math.floor(Date.now() / 1000);

  await signUp(page, username);

  // Set an alias — needed for the "Person" lookup below (specs: aliases, not raw person ids).
  await page.getByRole('link', { name: 'Account' }).click();
  await page.getByLabel('Alias').fill(alias);
  await page.getByRole('button', { name: 'Save alias' }).click();
  await expect(page.getByText(`Alias: ${alias}`)).toBeVisible();

  // Generate a machine key via the real Account UI flow.
  await page.getByRole('button', { name: 'Generate new key' }).click();
  const rawKey = (await page.locator('code').first().textContent())!.trim();
  await page.getByRole('button', { name: 'Dismiss' }).click();

  // Upload as the SDK plugin would: machine-key header, no session cookie, straight to the backend
  // (the frontend dev server only proxies /api, not the unprefixed /upload-run endpoint).
  const uploadResponse = await page.request.post(`${BACKEND_ORIGIN}/upload-run`, {
    headers: { 'X-Machine-Key': rawKey, 'Content-Type': 'application/json' },
    data: buildUploadPayload(heroName, utcStartSeconds),
  });
  expect(uploadResponse.ok(), await uploadResponse.text()).toBeTruthy();
  const uploadBody = await uploadResponse.json();
  expect(uploadBody.created).toBe(true);
  const runId = uploadBody.run_id as number;

  // Run detail: navigate straight there by id (every e2e run shares the one supported map now, so
  // there's nothing map-name-specific left to search a table row by) — objectives in sequence order,
  // all 8 participants including our hero with the right role.
  await page.goto(`/runs/${runId}`);
  const objectiveRows = page.locator('table').first().locator('tbody tr');
  await expect(objectiveRows.first()).toContainText('Vale');
  await expect(page.getByRole('cell', { name: heroName })).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp(heroName) })).toContainText('T1');

  // Link a character to the hero's raw name — retroactively backfills run_participants.character_id.
  await page.getByRole('link', { name: 'Characters' }).click();
  await page.getByLabel('Character name').fill(heroName);
  await page.getByRole('button', { name: 'Add character' }).click();
  await expect(page.getByRole('row', { name: new RegExp(heroName) })).toBeVisible();

  // Run History: both the "Person" and "Character" filters are dropdowns (real <select> elements) —
  // no raw id ever typed or shown — populated from GET /api/people (alias only) and
  // GET /api/characters/all (name only).
  await page.getByRole('link', { name: 'Run History' }).click();
  await expect(page.getByLabel('Person')).toHaveJSProperty('tagName', 'SELECT');
  await expect(page.getByLabel('Character')).toHaveJSProperty('tagName', 'SELECT');

  await page.getByLabel('Character').selectOption({ label: heroName });
  await expect(page.getByRole('row', { name: new RegExp(UNDERWORLD_MAP_NAME) })).toBeVisible();

  await page.getByLabel('Character').selectOption({ label: 'Any' });
  await page.getByLabel('Person').selectOption({ label: alias });
  await expect(page.getByRole('row', { name: new RegExp(UNDERWORLD_MAP_NAME) })).toBeVisible();

  // The duration-over-time chart shares the same filters as the table below it — narrowed to just
  // this run by the Person filter above, so exactly one point should be plotted. Clicking it
  // navigates straight to that run's detail page.
  const chartPoint = page.locator('svg[aria-label*="Run duration over time"] [role="link"]');
  await expect(chartPoint).toHaveCount(1);
  await chartPoint.click();
  await expect(page).toHaveURL(new RegExp(`/runs/${runId}$`));
  await page.goBack();

  // Leaderboard: the Dashboard's map picker is a dropdown, not a raw input — but with only one map
  // ever offered (Underworld, per specs/backend/01's curated set), a real click could never change
  // its value, so Dashboard auto-advances straight to that map's leaderboard instead of leaving the
  // user stuck on a picker whose only option is already selected. The "Your Fastest Completions"
  // panel reflects this account's personal best — which didn't exist until the character was
  // linked — and, unlike the ranked "Fastest To Complete Instance" board, isn't affected by however
  // many other runs other e2e executions have piled onto the shared Underworld map (see
  // FRONTEND_IMPLEMENTATION_PROGRESS.md).
  await page.getByRole('link', { name: 'Leaderboards' }).click();
  await expect(page).toHaveURL(new RegExp(`/leaderboards/${UNDERWORLD_MAP_ID}$`));

  // Global and "Yours" are separate panels — "Your Fastest Completions" heading text is unique on
  // the page, so no scoping needed to disambiguate it from the objective sections' own personal
  // panels. Immediately followed by either its table or a "No completed run yet." empty state —
  // never both, so the very next element settles it.
  const yourHeading = page.getByRole('heading', { name: 'Your Fastest Completions', exact: true });
  const yourContent = yourHeading.locator('xpath=following-sibling::*[1]');
  await expect(yourContent).not.toHaveText('No completed run yet.');
  await expect(yourContent).toContainText(/\d/);
});
