import { expect, test } from '@playwright/test';
import { signUp, uniqueName } from './helpers';

/**
 * specs/frontend/09-downloads.md — a user with no launcher grant sees the "ask an admin" empty
 * state, not a download link.
 */
test.describe('launcher page', () => {
  test('shows the granted-per-account empty state for an ungranted user', async ({ page }) => {
    await signUp(page, uniqueName('launcheruser'));

    await page.getByRole('link', { name: 'Launcher', exact: true }).click();
    await expect(page).toHaveURL('/launcher');

    await expect(page.getByText(/granted per account/)).toBeVisible();
    await expect(page.getByRole('link', { name: /Download launcher/ })).toHaveCount(0);
  });
});
