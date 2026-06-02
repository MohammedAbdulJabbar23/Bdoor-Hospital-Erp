import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * Premature bed-dashboard status filter (src/features/beds/BedStatusFilter.tsx +
 * PrematureWorkspacePage). Seeds one OCCUPIED bed and one AVAILABLE bed via the API, then drives
 * the segmented status filter through the UI and asserts which bed tiles are visible.
 *
 * Filter testids: bed-filter-all | bed-filter-available | bed-filter-pending | bed-filter-occupied.
 * Bed tiles: bed-<code>; status pill: bed-status-<code>.
 */
test('bed-dashboard status filter shows/hides occupied vs available tiles', async ({ page }) => {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');

  // Bed codes are validated to ≤30 chars; keep the unique suffix compact (base-36).
  const stamp = `${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`;
  const occupiedCode = `BF-OCC-${stamp}`;
  const availableCode = `BF-AVL-${stamp}`;

  // --- Occupied bed: own bed + infant + PREMATURE visit + admission + approve INITIAL → OCCUPIED.
  const occBed = await (await premature.post(`${API_BASE}/premature/beds`, {
    data: { code: occupiedCode, room: 'BedFilter-Occ' },
  })).json();
  const patient = await registerPatient(admin, { gender: 'FEMALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'PREMATURE' },
  })).json();
  await premature.post(`${API_BASE}/premature/admissions`, {
    data: { visitId: visit.id, bedId: occBed.id, stayValue: 3, stayUnit: 'DAYS' },
  });
  // The INITIAL payment is created by the admission; poll until it's queryable, then approve.
  let initialId: string | undefined;
  await expect(async () => {
    const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
    const initial = pending.content.find((p: any) => p.visitId === visit.id && p.stage === 'INITIAL');
    expect(initial?.id).toBeTruthy();
    initialId = initial.id;
  }).toPass({ timeout: 10_000 });
  await cashier.post(`${API_BASE}/payments/${initialId}/approve`, { data: { paymentMethod: 'CASH' } });
  await expect(async () => {
    const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
    expect(beds.find((b: any) => b.id === occBed.id).status).toBe('OCCUPIED');
  }).toPass({ timeout: 10_000 });

  // --- A second, dedicated AVAILABLE bed.
  await premature.post(`${API_BASE}/premature/beds`, {
    data: { code: availableCode, room: 'BedFilter-Avl' },
  });

  await admin.dispose(); await premature.dispose(); await cashier.dispose();

  // --- UI.
  await login(page, 'premature');
  await page.goto('/departments/premature');

  const occupiedTile = page.getByTestId(`bed-${occupiedCode}`);
  const availableTile = page.getByTestId(`bed-${availableCode}`);

  // Default filter = ALL: both tiles visible.
  await expect(page.getByTestId('bed-filter-all')).toHaveAttribute('aria-pressed', 'true', { timeout: 15_000 });
  await expect(occupiedTile).toBeVisible({ timeout: 15_000 });
  await expect(availableTile).toBeVisible();
  await expect(page.getByTestId(`bed-status-${occupiedCode}`)).toContainText(/Occupied/i);
  await expect(page.getByTestId(`bed-status-${availableCode}`)).toContainText(/Available/i);

  // Occupied filter: occupied tile visible, available tile gone.
  await page.getByTestId('bed-filter-occupied').click();
  await expect(occupiedTile).toBeVisible();
  await expect(availableTile).toHaveCount(0);

  // Available filter: available tile visible, occupied tile gone.
  await page.getByTestId('bed-filter-available').click();
  await expect(availableTile).toBeVisible();
  await expect(occupiedTile).toHaveCount(0);
});
