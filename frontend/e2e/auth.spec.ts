import { expect, test } from '@playwright/test';
import { signUp, uniqueName } from './helpers';

/** specs/frontend/01-auth.md against a real running backend. */
test.describe('auth', () => {
  test('unauthenticated visitor is redirected to login with a redirect param', async ({ page }) => {
    await page.goto('/characters');
    await expect(page).toHaveURL(/\/login\?redirect=%2Fcharacters/);
  });

  test('signup creates an account, logs in, and lands on the dashboard', async ({ page }) => {
    const username = uniqueName('signup');
    await signUp(page, username);
    // Lands on "/" — the Dashboard is a map/party-size chooser now (specs/features/
    // fow-and-party-size.md), no longer an auto-forward to the sole map's leaderboard.
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByLabel('Map')).toBeVisible();
  });

  test('signup rejects a mismatched confirm-password client-side, before hitting the API', async ({ page }) => {
    await page.goto('/signup');
    await page.getByLabel('Username').fill(uniqueName('mismatch'));
    await page.getByLabel('Password', { exact: true }).fill('password123');
    await page.getByLabel('Confirm password').fill('somethingelse');
    // Native `required` validation would otherwise block the submit before the client-side
    // mismatch check (which fires first in the JS handler, ahead of the API call) ever runs.
    await page.getByLabel('Signup key').fill('irrelevant-for-this-check');
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Passwords do not match')).toBeVisible();
    await expect(page).toHaveURL('/signup');
  });

  test('login with the wrong password shows an error and does not navigate away', async ({ page }) => {
    const username = uniqueName('wrongpw');
    await signUp(page, username);
    await page.getByRole('button', { name: 'Logout' }).click();
    await expect(page).toHaveURL('/login');

    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Password').fill('totallywrongpassword');
    await page.getByRole('button', { name: 'Login' }).click();

    await expect(page.getByText(/invalid credentials/i)).toBeVisible();
    await expect(page).toHaveURL('/login');
  });

  test('logout returns to login and protected routes reject the stale session', async ({ page }) => {
    const username = uniqueName('logout');
    await signUp(page, username);
    await page.getByRole('button', { name: 'Logout' }).click();
    await expect(page).toHaveURL('/login');

    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });

  test('login with correct credentials reaches the dashboard', async ({ page }) => {
    const username = uniqueName('relogin');
    await signUp(page, username);
    await page.getByRole('button', { name: 'Logout' }).click();

    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Password').fill('password123');
    await page.getByRole('button', { name: 'Login' }).click();

    // Lands on "/" — the Dashboard is a map/party-size chooser now (specs/features/
    // fow-and-party-size.md), no longer an auto-forward to the sole map's leaderboard.
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByLabel('Map')).toBeVisible();
  });
});
