import { test, expect } from '@playwright/test';
import { login, logout, Role } from './helpers/auth';

const ROLES: Role[] = ['admin', 'cashier', 'doctor', 'lab', 'pharmacist'];

for (const role of ROLES) {
  test(`smoke: ${role} can sign in`, async ({ page }) => {
    await login(page, role);
    await expect(page).toHaveURL(/(?!\/login)/);
    await expect(page.locator('body')).not.toContainText('Invalid username or password');
    await logout(page);
  });
}
