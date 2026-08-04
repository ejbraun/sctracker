import { expect, test } from '@playwright/test';
import { signUp, uniqueName } from './helpers';

/** specs/frontend/03-characters.md against a real running backend. */
test.describe('characters', () => {
  test.beforeEach(async ({ page }) => {
    await signUp(page, uniqueName('charuser'));
    await page.getByRole('link', { name: 'Characters' }).click();
    await expect(page).toHaveURL('/characters');
  });

  test('adds a character and it appears in the table', async ({ page }) => {
    const charName = uniqueName('Toon');
    await page.getByLabel('Character name').fill(charName);
    await page.getByRole('button', { name: 'Add character' }).click();

    await expect(page.getByRole('row', { name: new RegExp(charName) })).toBeVisible();
  });

  test('adding a duplicate character name shows the conflict error from the API', async ({ page }) => {
    const charName = uniqueName('Dupe');
    await page.getByLabel('Character name').fill(charName);
    await page.getByRole('button', { name: 'Add character' }).click();
    await expect(page.getByRole('row', { name: new RegExp(charName) })).toBeVisible();

    await page.getByLabel('Character name').fill(charName);
    await page.getByRole('button', { name: 'Add character' }).click();
    await expect(page.getByText(/already registered/i)).toBeVisible();
  });

  test('removes a character after confirming', async ({ page }) => {
    const charName = uniqueName('Removable');
    await page.getByLabel('Character name').fill(charName);
    await page.getByRole('button', { name: 'Add character' }).click();
    const row = page.getByRole('row', { name: new RegExp(charName) });
    await expect(row).toBeVisible();

    page.once('dialog', (dialog) => dialog.accept());
    await row.getByRole('button', { name: 'Remove' }).click();
    await expect(row).not.toBeVisible();
  });
});
