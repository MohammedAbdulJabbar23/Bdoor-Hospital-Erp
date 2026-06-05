import { test, expect } from '@playwright/test';
import { login, logout } from './helpers/auth';
import { authedContext } from './helpers/api';

const API = 'http://localhost:8080/api';

/**
 * User administration: an admin can edit an existing user's roles + account status and reset their
 * password from the Users page — the gap reported as "users/roles/permissions are not editable".
 */
test('admin edits a user (roles + status) and resets their password', async ({ page }) => {
  // Arrange: create a throwaway receptionist via the API so the test is deterministic.
  const username = `e2e${Date.now()}`;
  const admin = await authedContext('admin');
  const created = await admin.post(`${API}/users`, {
    data: { username, password: 'secret123', fullName: 'E2E Temp User', roles: ['RECEPTIONIST'] },
  });
  expect(created.ok()).toBeTruthy();
  await admin.dispose();

  await login(page, 'admin');
  await page.goto('/admin/users');
  const row = page.locator('tr', { hasText: `@${username}` });

  // Edit 1: add the DOCTOR role and disable the account.
  await page.getByTestId(`edit-user-${username}`).click();
  await expect(page.getByText('Edit user', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'DOCTOR', exact: true }).click();
  await page.getByTestId('status-disabled').click();
  await page.getByTestId('save-user').click();

  await expect(page.getByText(`User @${username} updated`)).toBeVisible({ timeout: 10_000 });
  await expect(row.getByText('DOCTOR', { exact: true })).toBeVisible();
  await expect(row.getByText('Disabled', { exact: true })).toBeVisible();

  // Edit 2: reset the password and re-enable so the user can sign in again.
  await page.getByTestId(`edit-user-${username}`).click();
  await page.getByTestId('new-password').fill('brandnew99');
  await page.getByTestId('set-password').click();
  await expect(page.getByText(`Password updated for @${username}`)).toBeVisible({ timeout: 10_000 });
  await page.getByTestId('status-active').click();
  await page.getByTestId('save-user').click();
  await expect(row.getByText('Active', { exact: true })).toBeVisible({ timeout: 10_000 });

  // The edited user can now sign in with the new password.
  await logout(page);
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill('brandnew99');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
});
