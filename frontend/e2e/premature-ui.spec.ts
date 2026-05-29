import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-005 — Premature workspace UI: premature staff assigns a bed from the queue.
 */
test('premature staff can admit an incoming patient to a bed via the workspace UI', async ({ page }) => {
  // Seed a PREMATURE visit + a dedicated AVAILABLE bed through the API (so the admit
  // dialog always has a selectable bed regardless of what prior runs left occupied).
  const admin = await authedContext('admin');
  const patient = await registerPatient(admin, { gender: 'FEMALE' });
  await admin.post(`${API_BASE}/visits`, { data: { patientId: patient.id, visitType: 'PREMATURE' } });
  await admin.post(`${API_BASE}/premature/beds`, {
    data: { code: `PREM-UI-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`, room: 'E2E' },
  });
  await admin.dispose();

  // Premature staff opens the workspace.
  await login(page, 'premature');
  await page.goto('/departments/premature');

  await expect(page.getByTestId('prem-incoming')).toContainText(patient.mrn);

  // Click "Assign bed" for this patient's row.
  await page.getByText(patient.mrn).locator('xpath=ancestor::li').getByRole('button').click();
  await expect(page.getByTestId('admit-dialog')).toBeVisible();
  await page.getByTestId('admit-stay-value').fill('3');
  await page.getByTestId('admit-confirm').click();

  // The patient leaves the incoming queue (now admitted).
  await expect(page.getByTestId('prem-incoming')).not.toContainText(patient.mrn, { timeout: 10_000 });
  // At least one bed shows PENDING_PAYMENT.
  await expect(page.getByTestId('prem-beds')).toContainText(/Pending payment/i);
});
