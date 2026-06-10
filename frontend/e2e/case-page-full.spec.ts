import { test, expect, Page } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * The FULL bed-stay case page, end-to-end in one journey: every tab (overview, orders,
 * clinical, history, nursing, treatment, case file, billing, timeline) and every action
 * (order with note, form saves, signature, extend-free finish with results-pending
 * override, discharge note, cashier final approval) — then the closed-state read-only
 * verification. A condensed emergency counterpart proves the shared tabs there too.
 */

// DevDataSeeder: the `premature` login is "Dr. Noor Al-Rubaie" — used for nurse auto-attribution.
const PREMATURE_FULL_NAME = 'Dr. Noor Al-Rubaie';

// Copied from bed-stay-clinical-forms.spec.ts — premature admission paid up to UNDER_CARE.
// Also returns the visit + patient so the journey can assert identity and approve the FINAL payment.
async function seedUnderCare(): Promise<{ admissionId: string; bedCode: string; visitId: string; patient: any }> {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');
  const bedCode = `PREM-FULL-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, { data: { code: bedCode, room: 'Full' } })).json();
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
  return { admissionId: adm.id, bedCode, visitId: visit.id, patient };
}

// Copied from bed-stay-clinical-forms.spec.ts — emergency case paid up to UNDER_TREATMENT.
async function seedEmergencyUnderTreatment(): Promise<{ caseId: string; bedCode: string }> {
  const admin = await authedContext('admin');
  const emergency = await authedContext('emergency');
  const cashier = await authedContext('cashier');

  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'EMERGENCY' },
  })).json();
  const bedCode = `EMRG-FULL-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await emergency.post(`${API_BASE}/emergency/beds`, {
    data: { code: bedCode, room: 'Full' },
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

/** Click a button and wait for its API write (POST/PUT) to succeed. */
async function clickAndWaitForWrite(page: Page, testId: string, urlPart: string) {
  const [res] = await Promise.all([
    page.waitForResponse((r) =>
      r.url().includes(urlPart) && ['PUT', 'POST'].includes(r.request().method()), { timeout: 15_000 }),
    page.getByTestId(testId).click(),
  ]);
  expect(res.ok()).toBe(true);
}

// Copied from bed-stay-orders.spec.ts — place an order with a referral note through the UI.
async function orderWithNote(page: Page, target: 'LABORATORY' | 'RADIOLOGY' | 'ECO', note: string) {
  await page.getByTestId(`case-tab-${target}`).click();
  await page.getByTestId(`order-${target}`).click();
  await expect(page.getByTestId('order-dialog')).toBeVisible();
  await page.getByTestId('order-note').fill(note);
  await page.getByTestId('order-send').click();
  await expect(page.getByText(/Order sent/i)).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId(`order-row-${target}`)).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId(`order-row-${target}`)).toContainText(note);
}

// Copied from premature-form.spec.ts — fill all BRD-mandatory Premature Form fields and save.
async function fillMandatoryForm(page: Page) {
  await page.getByTestId('case-tab-clinical').click();
  await page.getByTestId('clinical-form').click();
  await page.getByTestId('f-ageText').fill('12 days');
  await page.getByTestId('f-birthWeightKg').fill('1.2');
  await page.getByTestId('f-currentWeightKg').fill('1.45');
  await page.getByTestId('f-gaWeeks').fill('32');
  await page.getByTestId('f-gaDays').fill('4');
  await page.getByTestId('f-correctedGaWeeks').fill('34');
  await page.getByTestId('f-correctedGaDays').fill('1');
  await page.getByTestId('f-lengthCm').fill('42');
  await page.getByTestId('f-ofcCm').fill('30');
  await page.getByTestId('f-feedingType').fill('EBM');
  await page.getByTestId('save-form').click();
  // The signature pads render only once the form exists.
  await expect(page.getByTestId('canvas-RESIDENT')).toBeVisible({ timeout: 10_000 });
}

/** Draw a stroke across a SignaturePad canvas (mirrors premature-form.spec.ts). */
async function drawOnCanvas(page: Page, canvasTestId: string) {
  const canvas = page.getByTestId(canvasTestId);
  await expect(canvas).toBeVisible({ timeout: 10_000 });
  const box = await canvas.boundingBox();
  if (!box) throw new Error('no canvas box');
  await page.mouse.move(box.x + 20, box.y + 20);
  await page.mouse.down();
  await page.mouse.move(box.x + 120, box.y + 60);
  await page.mouse.move(box.x + 220, box.y + 30);
  await page.mouse.up();
}

test('premature: full case-page journey — every tab, every action, then closed read-only', async ({ page }) => {
  test.setTimeout(180_000);
  const { admissionId, bedCode, visitId, patient } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}`);

  // --- 1. Overview: identity + status. ---
  await expect(page.getByTestId('case-patient')).toContainText(patient.fullName, { timeout: 15_000 });
  await expect(page.getByTestId('case-patient')).toContainText(patient.mrn);
  await expect(page.getByText(bedCode).first()).toBeVisible();
  await expect(page.getByTestId('case-status')).toContainText(/Under care/i);

  // --- 2. Laboratory order with a referral note. ---
  const labNote = 'r/o sepsis — CBC + CRP, urgent';
  await orderWithNote(page, 'LABORATORY', labNote);

  // --- 3. Clinical tab: mandatory premature form → signature pads appear. ---
  await fillMandatoryForm(page);
  await expect(page.getByTestId('canvas-CLINICAL_PHARMACY')).toBeVisible();
  await expect(page.getByTestId('canvas-SENIOR_RESIDENT')).toBeVisible();

  // --- 4. History tab: save fields, then draw + save the SPECIALIST signature. ---
  await page.getByTestId('case-tab-history').click();
  await page.getByTestId('mh-chiefComplaint').fill('Fever for 2 days');
  await page.getByTestId('mh-doctorName').fill('Dr. House');
  await clickAndWaitForWrite(page, 'mh-save', '/medical-history');

  await page.getByTestId('signer-SPECIALIST').fill('Dr. Sami Specialist');
  await drawOnCanvas(page, 'canvas-SPECIALIST');
  const [sigRes] = await Promise.all([
    page.waitForResponse((r) =>
      r.url().includes('/medical-history/signatures/SPECIALIST') && r.request().method() === 'POST',
      { timeout: 15_000 }),
    page.getByTestId('save-sign-SPECIALIST').click(),
  ]);
  expect(sigRes.ok()).toBe(true);

  await page.reload();
  await expect(page.getByTestId('mh-chiefComplaint')).toHaveValue('Fever for 2 days', { timeout: 10_000 });
  await expect(page.getByTestId('signature-SPECIALIST').locator('img')).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId('signature-SPECIALIST')).toContainText('Dr. Sami Specialist');

  // --- 5. Nursing tab: add a procedure row, auto-attributed to the logged-in user. ---
  await page.getByTestId('case-tab-nursing').click();
  await page.getByTestId('nursing-procedureName').fill('Umbilical care');
  await clickAndWaitForWrite(page, 'nursing-add', '/nursing-procedures');
  await expect(page.getByTestId('nursing-rows')).toContainText('Umbilical care', { timeout: 10_000 });
  await expect(page.getByTestId('nursing-rows')).toContainText(PREMATURE_FULL_NAME);
  await page.reload();
  await expect(page.getByTestId('nursing-rows')).toContainText('Umbilical care', { timeout: 10_000 });
  await expect(page.getByTestId('nursing-rows')).toContainText(PREMATURE_FULL_NAME);

  // --- 6. Treatment tab: two medicine rows + a timing slot, persisted across reload. ---
  await page.getByTestId('case-tab-treatment').click();
  await page.getByTestId('tc-row-0-medicine').fill('Ampicillin');
  await page.getByTestId('tc-row-0-dose').fill('50mg/kg');
  await page.getByTestId('tc-row-0-freq').fill('q12h');
  // Timing inputs carry no testid — the first timing cell is the 4th input in the row.
  const row0Timing = page.getByTestId('treatment-tab').locator('tbody tr').nth(0).locator('input').nth(3);
  await row0Timing.fill('8');
  await page.getByTestId('tc-add-row').click();
  await page.getByTestId('tc-row-1-medicine').fill('Gentamicin');
  await page.getByTestId('tc-row-1-dose').fill('4mg/kg');
  await clickAndWaitForWrite(page, 'tc-save', '/treatment-charts/');
  await page.reload();
  await expect(page.getByTestId('tc-row-0-medicine')).toHaveValue('Ampicillin', { timeout: 10_000 });
  await expect(page.getByTestId('tc-row-0-dose')).toHaveValue('50mg/kg');
  await expect(page.getByTestId('tc-row-1-medicine')).toHaveValue('Gentamicin');
  await expect(page.getByTestId('tc-row-1-dose')).toHaveValue('4mg/kg');
  await expect(page.getByTestId('treatment-tab').locator('tbody tr').nth(0).locator('input').nth(3)).toHaveValue('8');

  // --- 7. Case File tab: registry prefill display + editable fields persisted. ---
  await page.getByTestId('case-tab-caseFile').click();
  await expect(page.getByTestId('casefile-tab')).toContainText(patient.fullName, { timeout: 10_000 });
  await expect(page.getByTestId('casefile-tab')).toContainText(patient.mrn);
  await page.getByTestId('cf-wardNumber').fill('W-7');
  await page.getByTestId('cf-initialDiagnosis').fill('Prematurity, RDS');
  await page.getByTestId('cf-treatingSpecialist').fill('Dr. Salam');
  await clickAndWaitForWrite(page, 'cf-save', '/case-form');
  await page.reload();
  await expect(page.getByTestId('cf-wardNumber')).toHaveValue('W-7', { timeout: 10_000 });
  await expect(page.getByTestId('cf-initialDiagnosis')).toHaveValue('Prematurity, RDS');
  await expect(page.getByTestId('cf-treatingSpecialist')).toHaveValue('Dr. Salam');

  // --- 8. Billing tab: the initial payment row shows Paid (no testids in BillingTab → text row). ---
  await page.getByTestId('case-tab-billing').click();
  const initialRow = page.locator('li').filter({ hasText: 'Admission / initial payment' });
  await expect(initialRow).toBeVisible({ timeout: 10_000 });
  await expect(initialRow).toContainText('Paid');
  const dischargeRow = page.locator('li').filter({ hasText: 'Discharge payment' });
  await expect(dischargeRow).toContainText('Not yet');

  // --- 9. Timeline tab: admitted + the lab order are listed. ---
  await page.getByTestId('case-tab-timeline').click();
  await expect(page.getByTestId('case-timeline')).toContainText('Admitted', { timeout: 10_000 });
  await expect(page.getByTestId('case-timeline')).toContainText('Order sent → LABORATORY');
  await expect(page.getByTestId('case-timeline')).toContainText(labNote);

  // --- 10. Discharge note → finish (results-pending: lab order open) → override. ---
  await page.getByTestId('case-tab-overview').click();
  await page.getByTestId('discharge-note-input').fill('Stable; home on oral feeds; review in 1 week.');
  await page.getByTestId('discharge-note-save').click();
  await expect(page.getByText(/Discharge note saved/i)).toBeVisible({ timeout: 10_000 });

  await page.getByTestId('detail-finish').click();
  const dialog = page.getByTestId('results-pending-dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText(/LAB/i);
  await dialog.getByTestId('override-reason').fill('Parents accept results will follow');
  await dialog.getByTestId('finish-override').click();
  await expect(page.getByText(/discharge payment sent to cashier/i)).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId('case-status')).toContainText(/Awaiting discharge payment/i, { timeout: 10_000 });

  // --- 11. Cashier approves the FINAL payment via API → case CLOSED. ---
  const cashier = await authedContext('cashier');
  const premature = await authedContext('premature');
  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const finalPay = pending.content.find((p: any) => p.visitId === visitId && p.stage === 'FINAL');
  expect(finalPay).toBeTruthy();
  const approveRes = await cashier.post(`${API_BASE}/payments/${finalPay.id}/approve`, { data: { paymentMethod: 'CASH' } });
  expect(approveRes.ok()).toBe(true);
  await expect(async () => {
    const c = await (await premature.get(`${API_BASE}/premature/admissions/${admissionId}/case`)).json();
    expect(c.admission.status).toBe('CLOSED');
  }).toPass({ timeout: 10_000 });
  await cashier.dispose(); await premature.dispose();

  await page.reload();
  await expect(page.getByTestId('case-status')).toContainText(/Closed/i, { timeout: 10_000 });

  // --- 12. Closed-state read-only: inputs disabled, write actions gone, signed slot as text. ---
  await page.getByTestId('case-tab-history').click();
  await expect(page.getByTestId('mh-chiefComplaint')).toBeDisabled({ timeout: 10_000 });
  await expect(page.getByTestId('mh-save')).toHaveCount(0);
  await expect(page.getByTestId('mh-signed-SPECIALIST')).toBeVisible();
  await expect(page.getByTestId('mh-signed-SPECIALIST')).toContainText('Dr. Sami Specialist');
  await expect(page.getByTestId('canvas-SPECIALIST')).toHaveCount(0);

  await page.getByTestId('case-tab-nursing').click();
  await expect(page.getByTestId('nursing-rows')).toContainText('Umbilical care', { timeout: 10_000 });
  await expect(page.getByTestId('nursing-add')).toHaveCount(0);
  await expect(page.getByTestId('nursing-procedureName')).toHaveCount(0);

  await page.getByTestId('case-tab-treatment').click();
  await expect(page.getByTestId('tc-row-0-medicine')).toBeDisabled({ timeout: 10_000 });
  await expect(page.getByTestId('tc-save')).toHaveCount(0);
  await expect(page.getByTestId('tc-add-row')).toHaveCount(0);

  await page.getByTestId('case-tab-caseFile').click();
  await expect(page.getByTestId('cf-wardNumber')).toBeDisabled({ timeout: 10_000 });
  await expect(page.getByTestId('cf-save')).toHaveCount(0);
});

test('emergency: condensed case-page journey — shared tabs, no case file, ECO order in timeline', async ({ page }) => {
  const { caseId } = await seedEmergencyUnderTreatment();
  await login(page, 'emergency');
  await page.goto(`/emergency/cases/${caseId}`);

  // The three shared tabs exist; the premature-only Case file tab does not.
  await expect(page.getByTestId('case-tab-history')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('case-tab-nursing')).toBeVisible();
  await expect(page.getByTestId('case-tab-treatment')).toBeVisible();
  await expect(page.getByTestId('case-tab-caseFile')).toHaveCount(0);

  // History: save a field and prove persistence.
  await page.getByTestId('case-tab-history').click();
  await page.getByTestId('mh-chiefComplaint').fill('RTA — head injury');
  await clickAndWaitForWrite(page, 'mh-save', '/medical-history');
  await page.reload();
  await expect(page.getByTestId('mh-chiefComplaint')).toHaveValue('RTA — head injury', { timeout: 10_000 });

  // Nursing: add a row, auto-attributed.
  await page.getByTestId('case-tab-nursing').click();
  await page.getByTestId('nursing-procedureName').fill('Wound dressing');
  await clickAndWaitForWrite(page, 'nursing-add', '/nursing-procedures');
  await expect(page.getByTestId('nursing-rows')).toContainText('Wound dressing', { timeout: 10_000 });

  // ECO order through the UI, then visible on the timeline.
  const ecoNote = 'echo — r/o pericardial effusion';
  await orderWithNote(page, 'ECO', ecoNote);
  await page.getByTestId('case-tab-timeline').click();
  await expect(page.getByTestId('case-timeline')).toContainText('Order sent → ECO', { timeout: 10_000 });
  await expect(page.getByTestId('case-timeline')).toContainText(ecoNote);
});
