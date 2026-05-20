import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';

test('VIP toggle on patient profile flips the patient.vip flag', async ({ page }) => {
  const stamp = Date.now();
  const api = await authedContext('admin');

  // Register a non-VIP patient via API
  const res = await api.post('http://localhost:8080/api/patients', {
    data: {
      fullName: `VIP Toggle ${stamp}`,
      gender: 'FEMALE',
      dateOfBirth: '1990-03-22',
      mobileNumber: `077${String(stamp).slice(-7)}`,
      vip: false,
    },
  });
  const patient = await res.json();
  expect(patient.vip).toBe(false);

  // Open profile in UI and click Mark VIP
  await login(page, 'admin');
  await page.goto(`/patients/${patient.id}`);
  await page.getByRole('button', { name: /^Mark VIP$/ }).click();

  // After mutation, the button label should flip
  await expect(page.getByRole('button', { name: /^Remove VIP$/ })).toBeVisible({ timeout: 8_000 });

  // Verify via API
  const check = await api.get(`http://localhost:8080/api/patients/${patient.id}`);
  expect((await check.json()).vip).toBe(true);

  await api.dispose();
});
