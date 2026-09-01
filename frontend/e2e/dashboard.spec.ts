import { expect, test } from '@playwright/test';
import { signUp, uniqueName } from './helpers';

/**
 * specs/frontend/04-leaderboards.md's map picker, "/". Now a real chooser (specs/features/
 * fow-and-party-size.md): more than one map is supported, so the Dashboard lets you pick a
 * map + party size and jump to that map's Leaderboards / Loserboards / Run History.
 */
test('the Dashboard map picker links to the chosen map', async ({ page }) => {
  await signUp(page, uniqueName('dashboarduser'));
  await expect(page).toHaveURL(/\/$/);

  // Defaults to Underworld, 8-man (its only size).
  await page.getByRole('link', { name: 'View Leaderboards' }).click();
  await expect(page).toHaveURL('/leaderboards/72?partySize=8');
  await expect(page.getByRole('heading', { name: 'Leaderboards' })).toBeVisible();

  // Switch to the Fissure of Woe and the links follow — it still defaults to the duo even though
  // it now offers every size 1-8.
  await page.goBack();
  await page.getByLabel('Map').selectOption('34');
  await page.getByRole('link', { name: 'View Run History' }).click();
  await expect(page).toHaveURL('/runs?map=34&partySize=2');
});
