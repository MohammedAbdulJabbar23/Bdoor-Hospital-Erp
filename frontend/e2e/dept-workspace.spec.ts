import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, startWalkInAndCheckIn, approvePendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * Department workspace (src/features/departments/DepartmentWorkspace.tsx) active-filter +
 * pagination indicator. Drives a forwarded LABORATORY case all the way to a TERMINAL state
 * (RETURNED) entirely via the API (same calls as brd-rec-002-laboratory): walk-in consult →
 * approve INITIAL → doctor forwards with a lab test → approve REFERRAL → upload findings →
 * finalize (forwarded case RETURNS to the parent).
 *
 * The default Active-only view (dept-filter-active pressed) must NOT show the RETURNED case;
 * switching to All (dept-filter-all) must surface it. The pager range indicator (dept-page-range)
 * is present whenever there are cases spanning >1 page.
 */
test('department workspace hides finalized (RETURNED) cases under Active, shows them under All', async ({ page }) => {
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const doctor = await authedContext('doctor');
  const lab = await authedContext('lab');

  // --- Walk-in consult → INITIAL approved.
  const patient = await registerPatient(admin);
  const doc = await findDoctor(admin, 'Kareem');
  const { visitId: consultId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
  await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

  // --- Forward to LAB with a single lab test (opens the case) → REFERRAL approved.
  const labItems = await (await admin.get(`${API_BASE}/catalogue/items?category=LAB&activeOnly=true`)).json();
  const [s1] = labItems.slice(0, 1);
  const fr = await (await doctor.post(`${API_BASE}/visits/${consultId}/forward-with-tests`, {
    data: { targetType: 'LABORATORY', services: [{ serviceItemId: s1.id, quantity: 1 }] },
  })).json();
  await approvePendingPaymentFor(cashier, patient.mrn, 'REFERRAL');

  // --- Upload findings for all services, then finalize → RETURNED (forwarded case).
  await lab.post(`${API_BASE}/dept-cases/${fr.caseId}/findings`, {
    data: { serviceItemId: s1.id, numericValue: 12.4, unit: 'g/dL', referenceRange: '12-16', flag: 'NORMAL', comments: 'within range' },
  });
  const fin = await (await lab.post(`${API_BASE}/dept-cases/${fr.caseId}/finalize`)).json();
  expect(fin.status).toBe('RETURNED');

  // The child lab visit display id is what the dept table shows in the "Visit" column.
  const finalizedCase = await (await lab.get(`${API_BASE}/dept-cases/${fr.caseId}`)).json();
  const childDisplayId: string = finalizedCase.visitDisplayId;
  expect(childDisplayId).toBeTruthy();

  await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await lab.dispose();

  // --- UI: laboratory workspace as lab staff.
  await login(page, 'lab');
  await page.goto('/departments/laboratory');

  // Default = Active only.
  await expect(page.getByTestId('dept-filter-active')).toHaveAttribute('aria-pressed', 'true', { timeout: 15_000 });

  // The RETURNED case (terminal) must NOT appear under Active-only — locate by its display id.
  const activeRow = page.getByText(childDisplayId, { exact: true });
  await expect(activeRow).toHaveCount(0);

  // Switch to All → the RETURNED case now appears (near-empty clean DB ⇒ page 1).
  await page.getByTestId('dept-filter-all').click();
  await expect(page.getByTestId('dept-filter-all')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText(childDisplayId, { exact: true })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(patient.fullName).first()).toBeVisible();

  // The pager range indicator is only rendered when cases span more than one page. When present,
  // it reads "from–to of total"; assert that shape rather than requiring it to exist.
  const pageRange = page.getByTestId('dept-page-range');
  if (await pageRange.count()) {
    await expect(pageRange).toContainText(/\d+\D+\d+\D+of\D+\d+/);
  }
});
