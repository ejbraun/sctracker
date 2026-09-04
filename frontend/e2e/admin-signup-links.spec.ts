import { expect, test } from '@playwright/test';
import { makeAdmin, signUp, uniqueName } from './helpers';

/**
 * specs/frontend/08-admin-signup-links.md against a real running backend. An admin mints a
 * multi-use signup link; a fresh visitor signs up through it without entering a key; the admin
 * page then shows the use count ticked up.
 */
test.describe('admin signup links', () => {
  test('generates a link and someone signs up through it', async ({ page, browser }) => {
    const admin = uniqueName('linkadmin');
    await signUp(page, admin);
    makeAdmin(admin);
    // reload so useAuth() picks up is_admin (fetched once with staleTime: Infinity)
    await page.reload();

    await page.getByRole('link', { name: 'Signup Links', exact: true }).click();
    await expect(page).toHaveURL('/admin/signup-links');

    await page.getByLabel('Label (optional)').fill('e2e-recruit');
    await page.getByRole('button', { name: 'Generate link' }).click();

    await expect(page.getByText("This link won't be shown again")).toBeVisible();
    const url = (await page.getByTestId('signup-link-url').textContent())!.trim();
    expect(url).toContain('/signup?invite=');

    // A fresh visitor with no admin session signs up via the link.
    const guestContext = await browser.newContext();
    const guest = await guestContext.newPage();
    await guest.goto(url);
    await expect(guest.getByText('Signing up with an invite link.')).toBeVisible();
    await expect(guest.getByLabel('Signup key')).toHaveCount(0);

    await guest.getByLabel('Username').fill(uniqueName('invitee'));
    await guest.getByLabel('Password', { exact: true }).fill('password123');
    await guest.getByLabel('Confirm password').fill('password123');
    await guest.getByRole('button', { name: 'Sign up' }).click();
    await guest.waitForURL('/');
    await guestContext.close();

    // Back on the admin page, the link now shows one use.
    await page.reload();
    await expect(page.getByRole('row', { name: /e2e-recruit/ })).toContainText('1 / 10');
  });
});
