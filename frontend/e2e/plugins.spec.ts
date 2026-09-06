import { expect, test } from '@playwright/test';
import { signUp, uniqueName } from './helpers';

/** specs/frontend/09-downloads.md — the /plugins page always shows the required SCTracker panel. */
test.describe('plugins page', () => {
  test('is reachable from the nav and shows the SCTracker download', async ({ page }) => {
    await signUp(page, uniqueName('pluginsuser'));

    await page.getByRole('link', { name: 'Plugins', exact: true }).click();
    await expect(page).toHaveURL('/plugins');

    await expect(page.getByRole('heading', { name: /SCTracker/ })).toBeVisible();
    await expect(page.getByText('required', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: /Download SCTracker\.dll/ })).toBeVisible();
  });
});
