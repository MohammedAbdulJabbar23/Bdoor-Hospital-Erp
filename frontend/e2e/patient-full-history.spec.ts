import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * Redesigned patient profile — header, summary chips and the unified, filterable
 * timeline (visits / admissions / forms / documents) (BRD: documents & full history).
 */

/** 1x1 transparent PNG. */
const PNG_B64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

// Adapted from case-documents.spec.ts — premature admission paid up to UNDER_CARE,
// returning the patient identity too so the test can open /patients/{id}.
async function seedUnderCare(): Promise<{ admissionId: string; patientId: string; patientName: string }> {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');
  const bedCode = `PREM-FH-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, { data: { code: bedCode, room: 'History' } })).json();
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, { data: { patientId: patient.id, visitType: 'PREMATURE' } })).json();
  const adm = await (await premature.post(`${API_BASE}/premature/admissions`, {
    data: { visitId: visit.id, bedId: bed.id, stayValue: 3, stayUnit: 'DAYS' } })).json();
  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const initial = pending.content.find((p: any) => p.visitId === visit.id && p.stage === 'INITIAL');
  await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
  await expect(async () => {
    const admissions = await (await premature.get(`${API_BASE}/premature/admissions`)).json();
    expect(admissions.find((a: any) => a.id === adm.id).status).toBe('UNDER_CARE');
  }).toPass({ timeout: 10_000 });
  await admin.dispose(); await premature.dispose(); await cashier.dispose();
  return { admissionId: adm.id, patientId: patient.id, patientName: patient.fullName };
}

test('profile shows header, chips and a filterable timeline with documents', async ({ page }) => {
  const { admissionId, patientId, patientName } = await seedUnderCare();

  // Doctor files the medical-history sheet via API (FORM timeline entry).
  const doctor = await authedContext('doctor');
  const mhRes = await doctor.put(`${API_BASE}/bed-stays/PREMATURE/${admissionId}/medical-history`, {
    data: { chiefComplaint: 'Fever' },
  });
  expect(mhRes.ok(), await mhRes.text()).toBe(true);

  // Doctor orders LAB from the stay — the forwarded child visit must be rolled into the
  // admission, NOT shown as a separate timeline entry (de-duplicated timeline).
  const orderRes = await doctor.post(`${API_BASE}/premature/admissions/${admissionId}/orders`, {
    data: { targetType: 'LABORATORY' },
  });
  expect(orderRes.ok(), await orderRes.text()).toBe(true);
  const forwardedDisplayId: string = (await orderRes.json()).visitDisplayId;
  expect(forwardedDisplayId).toBeTruthy();
  await doctor.dispose();

  // Nurse uploads a stay document via API (DOCUMENT timeline entry).
  const nurse = await authedContext('nurse');
  const upRes = await nurse.post(`${API_BASE}/bed-stays/PREMATURE/${admissionId}/documents`, {
    multipart: {
      file: { name: 'stats.png', mimeType: 'image/png', buffer: Buffer.from(PNG_B64, 'base64') },
      label: 'Statistics form',
    },
  });
  expect(upRes.ok(), await upRes.text()).toBe(true);
  await nurse.dispose();

  await login(page, 'doctor');
  await page.goto(`/patients/${patientId}`);
  await expect(page.getByTestId('profile-header')).toContainText(patientName);
  await expect(page.getByTestId('chip-visits')).toBeVisible();
  await expect(page.getByTestId('chip-documents')).toContainText('1');

  // Exams pill exists in the filter bar.
  await expect(page.getByTestId('timeline-filter-exams')).toBeVisible();

  // De-dup: with the timeline loaded ('all' filter), no entry mentions the forwarded
  // lab visit's display id — it is rolled up under the admission instead.
  await expect(page.locator('[data-testid="timeline-entry-ADMISSION"]').first()).toBeVisible();
  await expect(page.locator('[data-testid^="timeline-entry-"]', { hasText: forwardedDisplayId })).toHaveCount(0);

  await page.getByTestId('timeline-filter-documents').click();
  await expect(page.locator('[data-testid="timeline-entry-DOCUMENT"]')).toBeVisible();
  await expect(page.locator('[data-testid="timeline-entry-DOCUMENT"]')).toContainText('stats.png');

  await page.getByTestId('timeline-filter-forms').click();
  await expect(page.locator('[data-testid="timeline-entry-FORM"]').first()).toContainText('Medical history sheet');

  await page.getByTestId('timeline-filter-admissions').click();
  await page.locator('[data-testid="timeline-entry-ADMISSION"] a').first().click();
  await expect(page).toHaveURL(/premature\/admissions/);
});

// Language-switch pattern from i18n-rtl.spec.ts: the "ع" control in the topbar LangSwitcher
// flips i18n + document dir; timeline titles are kind/params-based so they re-render localized.
test('Arabic profile renders localized timeline titles', async ({ page }) => {
  const { patientId } = await seedUnderCare();

  await login(page, 'doctor');
  await page.goto(`/patients/${patientId}`);
  await expect(page.getByTestId('profile-header')).toBeVisible();
  await expect(page.locator('[data-testid="timeline-entry-ADMISSION"]').first()).toBeVisible();

  await page.getByRole('button', { name: 'ع', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl', { timeout: 10_000 });

  // 'Admitted to bed {{bed}}' → 'رقود في سرير {{bed}}' (patientProfile.timeline.kind.admissionOpened).
  await expect(page.locator('[data-testid="timeline-entry-ADMISSION"]').first())
    .toContainText('رقود في سرير', { timeout: 10_000 });
});
