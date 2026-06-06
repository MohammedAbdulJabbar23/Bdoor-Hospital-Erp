import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-005 — Premature: clicking an occupied bed opens the full tabbed CASE PAGE
 * (the old drawer was removed) showing the patient + status + tabs, and the patient header
 * links to their full history.
 */
test('clicking an occupied bed opens the case page and links to patient history', async ({ page }) => {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');

  const bedCode = `PREM-DTL-${Date.now()}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, {
    data: { code: bedCode, room: 'Detail Test' },
  })).json();

  const patient = await registerPatient(admin, { gender: 'FEMALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'PREMATURE' },
  })).json();
  await premature.post(`${API_BASE}/premature/admissions`, {
    data: { visitId: visit.id, bedId: bed.id, stayValue: 3, stayUnit: 'DAYS' },
  });
  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const initial = pending.content.find((p: any) => p.visitId === visit.id && p.stage === 'INITIAL');
  await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });

  await expect(async () => {
    const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
    expect(beds.find((b: any) => b.id === bed.id).status).toBe('OCCUPIED');
  }).toPass({ timeout: 10_000 });

  await admin.dispose(); await premature.dispose(); await cashier.dispose();

  await login(page, 'premature');
  await page.goto('/departments/premature');

  const card = page.getByTestId(`bed-${bedCode}`);
  await expect(card).toContainText(patient.mrn, { timeout: 10_000 });
  await card.click();

  // Navigates to the full case page (no drawer), with the patient, status and tabs.
  await expect(page).toHaveURL(/\/premature\/admissions\//, { timeout: 10_000 });
  await expect(page.getByTestId('case-patient')).toContainText(patient.fullName);
  await expect(page.getByTestId('case-status')).toContainText(/Under care/i);
  await expect(page.getByTestId('case-tab-LABORATORY')).toBeVisible();

  // Clicking the patient forwards to their full history page.
  await page.getByTestId('case-patient').click();
  await expect(page).toHaveURL(new RegExp(`/patients/${patient.id}`));
  await expect(page.getByText(patient.fullName).first()).toBeVisible();
});
