import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, findActiveItem, startWalkInAndCheckIn,
  approvePendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * HMS-BRD-REC-003 — Radiology.
 * REC-003 reuses the department-services engine, so this is a smoke confirming the
 * same flow that REC-002 covers also works with IMAGING-category services.
 */
test('REC-003 Radiology: forward → multi-select imaging → cashier → findings + measurements + finalize', async () => {
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const doctor = await authedContext('doctor');
  const rad = await authedContext('emergency'); // emergency user has DOCTOR role; check below if radiology has its own
  // Prefer dedicated radiology user if seeded
  let radCtx = rad;
  try {
    radCtx = await authedContext('radiology');
  } catch { /* fall back */ }

  const patient = await registerPatient(admin);
  const doc = await findDoctor(admin, 'Kareem');
  const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
  await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

  const imaging = await findActiveItem(admin, 'IMAGING');
  const fr = await (await doctor.post(`${API_BASE}/visits/${visitId}/forward-with-tests`, {
    data: { targetType: 'RADIOLOGY', services: [{ serviceItemId: imaging.id, quantity: 1 }] },
  })).json();
  await approvePendingPaymentFor(cashier, patient.mrn, 'REFERRAL');

  // Radiology findings use textFindings + measurements (per BRD §6.6)
  await radCtx.post(`${API_BASE}/dept-cases/${fr.caseId}/findings`, {
    data: {
      serviceItemId: imaging.id,
      textFindings: 'Right lower lobe consolidation; no pneumothorax.',
      measurements: 'EF not applicable; opacity ~3cm',
      diagnosis: 'Right lower lobe pneumonia',
    },
  });
  const finResp = await radCtx.post(`${API_BASE}/dept-cases/${fr.caseId}/finalize`);
  expect(finResp.ok()).toBeTruthy();
  const c = await finResp.json();
  expect(c.status).toBe('RETURNED');

  await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await rad.dispose();
  if (radCtx !== rad) await radCtx.dispose();
});
