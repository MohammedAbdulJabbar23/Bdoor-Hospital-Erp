import { test, expect, Page } from '@playwright/test';
import { login, logout } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-005 — Premature admission spine driven END-TO-END THROUGH THE UI (mirror of the
 * emergency lifecycle test). Reception-seeded incoming infant → premature staff admits via the
 * dialog → cashier approves via the queue → bed Occupied → extend + finish via the bed-detail
 * drawer → cashier approves the discharge → bed freed.
 */

async function relogin(page: Page, role: 'premature' | 'cashier') {
  // logout() clears localStorage, which throws on a fresh about:blank page (no origin yet),
  // so on the first call — before any navigation — we just log in.
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
  await page.getByRole('button', { name: /Approve & receive payment/ }).click();
  await expect(page.getByRole('button', { name: /Approve & receive payment/ })).toHaveCount(0, { timeout: 15_000 });
}

async function openPrematureWorkspace(page: Page) {
  await relogin(page, 'premature');
  await page.goto('/departments/premature');
  await expect(page.getByTestId('prem-beds')).toBeVisible({ timeout: 15_000 });
}

test('full premature lifecycle through the UI: admit → pay → extend → finish → discharge → bed freed', async ({ page }) => {
  const admin = await authedContext('admin');
  const patient = await registerPatient(admin, { gender: 'FEMALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'PREMATURE' },
  })).json();
  const bedCode = `PREM-LC-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await admin.post(`${API_BASE}/premature/beds`, {
    data: { code: bedCode, room: 'Lifecycle' },
  })).json();
  await admin.dispose();

  // --- Premature staff admits the incoming infant through the dialog. ---
  await login(page, 'premature');
  await page.goto('/departments/premature');
  await expect(page.getByTestId('prem-incoming')).toContainText(patient.mrn, { timeout: 15_000 });

  await page.getByTestId(`admit-${visit.id}`).click();
  await expect(page.getByTestId('admit-dialog')).toBeVisible();
  await page.getByTestId('admit-bed-select').selectOption(bed.id); // our dedicated bed, by value
  await page.getByTestId('admit-stay-value').fill('3');
  await page.getByTestId('admit-stay-unit').selectOption('DAYS');
  await page.getByTestId('admit-confirm').click();

  await expect(page.getByTestId('prem-incoming')).not.toContainText(patient.mrn, { timeout: 10_000 });
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Pending payment/i, { timeout: 10_000 });

  // --- Cashier approves the admission (initial) payment. ---
  await cashierApprove(page, patient.fullName);

  // --- Bed is Occupied; open the drawer, extend the stay, finish treatment. ---
  await openPrematureWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Occupied/i, { timeout: 15_000 });
  await page.getByTestId(`bed-${bedCode}`).click();
  const drawer = page.getByTestId('bed-detail-panel');
  await expect(drawer).toBeVisible();
  await expect(drawer).toContainText(patient.fullName);
  await expect(drawer).toContainText(/Under care/i);

  await drawer.getByTestId('detail-extend-value').fill('1');
  await drawer.getByTestId('detail-extend-unit').selectOption('DAYS');
  await drawer.getByTestId('detail-extend').click();
  await expect(page.getByText(/Period of stay extended/i)).toBeVisible({ timeout: 10_000 });

  await drawer.getByTestId('detail-finish').click();
  await expect(page.getByText(/discharge payment sent to cashier/i)).toBeVisible({ timeout: 10_000 });

  // --- Cashier approves the discharge payment; the bed is freed. ---
  await cashierApprove(page, patient.fullName);
  await openPrematureWorkspace(page);
  await expect(page.getByTestId(`bed-status-${bedCode}`)).toContainText(/Available/i, { timeout: 15_000 });
});
