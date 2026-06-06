import { test, expect, Page } from '@playwright/test';
import { login, logout } from './helpers/auth';
import { authedContext, getDoctorIdByUserName, getVisitsByPatient } from './helpers/api';

/**
 * BRD REC-001 happy path through the UI:
 *   reception register patient
 *   -> walk-in appointment with doctor
 *   -> check-in (fires AppointmentCheckedInEvent -> consult-charge bridge)
 *   -> cashier approves consult payment
 *   -> doctor opens exam, fills vitals/diagnosis, forwards to Lab
 *   -> lab finalizes findings (REC-002)
 */

const stamp = Date.now();
const patient = {
  fullName: `Playwright Test ${stamp}`,
  dateOfBirth: '1990-04-12',
  mobile: `077${String(stamp).slice(-7)}`,
};

let patientId: string | null = null;
let consultVisitId: string | null = null;
let labVisitId: string | null = null;

async function dismissToast(page: Page) {
  // toaster auto-dismisses; tests don't assert on it directly
  await page.locator('[role="status"]').first().waitFor({ state: 'visible', timeout: 5_000 }).catch(() => {});
}

test.describe.configure({ mode: 'serial' });

test.describe('BRD happy path', () => {
  test('receptionist registers a new patient', async ({ page }) => {
    await login(page, 'admin');
    await page.goto('/reception/patients/new');

    await page.getByLabel(/Full name/i).fill(patient.fullName);
    await page.getByLabel(/Date of birth/i).fill(patient.dateOfBirth);
    await page.getByLabel(/Mobile number/i).fill(patient.mobile);
    await page.getByRole('button', { name: /^Save$/i }).click();

    await expect(page).toHaveURL(/\/reception\/patients\?highlight=/, { timeout: 15_000 });
    const url = new URL(page.url());
    patientId = url.searchParams.get('highlight');
    expect(patientId).toBeTruthy();

    await logout(page);
  });

  test('receptionist queues walk-in and checks the patient in', async ({ page }) => {
    expect(patientId, 'patient must be registered first').toBeTruthy();

    await login(page, 'admin');
    await page.goto('/reception/appointments');

    // Pick Dr. Kareem (General Practitioner — consultation fee 15000 IQD)
    await page.getByRole('button', { name: /Dr\. Kareem Al-Janabi/i }).click();

    // Open walk-in modal
    await page.getByRole('button', { name: /^Walk-in$/ }).click();

    // Search for the patient by partial name in the modal
    const search = page.getByPlaceholder(/Search patient by name/i);
    await search.fill(patient.fullName);
    // Click the matching result (button containing the full name)
    await page.getByRole('button', { name: new RegExp(patient.fullName) }).click();

    // Submit the walk-in queue
    await page.getByRole('button', { name: /Queue walk-in/ }).click();

    // The row appears in the appointments table — hover to reveal "Check in"
    const row = page.getByRole('row', { name: new RegExp(patient.fullName) });
    await row.waitFor({ timeout: 15_000 });
    await row.hover();
    await row.getByRole('button', { name: /^Check in$/ }).click();

    await dismissToast(page);

    // Verify a visit now exists for this patient (via API)
    const api = await authedContext('admin');
    await expect(async () => {
      const visits = await getVisitsByPatient(api, patientId!);
      expect(visits.length).toBeGreaterThan(0);
      consultVisitId = visits[0].id;
    }).toPass({ timeout: 10_000 });
    await api.dispose();

    await logout(page);
  });

  test('cashier approves the consult payment', async ({ page }) => {
    expect(consultVisitId, 'visit must be created first').toBeTruthy();

    await login(page, 'cashier');
    await page.goto('/cashier');

    // Search by patient name to surface the right row
    const search = page.getByPlaceholder(/Search by/i).first();
    await search.fill(patient.fullName);

    // Approve the pending row
    const approveBtn = page.getByRole('button', { name: /^Approve$/ }).first();
    await expect(approveBtn).toBeVisible({ timeout: 10_000 });
    await approveBtn.click();

    // Approval modal: CASH is the default; just confirm
    await page.getByRole('button', { name: /Approve & receive payment/ }).click();

    await dismissToast(page);

    // Verify visit is now IN_PROGRESS
    const api = await authedContext('admin');
    await expect(async () => {
      const res = await api.get(`http://localhost:8080/api/visits/${consultVisitId}`);
      const v = await res.json();
      expect(v.status).toBe('IN_PROGRESS');
    }).toPass({ timeout: 10_000 });
    await api.dispose();

    await logout(page);
  });

  test('doctor fills exam and forwards to laboratory', async ({ page }) => {
    expect(consultVisitId, 'consult visit must be approved first').toBeTruthy();

    await login(page, 'doctor');
    await page.goto(`/clinical/exam/${consultVisitId}`);

    // Consultation is the default tab — the chief-complaint field is visible immediately.
    await expect(page.getByPlaceholder(/Headache for 3 days/i)).toBeVisible({ timeout: 15_000 });
    await page.getByPlaceholder(/Headache for 3 days/i).fill('Chest pain, intermittent over 3 days.');

    // Vitals tab. Labels aren't htmlFor-bound; use the fixed input order inside the Vitals card
    // 0=systolic, 1=diastolic, 2=heart rate, 3=resp rate, 4=temp, 5=spo2, 6=weight, 7=height
    await page.getByTestId('exam-tab-vitals').click();
    const vitalsInputs = page.locator('input[type="number"]');
    await vitalsInputs.nth(0).fill('120');
    await vitalsInputs.nth(1).fill('80');
    await vitalsInputs.nth(2).fill('72');
    await vitalsInputs.nth(4).fill('36.8');

    // Diagnoses tab — add a custom diagnosis row, then fill its Description.
    await page.getByTestId('exam-tab-diagnoses').click();
    await page.getByRole('button', { name: /Custom diagnosis/i }).click();
    await page.getByPlaceholder('Description').first().fill('Suspected angina pectoris');

    // Orders tab — forward to lab.
    await page.getByTestId('exam-tab-orders').click();
    await page.getByRole('button', { name: /^Lab$/, exact: true }).first().click();

    // New: a "select tests" dialog opens. Skip test selection (lab will pick).
    await page.getByRole('button', { name: /Skip — let dept pick/i }).click();
    await dismissToast(page);

    // Verify a child LABORATORY visit now exists
    const api = await authedContext('admin');
    await expect(async () => {
      const res = await api.get(`http://localhost:8080/api/visits/${consultVisitId}/children`);
      const kids = await res.json();
      const lab = kids.find((c: any) => c.visitType === 'LABORATORY');
      expect(lab).toBeTruthy();
      labVisitId = lab.id;
    }).toPass({ timeout: 10_000 });
    await api.dispose();

    await logout(page);
  });

  test('lab staff sees incoming visit (forwarded from doctor)', async ({ page }) => {
    expect(labVisitId, 'lab child visit must exist').toBeTruthy();

    await login(page, 'lab');
    await page.goto('/departments/laboratory');

    // Patient name should appear in the incoming/cases list within the page
    await expect(page.getByText(new RegExp(patient.fullName)).first()).toBeVisible({ timeout: 15_000 });

    await logout(page);
  });
});
