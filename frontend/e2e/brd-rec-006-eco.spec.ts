import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, findActiveItem, startWalkInAndCheckIn,
  approvePendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * HMS-BRD-REC-006 — Echocardiography (ECO).
 */
test('REC-006 ECO: forward → ECO service → cashier → cardiac findings + measurements + finalize', async () => {
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const doctor = await authedContext('doctor');
  const eco = await authedContext('eco');

  const patient = await registerPatient(admin);
  const doc = await findDoctor(admin, 'Kareem');
  const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
  await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

  const ecoItem = await findActiveItem(admin, 'ECO');
  const fr = await (await doctor.post(`${API_BASE}/visits/${visitId}/forward-with-tests`, {
    data: { targetType: 'ECO', services: [{ serviceItemId: ecoItem.id, quantity: 1 }] },
  })).json();
  await approvePendingPaymentFor(cashier, patient.mrn, 'REFERRAL');

  await eco.post(`${API_BASE}/dept-cases/${fr.caseId}/findings`, {
    data: {
      serviceItemId: ecoItem.id,
      textFindings: 'Normal LV function. No regional wall motion abnormality.',
      measurements: 'EF 60%; LVIDd 4.6cm; LA 3.5cm',
      diagnosis: 'Normal echocardiogram',
    },
  });
  const finResp = await eco.post(`${API_BASE}/dept-cases/${fr.caseId}/finalize`);
  expect(finResp.ok()).toBeTruthy();
  const c = await finResp.json();
  expect(c.status).toBe('RETURNED');

  await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await eco.dispose();
});
