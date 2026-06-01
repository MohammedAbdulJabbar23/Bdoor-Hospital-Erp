import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-004 — Emergency workspace UI: staff assigns a bed from the incoming queue.
 */
test('emergency staff can admit an incoming patient to a bed via the workspace UI', async ({ page }) => {
  // Seed an EMERGENCY visit + a dedicated AVAILABLE bed through the API (so the admit
  // dialog always has a selectable bed and service regardless of what prior runs left occupied).
  const admin = await authedContext('admin');
  const patient = await registerPatient(admin, { gender: 'MALE' });
  await admin.post(`${API_BASE}/visits`, { data: { patientId: patient.id, visitType: 'EMERGENCY' } });
  await admin.post(`${API_BASE}/emergency/beds`, {
    data: {
      code: `EMRG-UI-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`,
      room: 'E2E',
    },
  });
  await admin.dispose();

  // Emergency staff opens the workspace.
  await login(page, 'emergency');
  await page.goto('/departments/emergency');

  // The incoming queue shows the seeded patient by MRN.
  await expect(page.getByTestId('emerg-incoming')).toContainText(patient.mrn, { timeout: 15_000 });

  // Click "Assign bed" for this patient's row. The button testid is `admit-<visitId>` but since
  // we don't have the visitId here, target the button inside the patient's list item by MRN text.
  await page.getByText(patient.mrn).locator('xpath=ancestor::li').getByRole('button').click();
  await expect(page.getByTestId('admit-dialog')).toBeVisible();

  // Select the first non-empty service option. Wait until the select has loaded real
  // options (more than the single placeholder) before picking, so we don't race the fetch.
  const serviceSelect = page.getByTestId('admit-service-select');
  await expect(serviceSelect.locator('option')).not.toHaveCount(1, { timeout: 10_000 });
  await serviceSelect.selectOption({ index: 1 });

  // Set stay duration (bed select already defaults to first available bed).
  await page.getByTestId('admit-stay-value').fill('6');

  // Confirm admission.
  await page.getByTestId('admit-confirm').click();

  // The patient leaves the incoming queue (now admitted).
  await expect(page.getByTestId('emerg-incoming')).not.toContainText(patient.mrn, { timeout: 10_000 });

  // At least one bed tile shows PENDING_PAYMENT status label.
  await expect(page.getByTestId('emerg-beds')).toContainText(/Pending payment/i);
});
