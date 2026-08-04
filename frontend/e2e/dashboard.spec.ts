import { expect, test } from '@playwright/test';
import { signUp, uniqueName } from './helpers';

/**
 * specs/frontend/04-leaderboards.md's map picker, "/". Regression coverage for a real bug: with
 * only one map ever offered (Underworld, per specs/backend/01's curated set), a genuine user click
 * can never change the picker <select>'s value away from its own default, so a browser never fires
 * `change` — a navigate-on-change handler alone left the page a dead end. Dashboard.tsx now
 * auto-advances to the sole map's leaderboard instead. (A Playwright `selectOption` call on the
 * same single-option dropdown would have passed even with the bug present — it force-dispatches a
 * change event regardless of whether the value actually differs — which is why this needs an
 * explicit "does the page actually move on its own" assertion, not just an interaction-based one.)
 */
test("the Dashboard map picker auto-advances straight to Underworld's leaderboard", async ({ page }) => {
  await signUp(page, uniqueName('dashboarduser'));

  // Lands on "/" first (per signUp's own waitForURL), then Dashboard's effect fires once the maps
  // query resolves — assert the settled state, not the transient "/" one, since exactly how long
  // that transient state lasts is a timing detail, not the behavior under test.
  await expect(page).toHaveURL(/\/leaderboards\/\d+$/);
  await expect(page.getByRole('heading', { name: 'Leaderboard' })).toBeVisible();
});
