import { test, expect, Page } from '@playwright/test';
import { login, logout } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * Bed-stay orders + results-pending gate + discharge note, driven END-TO-END THROUGH THE UI for both
 * departments. Each test seeds straight to OCCUPIED (UNDER_CARE / UNDER_TREATMENT) via API, then:
 * open workspace → open bed drawer → order a LABORATORY workup (assert it shows in the live order
 * list) → set + save a discharge note (assert the saved toast) → Finish treatment (assert the
 * results-pending dialog appears listing the open order) → fill an override reason → Finish anyway →
 * assert success (case moves to awaiting discharge while the bed stays Occupied).
 */

async function relogin(page: Page, role: 'premature' | 'emergency' | 'cashier') {
  // logout() clears localStorage, which throws on a fresh about:blank page (no origin yet),
  // so on the first call — before any navigation — we just log in.
  if (page.url().startsWith('http')) await logout(page);
  await login(page, role);
}

async function openPrematureWorkspace(page: Page) {
  await relogin(page, 'premature');
  await page.goto('/departments/premature');
  await expect(page.getByTestId('prem-beds')).toBeVisible({ timeout: 15_000 });
}

async function openEmergencyWorkspace(page: Page) {
  await relogin(page, 'emergency');
  await page.goto('/departments/emergency');
  await expect(page.getByTestId('emerg-beds')).toBeVisible({ timeout: 15_000 });
}

test('premature: order Lab → results-pending warn → override finish + discharge note', async ({ page }) => {
  // Seed straight to UNDER_CARE via API.
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

  // --- Open the workspace and the bed drawer. ---
  await openPrematureWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();
  const drawer = page.getByTestId('bed-detail-panel');
  await expect(drawer).toBeVisible();
  await expect(drawer).toContainText(/Under care/i);

  // --- Order a LABORATORY workup; it appears in the live order list. ---
  await drawer.getByTestId('order-LABORATORY').click();
  await expect(page.getByText(/Order sent/i)).toBeVisible({ timeout: 10_000 });
  const orderList = drawer.getByTestId('order-list');
  await expect(orderList).toContainText(/LABORATORY/i, { timeout: 10_000 });

  // --- Set + save a discharge note. ---
  await drawer.getByTestId('discharge-note-input').fill('Stable; home on oral feeds; review in 1 week.');
  await drawer.getByTestId('discharge-note-save').click();
  await expect(page.getByText(/Discharge note saved/i)).toBeVisible({ timeout: 10_000 });

  // --- Finish treatment → results-pending dialog warns about the open order. ---
  await drawer.getByTestId('detail-finish').click();
  const dialog = page.getByTestId('results-pending-dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText(/LABORATORY/i);

  // --- Override with a reason → Finish anyway succeeds. ---
  await dialog.getByTestId('override-reason').fill('Parents accept results will follow');
  await dialog.getByTestId('finish-override').click();
  await expect(page.getByText(/discharge payment sent to cashier/i)).toBeVisible({ timeout: 10_000 });

  // --- Bed stays Occupied while awaiting the discharge payment; the (still-open) drawer reflects it. ---
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await expect(drawer).toContainText(/Awaiting discharge payment/i, { timeout: 10_000 });
});

test('emergency: order Lab → results-pending warn → override finish + discharge note', async ({ page }) => {
  // Seed straight to UNDER_TREATMENT via API.
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

  // --- Open the workspace and the bed drawer. ---
  await openEmergencyWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();
  const drawer = page.getByTestId('bed-detail-panel');
  await expect(drawer).toBeVisible();
  await expect(drawer).toContainText(/Under treatment/i);

  // --- Order a LABORATORY workup; it appears in the live order list. ---
  await drawer.getByTestId('order-LABORATORY').click();
  await expect(page.getByText(/Order sent/i)).toBeVisible({ timeout: 10_000 });
  const orderList = drawer.getByTestId('order-list');
  await expect(orderList).toContainText(/LABORATORY/i, { timeout: 10_000 });

  // --- Set + save a discharge note. ---
  await drawer.getByTestId('discharge-note-input').fill('Vitals stable; discharged with analgesia; GP follow-up.');
  await drawer.getByTestId('discharge-note-save').click();
  await expect(page.getByText(/Discharge note saved/i)).toBeVisible({ timeout: 10_000 });

  // --- Finish treatment → results-pending dialog warns about the open order. ---
  await drawer.getByTestId('detail-finish').click();
  const dialog = page.getByTestId('results-pending-dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText(/LABORATORY/i);

  // --- Override with a reason → Finish anyway succeeds. ---
  await dialog.getByTestId('override-reason').fill('Patient accepts results will follow');
  await dialog.getByTestId('finish-override').click();
  await expect(page.getByText(/discharge payment sent to cashier/i)).toBeVisible({ timeout: 10_000 });

  // --- Bed stays Occupied while awaiting the discharge payment; the (still-open) drawer reflects it. ---
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await expect(drawer).toContainText(/Awaiting discharge payment/i, { timeout: 10_000 });
});
