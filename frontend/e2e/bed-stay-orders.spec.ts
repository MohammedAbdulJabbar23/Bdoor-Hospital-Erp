import { test, expect, Page } from '@playwright/test';
import { login, logout } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * Bed-stay orders + referral note + results-pending gate + discharge note, driven END-TO-END THROUGH
 * THE UI for both departments. Each test seeds straight to OCCUPIED (UNDER_CARE / UNDER_TREATMENT) via
 * API, then: open workspace → click the bed (navigates to the new tabbed CASE PAGE) → on the
 * Laboratory tab, "Send to department" with a NOTE (assert it shows on the order row) → set + save a
 * discharge note on Overview → Finish treatment (results-pending dialog warns about the open order) →
 * override with a reason → Finish anyway → assert the case moves to Awaiting discharge payment.
 */

async function relogin(page: Page, role: 'premature' | 'emergency' | 'cashier') {
  if (page.url().startsWith('http')) await logout(page);
  await login(page, role);
}

async function orderLabWithNote(page: Page, note: string) {
  await page.getByTestId('case-tab-LABORATORY').click();
  await page.getByTestId('order-LABORATORY').click();
  await expect(page.getByTestId('order-dialog')).toBeVisible();
  await page.getByTestId('order-note').fill(note);
  await page.getByTestId('order-send').click();
  await expect(page.getByText(/Order sent/i)).toBeVisible({ timeout: 10_000 });
  // The order appears on the Laboratory tab with its referral note.
  await expect(page.getByTestId('order-row-LABORATORY')).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId('order-row-LABORATORY')).toContainText(note);
}

async function saveDischargeNote(page: Page, note: string) {
  await page.getByTestId('case-tab-overview').click();
  await page.getByTestId('discharge-note-input').fill(note);
  await page.getByTestId('discharge-note-save').click();
  await expect(page.getByText(/Discharge note saved/i)).toBeVisible({ timeout: 10_000 });
}

async function finishWithOverride(page: Page, reason: string) {
  await page.getByTestId('detail-finish').click();
  const dialog = page.getByTestId('results-pending-dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText(/LAB/i);
  await dialog.getByTestId('override-reason').fill(reason);
  await dialog.getByTestId('finish-override').click();
  await expect(page.getByText(/discharge payment sent to cashier/i)).toBeVisible({ timeout: 10_000 });
}

test('premature: order Lab with note → results-pending warn → override finish + discharge note', async ({ page }) => {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');

  const patient = await registerPatient(admin, { gender: 'FEMALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'PREMATURE' },
  })).json();
  const bedCode = `PREM-ORD-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, {
    data: { code: bedCode, room: 'Orders' },
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

  await relogin(page, 'premature');
  await page.goto('/departments/premature');
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();

  await expect(page).toHaveURL(/\/premature\/admissions\//, { timeout: 10_000 });
  await expect(page.getByTestId('case-status')).toContainText(/Under care/i);

  await orderLabWithNote(page, 'r/o sepsis — CBC + CRP, urgent');
  await saveDischargeNote(page, 'Stable; home on oral feeds; review in 1 week.');
  await finishWithOverride(page, 'Parents accept results will follow');

  await expect(page.getByTestId('case-status')).toContainText(/Awaiting discharge payment/i, { timeout: 10_000 });
});

test('emergency: order Lab with note → results-pending warn → override finish + discharge note', async ({ page }) => {
  const admin = await authedContext('admin');
  const emergency = await authedContext('emergency');
  const cashier = await authedContext('cashier');

  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'EMERGENCY' },
  })).json();
  const bedCode = `EMRG-ORD-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await emergency.post(`${API_BASE}/emergency/beds`, {
    data: { code: bedCode, room: 'Orders' },
  })).json();
  const services = await (await emergency.get(`${API_BASE}/emergency/services`)).json();
  await emergency.post(`${API_BASE}/emergency/cases`, {
    data: { visitId: visit.id, bedId: bed.id, serviceItemId: services[0].id, stayValue: 6, stayUnit: 'HOURS' },
  });
  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const initial = pending.content.find((p: any) => p.visitId === visit.id && p.stage === 'INITIAL');
  await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
  await expect(async () => {
    const beds = await (await emergency.get(`${API_BASE}/emergency/beds`)).json();
    expect(beds.find((b: any) => b.id === bed.id).status).toBe('OCCUPIED');
  }).toPass({ timeout: 10_000 });
  await admin.dispose(); await emergency.dispose(); await cashier.dispose();

  await relogin(page, 'emergency');
  await page.goto('/departments/emergency');
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();

  await expect(page).toHaveURL(/\/emergency\/cases\//, { timeout: 10_000 });
  await expect(page.getByTestId('case-status')).toContainText(/Under treatment/i);

  // Emergency has NO extend-stay control.
  await expect(page.getByTestId('detail-extend')).toHaveCount(0);

  await orderLabWithNote(page, 'chest film + CBC — trauma');
  await saveDischargeNote(page, 'Vitals stable; discharged with analgesia; GP follow-up.');
  await finishWithOverride(page, 'Patient accepts results will follow');

  await expect(page.getByTestId('case-status')).toContainText(/Awaiting discharge payment/i, { timeout: 10_000 });
});
