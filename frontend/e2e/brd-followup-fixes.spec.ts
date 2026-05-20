import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, findActiveItem, startWalkInAndCheckIn,
  approvePendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * Regression coverage for the follow-up gap fixes:
 *  - patient archive endpoint
 *  - VIP toggle accepts cashier role
 *  - OTC PHARMACY visit completes when dispense is given
 *  - body_region on radiology findings round-trips
 *  - REC-003 §6.4 — Fluoroscopy + Nuclear Medicine are seeded
 */

test('patient archive + unarchive flips the archived flag', async () => {
  const admin = await authedContext('admin');
  const p = await registerPatient(admin);
  expect(p.archived).toBe(false);

  const aRes = await admin.put(`${API_BASE}/patients/${p.id}/archive`);
  expect(aRes.ok()).toBeTruthy();
  expect((await aRes.json()).archived).toBe(true);

  const uRes = await admin.put(`${API_BASE}/patients/${p.id}/unarchive`);
  expect(uRes.ok()).toBeTruthy();
  expect((await uRes.json()).archived).toBe(false);

  await admin.dispose();
});

test('cashier role can toggle a patient VIP (REC-001 §6.5)', async () => {
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const p = await registerPatient(admin);
  expect(p.vip).toBe(false);

  const res = await cashier.put(`${API_BASE}/patients/${p.id}/vip`, { data: { vip: true } });
  expect(res.ok()).toBeTruthy();
  expect((await res.json()).vip).toBe(true);

  await admin.dispose(); await cashier.dispose();
});

test('OTC walk-in PHARMACY anchor visit completes when dispense is given', async () => {
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const pharmacist = await authedContext('pharmacist');

  const patient = await registerPatient(admin);
  const drug = await findActiveItem(admin, 'DRUG');

  // Stock so mark-given doesn't fail OUT_OF_STOCK
  const future = new Date(); future.setFullYear(future.getFullYear() + 1);
  await pharmacist.post(`${API_BASE}/pharmacy/inventory/batches`, {
    data: { drugServiceItemId: drug.id, batchNo: `OTC-VC-${Date.now()}`, expiryDate: future.toISOString().slice(0, 10), qty: 20 },
  });

  const sale = await (await pharmacist.post(`${API_BASE}/pharmacy/walk-in-sales`, {
    data: { patientId: patient.id, lines: [{ drugServiceItemId: drug.id, quantity: 1 }] },
  })).json();

  // After charging, visit should now be AWAITING_PAYMENT (was CREATED before fix)
  await expect(async () => {
    const v = await (await admin.get(`${API_BASE}/visits/${sale.visitId}`)).json();
    expect(v.status).toBe('AWAITING_PAYMENT');
  }).toPass({ timeout: 10_000 });

  await approvePendingPaymentFor(cashier, patient.mrn, 'PHARMACY');

  await expect(async () => {
    const v = await (await admin.get(`${API_BASE}/visits/${sale.visitId}`)).json();
    expect(v.status).toBe('IN_PROGRESS');
  }).toPass({ timeout: 10_000 });

  await pharmacist.post(`${API_BASE}/dispenses/${sale.dispense.id}/mark-given`);

  await expect(async () => {
    const v = await (await admin.get(`${API_BASE}/visits/${sale.visitId}`)).json();
    expect(v.status).toBe('COMPLETED');
  }).toPass({ timeout: 10_000 });

  await admin.dispose(); await cashier.dispose(); await pharmacist.dispose();
});

test('radiology findings round-trip the body_region field', async () => {
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const doctor = await authedContext('doctor');
  const rad = await authedContext('radiology');

  const patient = await registerPatient(admin);
  const doc = await findDoctor(admin, 'Kareem');
  const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
  await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

  const img = await findActiveItem(admin, 'IMAGING');
  const fr = await (await doctor.post(`${API_BASE}/visits/${visitId}/forward-with-tests`, {
    data: { targetType: 'RADIOLOGY', services: [{ serviceItemId: img.id, quantity: 1 }] },
  })).json();
  await approvePendingPaymentFor(cashier, patient.mrn, 'REFERRAL');

  await rad.post(`${API_BASE}/dept-cases/${fr.caseId}/findings`, {
    data: {
      serviceItemId: img.id,
      textFindings: 'Mild scoliosis',
      bodyRegion: 'Lumbar spine',
      diagnosis: 'Mild idiopathic scoliosis',
    },
  });
  const c = await (await rad.get(`${API_BASE}/dept-cases/${fr.caseId}`)).json();
  const line = c.services.find((l: any) => l.serviceItemId === img.id);
  expect(line.bodyRegion).toBe('Lumbar spine');

  await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await rad.dispose();
});

test('REC-003 §6.4 minimum imaging types — Fluoroscopy + Nuclear Medicine are seeded', async () => {
  const admin = await authedContext('admin');
  const items = await (await admin.get(`${API_BASE}/catalogue/items?category=IMAGING`)).json();
  const codes = items.map((i: any) => i.code);
  expect(codes).toContain('IMG-FLUORO');
  expect(codes.some((c: string) => c.startsWith('IMG-NM-'))).toBeTruthy();
  await admin.dispose();
});
