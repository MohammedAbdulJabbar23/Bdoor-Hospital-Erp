import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, findActiveItem, startWalkInAndCheckIn,
  approvePendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * Locked client decisions (2026-05-01) that don't map to a single BRD section but
 * must hold across the system.
 */
test.describe('Locked client decisions', () => {
  test('Single central cashier: every payment surfaces in one /payments queue regardless of origin', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);

    // INITIAL appears in central queue
    const initialList = await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`);
    const initialMine = (await initialList.json()).content.filter((p: any) => p.patientMrn === patient.mrn);
    expect(initialMine.length).toBe(1);
    expect(initialMine[0].stage).toBe('INITIAL');

    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    // Forward with a lab test — produces a REFERRAL payment that also shows in central queue
    const lab = await findActiveItem(admin, 'LAB');
    await doctor.post(`${API_BASE}/visits/${visitId}/forward-with-tests`, {
      data: { targetType: 'LABORATORY', services: [{ serviceItemId: lab.id, quantity: 1 }] },
    });
    const refList = await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`);
    const refMine = (await refList.json()).content.filter((p: any) => p.patientMrn === patient.mrn);
    expect(refMine.length).toBe(1);
    expect(refMine[0].stage).toBe('REFERRAL');

    await admin.dispose(); await cashier.dispose(); await doctor.dispose();
  });

  test('Forwarded patient pays receiving department fees (REFERRAL-stage payment)', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    // Pick a lab + radiology item; forward to radiology with the imaging fee.
    const img = await findActiveItem(admin, 'IMAGING');
    await doctor.post(`${API_BASE}/visits/${visitId}/forward-with-tests`, {
      data: { targetType: 'RADIOLOGY', services: [{ serviceItemId: img.id, quantity: 1 }] },
    });

    await expect(async () => {
      const list = await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`);
      const ref = (await list.json()).content.find(
        (p: any) => p.patientMrn === patient.mrn && p.stage === 'REFERRAL',
      );
      expect(ref).toBeTruthy();
      expect(Number(ref.totalDue)).toBeCloseTo(img.fee ?? 0, 2);
      // The line should reference the RECEIVING dept's catalogue item, not the doctor's fee.
      expect(ref.lines.some((l: any) => l.code === img.code)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await cashier.dispose(); await doctor.dispose();
  });

  test('VIP bypass auto-approves at every cashier stage (initial AND referral)', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');

    const patient = await registerPatient(admin, { vip: true });
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);

    // Should NOT need cashier — visit reaches IN_PROGRESS automatically
    await expect(async () => {
      const v = await (await admin.get(`${API_BASE}/visits/${visitId}`)).json();
      expect(v.status).toBe('IN_PROGRESS');
    }).toPass({ timeout: 10_000 });

    // Forward with tests → REFERRAL payment must also auto-approve
    const lab = await findActiveItem(admin, 'LAB');
    const fr = await (await doctor.post(`${API_BASE}/visits/${visitId}/forward-with-tests`, {
      data: { targetType: 'LABORATORY', services: [{ serviceItemId: lab.id, quantity: 1 }] },
    })).json();

    await expect(async () => {
      const list = await cashier.get(`${API_BASE}/payments?size=100`);
      const refs = (await list.json()).content.filter(
        (p: any) => p.patientMrn === patient.mrn && p.stage === 'REFERRAL',
      );
      expect(refs.length).toBeGreaterThan(0);
      expect(refs.every((p: any) => p.status === 'APPROVED' && p.vipBypass === true)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    // Lab case should unlock immediately (AWAITING_STUDY, not AWAITING_PAYMENT)
    await expect(async () => {
      const c = await (await admin.get(`${API_BASE}/dept-cases/${fr.caseId}`)).json();
      expect(['AWAITING_STUDY', 'FINDINGS_COMPLETE']).toContain(c.status);
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await cashier.dispose(); await doctor.dispose();
  });

  test('Routing slip data available on every approved payment (patient/visit/services)', async () => {
    // Routing-slip print is FE-only (no API). This test asserts the data carried by the
    // payment row is sufficient to render the slip after approval.
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    await startWalkInAndCheckIn(admin, patient.id, doc.id);

    const approved = await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');
    expect(approved.patientName).toBeTruthy();
    expect(approved.patientMrn).toBeTruthy();
    expect(approved.visitDisplayId).toBeTruthy();
    expect(approved.paymentDisplayId).toBeTruthy();
    expect(approved.lines.length).toBeGreaterThan(0);
    expect(approved.lines[0]).toHaveProperty('code');
    expect(approved.lines[0]).toHaveProperty('name');

    await admin.dispose(); await cashier.dispose();
  });
});
