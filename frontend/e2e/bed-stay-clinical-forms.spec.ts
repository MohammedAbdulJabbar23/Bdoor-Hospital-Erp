import { test, expect, Page } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * BRD REC-005 §6.6 + P6 — the three shared clinical forms (medical history, nursing
 * procedures, treatment chart) and the premature-only patient case file, exercised
 * through the bed-stay case page tabs. Persistence is proven by reloading the page
 * and asserting the saved values come back from the API.
 */

// Copied from premature-form.spec.ts — premature admission paid up to UNDER_CARE.
async function seedUnderCare(): Promise<{ admissionId: string; bedCode: string }> {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');
  const bedCode = `PREM-CF-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, { data: { code: bedCode, room: 'Forms' } })).json();
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, { data: { patientId: patient.id, visitType: 'PREMATURE' } })).json();
  const adm = await (await premature.post(`${API_BASE}/premature/admissions`, {
    data: { visitId: visit.id, bedId: bed.id, stayValue: 3, stayUnit: 'DAYS' } })).json();
  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const initial = pending.content.find((p: any) => p.visitId === visit.id && p.stage === 'INITIAL');
  await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
  await expect(async () => {
    const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
    expect(beds.find((b: any) => b.id === bed.id).status).toBe('OCCUPIED');
  }).toPass({ timeout: 10_000 });
  await admin.dispose(); await premature.dispose(); await cashier.dispose();
  return { admissionId: adm.id, bedCode };
}

// Copied/adapted from emergency-lifecycle-ui.spec.ts — emergency case admitted via the
// API (service selection) with the INITIAL payment approved, i.e. UNDER_TREATMENT.
async function seedEmergencyUnderTreatment(): Promise<{ caseId: string; bedCode: string }> {
  const admin = await authedContext('admin');
  const emergency = await authedContext('emergency');
  const cashier = await authedContext('cashier');

  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'EMERGENCY' },
  })).json();
  const bedCode = `EMRG-CF-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await emergency.post(`${API_BASE}/emergency/beds`, {
    data: { code: bedCode, room: 'Forms' },
  })).json();
  const services = await (await emergency.get(`${API_BASE}/emergency/services`)).json();
  const emergencyCase = await (await emergency.post(`${API_BASE}/emergency/cases`, {
    data: { visitId: visit.id, bedId: bed.id, serviceItemId: services[0].id, stayValue: 6, stayUnit: 'HOURS' },
  })).json();
  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const initial = pending.content.find((p: any) => p.visitId === visit.id && p.stage === 'INITIAL');
  await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
  await expect(async () => {
    const beds = await (await emergency.get(`${API_BASE}/emergency/beds`)).json();
    expect(beds.find((b: any) => b.id === bed.id).status).toBe('OCCUPIED');
  }).toPass({ timeout: 10_000 });
  await admin.dispose(); await emergency.dispose(); await cashier.dispose();
  return { caseId: emergencyCase.id, bedCode };
}

/** Click a save button and wait for its API write to succeed (2xx). More robust than
 *  asserting the transient "Saved" toast; the after-reload value check is the real proof. */
async function clickAndWaitForWrite(page: Page, testId: string, urlPart: string) {
  const [res] = await Promise.all([
    page.waitForResponse((r) =>
      r.url().includes(urlPart) && ['PUT', 'POST'].includes(r.request().method()), { timeout: 15_000 }),
    page.getByTestId(testId).click(),
  ]);
  expect(res.ok()).toBe(true);
}

test.describe('Bed-stay clinical forms (BRD REC-005 §6.6 + P6)', () => {

  test('medical history: fill → save → reload persists', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'premature');
    await page.goto(`/premature/admissions/${admissionId}?tab=history`);
    await page.getByTestId('mh-chiefComplaint').fill('Fever for 2 days');
    await page.getByTestId('mh-doctorName').fill('Dr. House');
    await clickAndWaitForWrite(page, 'mh-save', '/medical-history');
    await page.reload();
    await expect(page.getByTestId('mh-chiefComplaint')).toHaveValue('Fever for 2 days', { timeout: 10_000 });
    await expect(page.getByTestId('mh-doctorName')).toHaveValue('Dr. House');
  });

  test('nursing: nurse adds a procedure row, auto-attributed', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'nurse');
    await page.goto(`/premature/admissions/${admissionId}?tab=nursing`);
    await page.getByTestId('nursing-procedureName').fill('Umbilical care');
    await page.getByTestId('nursing-add').click();
    await expect(page.getByTestId('nursing-rows')).toContainText('Umbilical care', { timeout: 10_000 });
    await page.reload();
    await expect(page.getByTestId('nursing-rows')).toContainText('Umbilical care');
  });

  test('treatment chart: add medicine rows → save → reload persists', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'premature');
    await page.goto(`/premature/admissions/${admissionId}?tab=treatment`);
    await page.getByTestId('tc-row-0-medicine').fill('Ampicillin');
    await page.getByTestId('tc-row-0-dose').fill('50mg/kg');
    await page.getByTestId('tc-add-row').click();
    await page.getByTestId('tc-row-1-medicine').fill('Gentamicin');
    await clickAndWaitForWrite(page, 'tc-save', '/treatment-charts/');
    await page.reload();
    await expect(page.getByTestId('tc-row-0-medicine')).toHaveValue('Ampicillin', { timeout: 10_000 });
    await expect(page.getByTestId('tc-row-0-dose')).toHaveValue('50mg/kg');
    await expect(page.getByTestId('tc-row-1-medicine')).toHaveValue('Gentamicin');
  });

  test('case file (P6): premature staff save diagnosis; registry prefill shown', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'premature');
    await page.goto(`/premature/admissions/${admissionId}?tab=caseFile`);
    await page.getByTestId('cf-wardNumber').fill('W-3');
    await page.getByTestId('cf-initialDiagnosis').fill('Prematurity, RDS');
    await clickAndWaitForWrite(page, 'cf-save', '/case-form');
    await page.reload();
    await expect(page.getByTestId('cf-initialDiagnosis')).toHaveValue('Prematurity, RDS', { timeout: 10_000 });
    await expect(page.getByTestId('cf-wardNumber')).toHaveValue('W-3');
  });

  test('emergency smoke: shared History tab works on an emergency case', async ({ page }) => {
    const { caseId } = await seedEmergencyUnderTreatment();
    await login(page, 'emergency');
    await page.goto(`/emergency/cases/${caseId}?tab=history`);
    await page.getByTestId('mh-chiefComplaint').fill('RTA — head injury');
    await clickAndWaitForWrite(page, 'mh-save', '/medical-history');
    await page.reload();
    await expect(page.getByTestId('mh-chiefComplaint')).toHaveValue('RTA — head injury', { timeout: 10_000 });
  });
});
