import { test, expect } from '@playwright/test';
import { login, logout } from './helpers/auth';

/**
 * RoleGate route guards (src/shared/auth/RoleGate.tsx + App.tsx route table).
 *
 * A non-privileged role hitting a route it lacks the role for should land on the friendly
 * "Access denied" view — NOT the real page. ADMIN passes every gate, so the same route renders
 * the real page for admin.
 */

const DENIED_HEADING = /Access denied/i;
const DENIED_BODY = /don't have permission to view this page/i;

test('cashier is denied /admin/users (RoleGate blocks, Users page not rendered)', async ({ page }) => {
  await login(page, 'cashier');
  await page.goto('/admin/users');

  // The access-denied view is shown.
  await expect(page.getByText(DENIED_HEADING).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(DENIED_BODY)).toBeVisible();
  await expect(page.getByRole('link', { name: /Back to dashboard/i })).toBeVisible();

  // The real Users page content is NOT rendered. (NB: the breadcrumb derives "Users & roles"
  // from the route path even when access is denied, so the page H1 heading and the "New user"
  // button — which only the real UsersPage renders — are the reliable markers.)
  await expect(page.getByRole('button', { name: /New user/i })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Users & roles' })).toHaveCount(0);
});

test('receptionist is denied /pharmacy (RoleGate blocks)', async ({ page }) => {
  await login(page, 'receptionist');
  await page.goto('/pharmacy');

  await expect(page.getByText(DENIED_HEADING).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(DENIED_BODY)).toBeVisible();
});

test('admin can view /admin/users (RoleGate passes, real page renders)', async ({ page }) => {
  await login(page, 'admin');
  await page.goto('/admin/users');

  // The real Users page renders; the access-denied view does NOT.
  await expect(page.getByRole('heading', { name: 'Users & roles' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: /New user/i })).toBeVisible();
  await expect(page.getByText(DENIED_BODY)).toHaveCount(0);

  await logout(page);
});
