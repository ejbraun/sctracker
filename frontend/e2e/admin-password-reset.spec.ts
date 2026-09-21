import { expect, test } from '@playwright/test';
import { makeAdmin, signUp, uniqueName } from './helpers';

/**
 * specs/frontend/10-admin-password-reset.md against a real running backend. An admin mints a reset
 * link for a user; a visitor with no session opens it, sets a new password and logs in with it; the
 * link is then dead.
 */
test.describe('admin password reset', () => {
  test('admin generates a link and the user resets their password through it', async ({ page, browser }) => {
    const victim = uniqueName('victim');
    const victimContext = await browser.newContext();
    const victimPage = await victimContext.newPage();
    await signUp(victimPage, victim, 'oldpassword1');

    const admin = uniqueName('resetadmin');
    await signUp(page, admin);
    makeAdmin(admin);
    // reload so useAuth() picks up is_admin (fetched once with staleTime: Infinity)
    await page.reload();

    await page.getByRole('link', { name: 'User Management', exact: true }).click();
    await expect(page).toHaveURL('/admin/users');

    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('row', { name: new RegExp(victim) }).getByRole('button', { name: 'Reset password' }).click();

    await expect(page.getByText("It won't be shown again")).toBeVisible();
    const url = (await page.getByTestId('password-reset-url').textContent())!.trim();
    expect(url).toContain('/reset-password?token=');

    // The user's existing session dies with the reset; a fresh visitor opens the link.
    const guestContext = await browser.newContext();
    const guest = await guestContext.newPage();
    await guest.goto(url);
    await guest.getByLabel('New password', { exact: true }).fill('brandnewpass1');
    await guest.getByLabel('Confirm new password').fill('brandnewpass1');
    await guest.getByRole('button', { name: 'Set password' }).click();

    await guest.waitForURL('/login');
    await expect(guest.getByText('Password updated.')).toBeVisible();
    await guest.getByLabel('Username').fill(victim);
    await guest.getByLabel('Password', { exact: true }).fill('brandnewpass1');
    await guest.getByRole('button', { name: 'Login' }).click();
    await guest.waitForURL('/');

    // Single use: the same link now errors.
    await guest.context().clearCookies();
    await guest.goto(url);
    await guest.getByLabel('New password', { exact: true }).fill('yetanotherpass1');
    await guest.getByLabel('Confirm new password').fill('yetanotherpass1');
    await guest.getByRole('button', { name: 'Set password' }).click();
    await expect(guest.getByText('invalid or expired reset link')).toBeVisible();

    // The victim's original browser session was revoked by the reset.
    await victimPage.reload();
    await expect(victimPage).toHaveURL(/\/login/);

    await guestContext.close();
    await victimContext.close();
  });
});
