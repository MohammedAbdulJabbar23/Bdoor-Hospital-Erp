import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * Case-page Documents tab — upload/tag/preview/archive of stay documents plus the
 * merged read-only lab-result attachments (BRD: documents & full history).
 */

/** 1x1 transparent PNG. */
const PNG_B64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

// Copied from bed-stay-clinical-forms.spec.ts — premature admission paid up to UNDER_CARE.
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

type LabOrder = { visitId: string; caseId: string; serviceItemId: string };

/**
 * Mirrors StayDocumentsIT.orderLabAndUploadResult over HTTP: doctor orders LAB from the
 * stay, lab opens the dept case on the forwarded visit and uploads result.png to the
 * service line (the attachment upload does not require the referral payment).
 */
async function orderLabAndUploadResult(admissionId: string): Promise<LabOrder> {
  const admin = await authedContext('admin');
  const doctor = await authedContext('doctor');
  const premature = await authedContext('premature');
  const lab = await authedContext('lab');

  // ordering requires UNDER_CARE; payment approval flips the admission asynchronously
  await expect(async () => {
    const admissions = await (await premature.get(`${API_BASE}/premature/admissions`)).json();
    expect(admissions.find((a: any) => a.id === admissionId).status).toBe('UNDER_CARE');
  }).toPass({ timeout: 10_000 });

  const orderRes = await doctor.post(`${API_BASE}/premature/admissions/${admissionId}/orders`, {
    data: { targetType: 'LABORATORY' },
  });
  expect(orderRes.ok(), await orderRes.text()).toBe(true);
  const forwardedVisitId = (await orderRes.json()).visitId;

  // the receiving lab opens the case on the forwarded visit (creates the DepartmentCase)
  const itemRes = await admin.post(`${API_BASE}/catalogue/items`, {
    data: { category: 'LAB', code: `CBC-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`,
      nameEn: 'Complete Blood Count', fee: 5000, currency: 'IQD' },
  });
  expect(itemRes.ok(), await itemRes.text()).toBe(true);
  const item = await itemRes.json();
  const caseRes = await lab.post(`${API_BASE}/dept-cases/open`, {
    data: { category: 'LAB', visitId: forwardedVisitId, services: [{ serviceItemId: item.id, quantity: 1 }] },
  });
  expect(caseRes.ok(), await caseRes.text()).toBe(true);
  const deptCase = await caseRes.json();

  // lab attaches the result file
  const upRes = await lab.post(`${API_BASE}/dept-cases/${deptCase.id}/services/${item.id}/attachments`, {
    multipart: {
      file: { name: 'result.png', mimeType: 'image/png', buffer: Buffer.from(PNG_B64, 'base64') },
    },
  });
  expect(upRes.ok(), await upRes.text()).toBe(true);

  await admin.dispose(); await doctor.dispose(); await premature.dispose(); await lab.dispose();
  return { visitId: forwardedVisitId, caseId: deptCase.id, serviceItemId: item.id };
}

/**
 * Mirrors StayDocumentsIT.approveReferralAndUploadFindings over HTTP, plus the finalize step
 * from PrematureOrdersIT.driveLabChildToCompleted: approve the REFERRAL payment as cashier →
 * await AWAITING_STUDY (the PaymentToCaseBridge fires AFTER_COMMIT) → POST findings as lab →
 * finalize so the result returns to the stay (which is what sets resultsAt → 'Results ready').
 */
async function approveReferralAndUploadFindings(order: LabOrder, textFindings: string): Promise<void> {
  const cashier = await authedContext('cashier');
  const lab = await authedContext('lab');

  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const referral = pending.content.find((p: any) => p.visitId === order.visitId);
  expect(referral, `no pending referral payment for forwarded visit ${order.visitId}`).toBeTruthy();
  const apRes = await cashier.post(`${API_BASE}/payments/${referral.id}/approve`, { data: { paymentMethod: 'CASH' } });
  expect(apRes.ok(), await apRes.text()).toBe(true);

  await expect(async () => {
    const c = await (await lab.get(`${API_BASE}/dept-cases/${order.caseId}`)).json();
    expect(c.status).toBe('AWAITING_STUDY');
  }).toPass({ timeout: 10_000 });

  const findRes = await lab.post(`${API_BASE}/dept-cases/${order.caseId}/findings`, {
    data: { serviceItemId: order.serviceItemId, textFindings },
  });
  expect(findRes.ok(), await findRes.text()).toBe(true);
  const finRes = await lab.post(`${API_BASE}/dept-cases/${order.caseId}/finalize`, { data: {} });
  expect(finRes.ok(), await finRes.text()).toBe(true);

  await cashier.dispose(); await lab.dispose();
}

test('upload → tagged list → preview → archive → hidden behind toggle', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}?tab=documents`);
  await page.getByTestId('doc-label').fill('Statistics form');
  await page.getByTestId('doc-file').setInputFiles({
    name: 'stats.png', mimeType: 'image/png', buffer: Buffer.from(PNG_B64, 'base64'),
  });
  await expect(page.getByTestId('doc-rows')).toContainText('stats.png', { timeout: 10_000 });
  await expect(page.getByTestId('doc-rows')).toContainText('Statistics form');

  await page.getByTestId('doc-view-stats.png').click();
  await expect(page.getByTestId('doc-preview')).toBeVisible();
  await expect(page.getByTestId('doc-preview').locator('img')).toBeVisible({ timeout: 10_000 });
  await page.getByTestId('doc-preview-close').click();

  page.on('dialog', (d) => d.accept());
  await page.getByTestId('doc-archive-stats.png').click();
  await expect(page.getByTestId('doc-rows')).not.toContainText('stats.png', { timeout: 10_000 });
  await page.getByTestId('doc-toggle-archived').click();
  await expect(page.getByTestId('doc-rows')).toContainText('stats.png');
});

test('lab result document appears with LABORATORY badge', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  await orderLabAndUploadResult(admissionId);
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}?tab=documents`);
  await expect(page.getByTestId('doc-rows')).toContainText('result.png', { timeout: 10_000 });
  await expect(page.getByTestId('doc-rows')).toContainText('Lab result');
});

test('lab order row shows Results ready and expands to findings + result documents', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  const order = await orderLabAndUploadResult(admissionId);
  await approveReferralAndUploadFindings(order, 'WBC within normal limits');

  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}?tab=LABORATORY`);

  // the finalized child returns its result to the stay asynchronously → friendly status pill
  await expect(page.getByTestId('order-list')).toContainText('Results ready', { timeout: 10_000 });

  await page.getByTestId(`order-expand-${order.visitId}`).click();
  const panel = page.getByTestId(`order-results-${order.visitId}`);
  await expect(panel).toBeVisible();
  await expect(panel).toContainText('WBC within normal limits', { timeout: 10_000 });
  await expect(panel).toContainText('Complete Blood Count');
  await expect(panel).toContainText('result.png');
});
