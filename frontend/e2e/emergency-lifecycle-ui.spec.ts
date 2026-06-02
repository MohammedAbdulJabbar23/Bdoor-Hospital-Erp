import { test, expect, Page } from '@playwright/test';
import { login, logout } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-004 — Emergency admission spine driven END-TO-END THROUGH THE UI.
 *
 * The existing brd-rec-004 spec exercises the same state machine at the API level. These tests
 * click the entire workflow through the real screens: reception-seeded incoming patient → emergency
 * staff admits via the dialog → cashier approves via the queue → bed becomes Occupied → staff extends
 * and finishes treatment via the bed-detail drawer → cashier approves the discharge → bed is freed.
 * The second test drives the P12b discharge-rejection-and-reissue loop through the UI (which also
 * covers the cashier REJECT path through the screens).
 */

// ---- UI helpers (cashier queue uses text/role selectors; no testids) ---------------------------

// Switch roles cleanly. logout() clears localStorage, which throws on a fresh about:blank page
// (no document origin yet), so on the first call — before any navigation — we just log in.
async function relogin(page: Page, role: 'emergency' | 'cashier') {
  if (page.url().startsWith('http')) await logout(page);
  await login(page, role);
}

async function cashierApprove(page: Page, patientName: string) {
  await relogin(page, 'cashier');
  await page.goto('/cashier');
  await page.getByPlaceholder(/Search by/i).first().fill(patientName);
  const approve = page.getByRole('button', { name: /^Approve$/ }).first();
  await expect(approve).toBeVisible({ timeout: 15_000 });
  await approve.click();
  // Decision dialog: CASH is default; confirm.
  await page.getByRole('button', { name: /Approve & receive payment/ }).click();
  // Dialog closes on success.
  await expect(page.getByRole('button', { name: /Approve & receive payment/ })).toHaveCount(0, { timeout: 15_000 });
}

async function cashierReject(page: Page, patientName: string, reason: string) {
  await relogin(page, 'cashier');
  await page.goto('/cashier');
  await page.getByPlaceholder(/Search by/i).first().fill(patientName);
  const reject = page.getByRole('button', { name: /^Reject$/ }).first();
  await expect(reject).toBeVisible({ timeout: 15_000 });
  await reject.click();
  // Decision dialog (reject mode): fill the reason, confirm.
  const dialog = page.locator('div.max-w-lg');
  await dialog.getByPlaceholder(/patient declined/i).fill(reason);
  await dialog.getByRole('button', { name: 'Reject', exact: true }).click();
  await expect(dialog).toHaveCount(0, { timeout: 15_000 });
}

async function openEmergencyWorkspace(page: Page) {
  await relogin(page, 'emergency');
  await page.goto('/departments/emergency');
  await expect(page.getByTestId('emerg-beds')).toBeVisible({ timeout: 15_000 });
}

// ------------------------------------------------------------------------------------------------

test('full emergency lifecycle through the UI: admit → pay → extend → finish → discharge → bed freed', async ({ page }) => {
  // Seed (reception side) via API: an EMERGENCY visit waiting for a bed + a dedicated AVAILABLE bed.
  const admin = await authedContext('admin');
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'EMERGENCY' },
  })).json();
  const bedCode = `EMRG-LC-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await admin.post(`${API_BASE}/emergency/beds`, {
    data: { code: bedCode, room: 'Lifecycle' },
  })).json();
  await admin.dispose();

  // --- Emergency staff admits the incoming patient through the dialog. ---
  await login(page, 'emergency');
  await page.goto('/departments/emergency');
  await expect(page.getByTestId('emerg-incoming')).toContainText(patient.mrn, { timeout: 15_000 });

  await page.getByTestId(`admit-${visit.id}`).click();
  await expect(page.getByTestId('admit-dialog')).toBeVisible();

  const serviceSelect = page.getByTestId('admit-service-select');
  await expect(serviceSelect.locator('option')).not.toHaveCount(1, { timeout: 10_000 });
  await serviceSelect.selectOption({ index: 1 });
  await page.getByTestId('admit-bed-select').selectOption(bed.id); // our dedicated bed, by value
  await page.getByTestId('admit-stay-value').fill('6');
  await page.getByTestId('admit-stay-unit').selectOption('HOURS');
  await page.getByTestId('admit-confirm').click();

  // Patient leaves the queue; the bed is now Pending payment.
  await expect(page.getByTestId('emerg-incoming')).not.toContainText(patient.mrn, { timeout: 10_000 });
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Pending payment/i, { timeout: 10_000 });

  // --- Cashier approves the initial payment through the queue UI. ---
  await cashierApprove(page, patient.fullName);

  // --- Back in the workspace: the bed is Occupied; open the drawer. ---
  await openEmergencyWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();
  const drawer = page.getByTestId('bed-detail-panel');
  await expect(drawer).toBeVisible();
  await expect(drawer).toContainText(patient.fullName);
  await expect(drawer).toContainText(/Under treatment/i);

  // --- Extend the stay via the drawer, then finish treatment. ---
  await drawer.getByTestId('detail-extend-value').fill('1');
  await drawer.getByTestId('detail-extend-unit').selectOption('DAYS');
  await drawer.getByTestId('detail-extend').click();
  await expect(page.getByText(/Period of stay extended/i)).toBeVisible({ timeout: 10_000 });

  await drawer.getByTestId('detail-finish').click();
  await expect(page.getByText(/discharge payment sent to cashier/i)).toBeVisible({ timeout: 10_000 });

  // --- Cashier approves the discharge (FINAL) payment through the UI. ---
  await cashierApprove(page, patient.fullName);

  // --- The bed is freed back to Available. ---
  await openEmergencyWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Available/i, { timeout: 15_000 });
});

test('emergency discharge rejected then re-issued through the UI (P12b)', async ({ page }) => {
  // Seed straight to OCCUPIED via API so the UI test focuses on the finish → reject → reissue loop.
  const admin = await authedContext('admin');
  const emergency = await authedContext('emergency');
  const cashier = await authedContext('cashier');

  const patient = await registerPatient(admin, { gender: 'FEMALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'EMERGENCY' },
  })).json();
  const bedCode = `EMRG-P12B-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await emergency.post(`${API_BASE}/emergency/beds`, {
    data: { code: bedCode, room: 'P12b' },
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

  // --- Finish treatment via the drawer. ---
  await openEmergencyWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();
  let drawer = page.getByTestId('bed-detail-panel');
  await expect(drawer).toBeVisible();
  await drawer.getByTestId('detail-finish').click();
  await expect(page.getByText(/discharge payment sent to cashier/i)).toBeVisible({ timeout: 10_000 });

  // --- Cashier REJECTS the discharge payment with a reason (through the UI). ---
  await cashierReject(page, patient.fullName, 'patient will pay tomorrow');

  // --- Bed stays Occupied; the drawer now offers Re-issue. Re-issue the discharge payment. ---
  await openEmergencyWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();
  drawer = page.getByTestId('bed-detail-panel');
  await expect(drawer).toBeVisible();
  await expect(drawer).toContainText(/Awaiting discharge payment/i);
  await drawer.getByTestId('detail-reissue').click();
  await expect(page.getByText(/Discharge payment re-issued/i)).toBeVisible({ timeout: 10_000 });

  // --- Cashier approves the re-issued payment; the bed is freed. ---
  await cashierApprove(page, patient.fullName);
  await openEmergencyWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Available/i, { timeout: 15_000 });
});
