import type { Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Backend usernames are globally unique and this app has no test-DB reset between e2e runs. */
export function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

// Two levels up from frontend/e2e/ — where docker-compose.yml lives.
const REPO_ROOT = path.dirname(path.dirname(path.dirname(fileURLToPath(import.meta.url))));

/**
 * Signup is invite-gated (see AuthService.signup / scripts/generate-signup-keys.mjs) — a real
 * deployment hands out a finite pool of single-use keys, which e2e shouldn't burn through. Mints
 * a throwaway key directly in the dev MySQL container instead, the same way
 * AbstractIntegrationTest.freshSignupKey() does for the backend suite.
 */
function mintSignupKey(): string {
  const rawKey = randomBytes(32).toString('base64url');
  const hash = createHash('sha256').update(rawKey, 'utf8').digest('hex');
  execFileSync(
    'docker',
    ['compose', 'exec', '-T', 'mysql', 'mysql', '-uroot', '-proot', 'uwtracker', '-e', `INSERT INTO signup_keys (key_hash) VALUES ('${hash}');`],
    { cwd: REPO_ROOT, stdio: ['ignore', 'ignore', 'ignore'] },
  );
  return rawKey;
}

export async function signUp(page: Page, username: string, password = 'password123'): Promise<void> {
  const signupKey = mintSignupKey();
  await page.goto('/signup');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByLabel('Confirm password').fill(password);
  await page.getByLabel('Signup key').fill(signupKey);
  await page.getByRole('button', { name: 'Sign up' }).click();
  await page.waitForURL('/');
}
