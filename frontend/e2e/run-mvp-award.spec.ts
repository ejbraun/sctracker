import { expect, test, type Page } from '@playwright/test';
import { goToAccount, seedMvpAward, signUp, uniqueName, uploadRun } from './helpers';

/**
 * POST /report-run-mvp's real 60s vote-and-tally window isn't worth waiting out here (see
 * MvpReportIntegrationTest for that coverage, against real MySQL, without the wait either) — this
 * suite is only proving the RunDetail page renders whatever GET /runs/:id's mvp_award field says,
 * the same way run-flow.spec.ts proves rendering without re-testing ingestion's own validation
 * rules. seedMvpAward writes the row directly, the same "seed it in MySQL" approach helpers.ts
 * already uses for signup keys.
 */
async function uploadAndGenerateKey(page: Page, username: string): Promise<number> {
  await signUp(page, username);

  await goToAccount(page);
  await page.getByRole('button', { name: 'Generate new key' }).click();
  const rawKey = (await page.locator('code').first().textContent())!.trim();
  await page.getByRole('button', { name: 'Dismiss' }).click();

  const heroName = uniqueName('MvpHero');
  const utcStartSeconds = Math.floor(Date.now() / 1000);
  const uploadBody = await uploadRun(page, rawKey, heroName, utcStartSeconds);
  return uploadBody.run_id;
}

test('run detail shows the credited role when an MVP award exists', async ({ page }) => {
  const runId = await uploadAndGenerateKey(page, uniqueName('mvprole'));
  seedMvpAward(runId, 'Spiker');

  await page.goto(`/runs/${runId}`);
  const mvpHeading = page.getByRole('heading', { name: 'MVP', exact: true });
  await expect(mvpHeading).toBeVisible();
  const mvpPanel = mvpHeading.locator('xpath=..');
  await expect(mvpPanel).toContainText('Spiker');
});

test('run detail shows Nobody when the MVP award is an explicit Nobody result', async ({ page }) => {
  const runId = await uploadAndGenerateKey(page, uniqueName('mvpnobody'));
  seedMvpAward(runId, null);

  await page.goto(`/runs/${runId}`);
  const mvpHeading = page.getByRole('heading', { name: 'MVP', exact: true });
  await expect(mvpHeading).toBeVisible();
  const mvpPanel = mvpHeading.locator('xpath=..');
  await expect(mvpPanel).toContainText('Nobody');
});

test('run detail has no MVP panel when no vote has resolved for the run', async ({ page }) => {
  const runId = await uploadAndGenerateKey(page, uniqueName('mvpnone'));

  await page.goto(`/runs/${runId}`);
  await expect(page.getByRole('heading', { name: 'MVP', exact: true })).toHaveCount(0);
});
