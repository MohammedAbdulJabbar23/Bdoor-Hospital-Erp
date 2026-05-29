import { Page, expect } from '@playwright/test';

export type Role =
  | 'admin'
  | 'cashier'
  | 'doctor'
  | 'dr.layla'
  | 'eco'
  | 'emergency'
  | 'lab'
  | 'nurse'
  | 'pharmacist'
  | 'radiology'
  | 'premature'
  | 'receptionist';

export async function login(page: Page, username: Role, password = username) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
}

export async function logout(page: Page) {
  await page.evaluate(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });
}
