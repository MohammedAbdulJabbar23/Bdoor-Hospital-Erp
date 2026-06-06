import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

async function seedUnderCare(): Promise<{ admissionId: string; bedCode: string }> {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');
  const bedCode = `PREM-FRM-${Date.now()}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, { data: { code: bedCode, room: 'Form' } })).json();
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

/** Open the Clinical tab → Form view. The premature Form + Tours now live under the Clinical tab. */
async function openClinicalForm(page: import('@playwright/test').Page) {
  await page.getByTestId('case-tab-clinical').click();
  await page.getByTestId('clinical-form').click();
}

/** Fill all BRD-mandatory Premature Form fields and save; waits until the save lands
 *  (signatures render only once the form exists). Doesn't rely on registration pre-fill. */
async function fillMandatoryForm(page: import('@playwright/test').Page) {
  await openClinicalForm(page);
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
  await expect(page.getByTestId('canvas-RESIDENT')).toBeVisible({ timeout: 10_000 });
}

test('premature staff fill the Premature Form and record a tour', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}`);

  await fillMandatoryForm(page);

  await page.reload();
  await openClinicalForm(page);
  await expect(page.getByTestId('f-currentWeightKg')).toHaveValue('1.45');

  await page.getByTestId('clinical-tours').click();
  await page.getByTestId('tour-respRate').fill('40');
  await page.getByTestId('tour-spo2').fill('96');
  await page.getByTestId('tour-pulse').fill('140');
  await page.getByTestId('tour-uop').fill('2 ml/kg');
  await page.getByTestId('tour-temp').fill('36.8');
  await page.getByRole('checkbox', { name: 'CPAP' }).check();
  await page.getByTestId('save-tour').click();
  await expect(page.getByTestId('tour-list')).toContainText(/RR 40/);
});

test('premature staff draw and save a resident signature', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}`);

  // Save the form first so the signatures section renders.
  await fillMandatoryForm(page);

  // Draw on the resident signature canvas.
  const canvas = page.getByTestId('canvas-RESIDENT');
  await expect(canvas).toBeVisible({ timeout: 10_000 });
  const box = await canvas.boundingBox();
  if (!box) throw new Error('no canvas box');
  await page.mouse.move(box.x + 20, box.y + 20);
  await page.mouse.down();
  await page.mouse.move(box.x + 120, box.y + 60);
  await page.mouse.move(box.x + 220, box.y + 30);
  await page.mouse.up();
  await page.getByTestId('signer-RESIDENT').fill('Dr. Noor');
  await page.getByTestId('save-sign-RESIDENT').click();

  // After saving, the pad shows the stored signature image.
  await expect(page.getByTestId('signature-RESIDENT').locator('img')).toBeVisible({ timeout: 10_000 });
});

test('clicking the bed opens the case page with the clinical form', async ({ page }) => {
  const { admissionId, bedCode } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto('/departments/premature');
  await page.getByTestId(`bed-${bedCode}`).click();
  await expect(page).toHaveURL(new RegExp(`/premature/admissions/${admissionId}`));
  await openClinicalForm(page);
  await expect(page.getByTestId('prem-form')).toBeVisible();
});
