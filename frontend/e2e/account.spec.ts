import { expect, test } from '@playwright/test';
import { goToAccount, signUp, uniqueName } from './helpers';

/** specs/frontend/02-account.md — machine-key generate/list/revoke against a real running backend. */
test.describe('account', () => {
  test.beforeEach(async ({ page }) => {
    await signUp(page, uniqueName('accountuser'));
    await goToAccount(page);
    await expect(page).toHaveURL('/account');
  });

  test('generating a key reveals the raw key once, and the list never shows it again', async ({ page }) => {
    const label = uniqueName('laptop');
    await page.getByLabel('Label (optional)').fill(label);
    await page.getByRole('button', { name: 'Generate new key' }).click();

    await expect(page.getByText("This key won't be shown again")).toBeVisible();
    const rawKey = await page.getByTestId('raw-machine-key').textContent();
    expect(rawKey).toBeTruthy();
    expect(rawKey!.length).toBeGreaterThan(10);

    await expect(page.getByRole('row', { name: new RegExp(label) })).toBeVisible();

    await page.getByRole('button', { name: 'Dismiss' }).click();
    await expect(page.getByText("This key won't be shown again")).not.toBeVisible();
    // The raw key text itself must not appear anywhere else on the page (e.g. leaked into the table).
    await expect(page.getByText(rawKey!, { exact: true })).not.toBeVisible();
  });

  test('revoking a key marks it revoked and hides the revoke button', async ({ page }) => {
    const label = uniqueName('revokeme');
    await page.getByLabel('Label (optional)').fill(label);
    await page.getByRole('button', { name: 'Generate new key' }).click();
    await page.getByRole('button', { name: 'Dismiss' }).click();

    const row = page.getByRole('row', { name: new RegExp(label) });
    page.once('dialog', (dialog) => dialog.accept());
    await row.getByRole('button', { name: 'Revoke' }).click();

    await expect(row.getByText(/^Revoked/)).toBeVisible();
    await expect(row.getByRole('button', { name: 'Revoke' })).toHaveCount(0);
  });

  test('setting an alias persists it and shows it on the profile', async ({ page }) => {
    const alias = uniqueName('Howl');
    await expect(page.getByText('Alias: —')).toBeVisible();

    await page.getByLabel('Alias').fill(alias);
    await page.getByRole('button', { name: 'Save alias' }).click();

    await expect(page.getByText(`Alias: ${alias}`)).toBeVisible();
    await page.reload();
    await expect(page.getByText(`Alias: ${alias}`)).toBeVisible();
  });

  test('clearing an alias with a blank value reverts the profile to no alias', async ({ page }) => {
    const alias = uniqueName('Temp');
    await page.getByLabel('Alias').fill(alias);
    await page.getByRole('button', { name: 'Save alias' }).click();
    await expect(page.getByText(`Alias: ${alias}`)).toBeVisible();

    await page.getByLabel('Alias').fill('');
    await page.getByRole('button', { name: 'Save alias' }).click();
    await expect(page.getByText('Alias: —')).toBeVisible();
  });
});
