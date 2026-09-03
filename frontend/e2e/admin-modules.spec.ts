import { expect, test } from '@playwright/test';
import { makeAdmin, signUp, uniqueName } from './helpers';

/**
 * specs/frontend/07-admin-modules.md against a real running backend. The admin promotes their own
 * fresh account, registers a module, then grants it to themselves and confirms it sticks across a
 * reload.
 */
test.describe('admin modules', () => {
  test('registers a module and grants it to a user', async ({ page }) => {
    const admin = uniqueName('modadmin');
    await signUp(page, admin);
    makeAdmin(admin);
    // reload so useAuth() picks up is_admin (fetched once with staleTime: Infinity)
    await page.reload();

    const moduleKey = uniqueName('pp-feat').toLowerCase();

    await page.getByRole('link', { name: 'Modules', exact: true }).click();
    await expect(page).toHaveURL('/admin/modules');

    await page.getByLabel('module key (a-z0-9-)').fill(moduleKey);
    await page.getByLabel('display name').fill('E2E feature module');
    await page.getByLabel('bucket prefix, e.g. plugins/Foo').fill(`plugins/${moduleKey}`);
    await page.getByLabel('artifact object, e.g. Foo.dll').fill(`${moduleKey}.dll`);
    await page.getByRole('button', { name: 'Create module' }).click();

    const moduleRow = page.getByRole('row', { name: new RegExp(moduleKey) });
    await expect(moduleRow).toBeVisible();
    await expect(moduleRow.getByRole('button', { name: 'Private' })).toBeVisible();

    // Grant it to the admin's own account from User Management.
    await page.getByRole('link', { name: 'User Management', exact: true }).click();
    await expect(page).toHaveURL('/admin/users');

    const userRow = page.getByRole('row', { name: new RegExp(admin) });
    await userRow.getByRole('button', { name: 'Modules', exact: true }).click();

    const grantRow = page.getByRole('row', { name: new RegExp(moduleKey) });
    await grantRow.getByRole('button', { name: 'Grant' }).click();
    await expect(grantRow.getByRole('button', { name: 'Revoke' })).toBeVisible();

    await page.reload();
    await page.getByRole('row', { name: new RegExp(admin) }).getByRole('button', { name: 'Modules', exact: true }).click();
    await expect(
      page.getByRole('row', { name: new RegExp(moduleKey) }).getByRole('button', { name: 'Revoke' }),
    ).toBeVisible();
  });
});
