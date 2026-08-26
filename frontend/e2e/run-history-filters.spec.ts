import { expect, test } from '@playwright/test';
import { goToAccount, signUp, uniqueName } from './helpers';

/**
 * Run History's "person" and "character" filters cross-filter each other (no raw ids, no free-text
 * — see run-flow.spec.ts's note on why every field here is a dropdown). Self-contained: creates its
 * own two accounts/characters rather than relying on whatever demo data happens to be seeded, since
 * this suite has no DB reset between runs (see FRONTEND_IMPLEMENTATION_PROGRESS.md).
 */
test('selecting a person narrows the character dropdown, and selecting a character fills in its person', async ({ page }) => {
  const characterA = uniqueName('AlphaOne');
  const characterB = uniqueName('BetaOne');
  const aliasA = uniqueName('FilterA');
  const aliasB = uniqueName('FilterB');

  await signUp(page, uniqueName('filtersuserA'));
  await goToAccount(page);
  await page.getByLabel('Alias').fill(aliasA);
  await page.getByRole('button', { name: 'Save alias' }).click();
  await page.getByRole('link', { name: 'Characters' }).click();
  await page.getByLabel('Character name').fill(characterA);
  await page.getByRole('button', { name: 'Add character' }).click();
  await expect(page.getByRole('row', { name: new RegExp(characterA) })).toBeVisible();
  await page.getByRole('button', { name: 'Logout' }).click();

  await signUp(page, uniqueName('filtersuserB'));
  await goToAccount(page);
  await page.getByLabel('Alias').fill(aliasB);
  await page.getByRole('button', { name: 'Save alias' }).click();
  await page.getByRole('link', { name: 'Characters' }).click();
  await page.getByLabel('Character name').fill(characterB);
  await page.getByRole('button', { name: 'Add character' }).click();
  await expect(page.getByRole('row', { name: new RegExp(characterB) })).toBeVisible();

  await page.getByRole('link', { name: 'Run History' }).click();
  const characterSelect = page.getByLabel('Character');
  const personSelect = page.getByLabel('Person');

  // Before any person is picked, both characters are choosable.
  await expect(characterSelect.locator('option', { hasText: characterA })).toHaveCount(1);
  await expect(characterSelect.locator('option', { hasText: characterB })).toHaveCount(1);

  // Picking person A narrows the character dropdown down to just their character.
  await personSelect.selectOption({ label: aliasA });
  await expect(characterSelect.locator('option', { hasText: characterA })).toHaveCount(1);
  await expect(characterSelect.locator('option', { hasText: characterB })).toHaveCount(0);

  // Reset, then pick character B directly — person should auto-fill to its owner (B), not stay on A.
  await personSelect.selectOption({ label: 'Any' });
  await characterSelect.selectOption({ label: characterB });
  await expect(personSelect).toHaveValue(await personSelect.locator('option', { hasText: aliasB }).getAttribute('value'));
});
