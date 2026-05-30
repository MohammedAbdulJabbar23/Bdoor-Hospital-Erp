import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-005 — Premature bed detail drawer: click an occupied bed → see patient +
 * admission details → click the patient → navigate to their full history.
 */
test('clicking an occupied bed opens the detail drawer and links to patient history', async ({ page }) => {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');

  // --- Seed an OCCUPIED bed via the API: own bed + patient + admitted + initial approved.
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

  // --- UI: open the workspace as premature staff.
  await login(page, 'premature');
  await page.goto('/departments/premature');

  // The occupied bed card shows the patient and is clickable.
  const card = page.getByTestId(`bed-${bedCode}`);
  await expect(card).toContainText(patient.mrn, { timeout: 10_000 });
  await card.click();

  // Drawer opens with patient + admission details + actions.
  const drawer = page.getByTestId('bed-detail-panel');
  await expect(drawer).toBeVisible();
  await expect(drawer).toContainText(patient.fullName);
  await expect(drawer).toContainText(patient.mrn);
  await expect(drawer.getByTestId('detail-finish')).toBeVisible(); // UNDER_CARE actions present

  // Clicking the patient forwards to their full history page.
  await drawer.getByTestId('bed-detail-patient').click();
  await expect(page).toHaveURL(new RegExp(`/patients/${patient.id}`));
  await expect(page.getByText(patient.fullName).first()).toBeVisible();
});

test('Escape closes the bed detail drawer', async ({ page }) => {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');

  const bedCode = `PREM-ESC-${Date.now()}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, {
    data: { code: bedCode, room: 'Esc Test' },
  })).json();
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'PREMATURE' },
  })).json();
  await premature.post(`${API_BASE}/premature/admissions`, {
    data: { visitId: visit.id, bedId: bed.id, stayValue: 1, stayUnit: 'DAYS' },
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
  await page.getByTestId(`bed-${bedCode}`).click();
  await expect(page.getByTestId('bed-detail-panel')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('bed-detail-panel')).toHaveCount(0);
});
