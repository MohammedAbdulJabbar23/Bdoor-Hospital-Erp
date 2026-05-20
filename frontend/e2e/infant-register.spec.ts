import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';

test('receptionist can register an infant via the new UI', async ({ page }) => {
  const stamp = Date.now();
  await login(page, 'admin');
  await page.goto('/reception/patients/new-infant');

  await expect(page.getByRole('heading', { name: /Register infant/i })).toBeVisible();

  await page.getByLabel(/^Full name/i).fill(`Baby Test ${stamp}`);
  await page.getByLabel(/Date of birth/i).fill('2026-05-10');
  await page.getByLabel(/^Time of birth/i).fill('08:30');
  // Mother name is required by backend if no motherPatientId is provided
  await page.getByLabel(/Mother's name/i).fill(`Fatima Mother ${stamp}`);
  await page.getByLabel(/Guardian name/i).fill('Fatima Al-Test');
  await page.getByLabel(/Apgar \(1 min\)/i).fill('8');
  await page.getByLabel(/Apgar \(5 min\)/i).fill('9');

  await page.getByRole('button', { name: /^Register infant$/ }).click();

  await expect(page).toHaveURL(/\/reception\/patients\?highlight=/, { timeout: 15_000 });
});
