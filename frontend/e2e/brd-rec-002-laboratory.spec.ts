import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, findActiveItem, startWalkInAndCheckIn,
  approvePendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * HMS-BRD-REC-002 — Laboratory Visit.
 */

test.describe('REC-002 Laboratory', () => {
  test('§6.1 Direct visit origin: receptionist creates a direct LAB visit (DIRECT_RETURNING)', async () => {
    const admin = await authedContext('admin');
    const patient = await registerPatient(admin);

    const res = await admin.post(`${API_BASE}/visits`, {
      data: { patientId: patient.id, visitType: 'LABORATORY' },
    });
    expect(res.status()).toBe(201);
    const v = await res.json();
    expect(v.visitType).toBe('LABORATORY');
    expect(['DIRECT_NEW', 'DIRECT_RETURNING']).toContain(v.origin);
    expect(v.parentVisitId).toBeNull();
    await admin.dispose();
  });

  test('§6.1 Forwarded origin: child lab visit carries parent reference + FORWARDED origin', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId: consultId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const fwd = await doctor.post(`${API_BASE}/visits/${consultId}/forward`, {
      data: { targetType: 'LABORATORY' },
    });
    const r = await fwd.json();
    expect(r.child.origin).toBe('FORWARDED');
    expect(r.child.parentVisitId).toBe(consultId);
    expect(r.parent.status).toBe('AWAITING_RESULTS');

    await admin.dispose(); await cashier.dispose(); await doctor.dispose();
  });

  test('§6.3 Lab queue lists forwarded incoming visit with arrival time + origin', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');
    const lab = await authedContext('lab');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId: consultId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');
    const r = await (await doctor.post(`${API_BASE}/visits/${consultId}/forward`, { data: { targetType: 'LABORATORY' } })).json();

    const queue = await (await lab.get(`${API_BASE}/visits?type=LABORATORY&size=50`)).json();
    const mine = queue.content.find((v: any) => v.id === r.child.id);
    expect(mine).toBeTruthy();
    expect(mine.startedAt).toBeTruthy();
    expect(mine.origin).toBe('FORWARDED');

    await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await lab.dispose();
  });

  test('§6.4 Multi-select services + auto-total + REFERRAL-stage payment is created', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId: consultId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const labItems = await (await admin.get(`${API_BASE}/catalogue/items?category=LAB&activeOnly=true`)).json();
    expect(labItems.length).toBeGreaterThanOrEqual(2);
    const picked = labItems.slice(0, 2);
    const expectedTotal = picked.reduce((s: number, it: any) => s + (it.fee ?? 0), 0);

    const fr = await doctor.post(`${API_BASE}/visits/${consultId}/forward-with-tests`, {
      data: {
        targetType: 'LABORATORY',
        services: picked.map((p: any) => ({ serviceItemId: p.id, quantity: 1 })),
      },
    });
    expect(fr.status()).toBe(200);
    const result = await fr.json();
    expect(result.caseId).toBeTruthy();

    // The REFERRAL payment should equal sum of fees
    await expect(async () => {
      const list = await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`);
      const ps = (await list.json()).content.filter((p: any) => p.patientMrn === patient.mrn && p.stage === 'REFERRAL');
      expect(ps.length).toBe(1);
      expect(Number(ps[0].totalDue)).toBeCloseTo(expectedTotal, 2);
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await cashier.dispose(); await doctor.dispose();
  });

  test('§6.4 opening a case without services is rejected', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');
    const lab = await authedContext('lab');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');
    const r = await (await doctor.post(`${API_BASE}/visits/${visitId}/forward`, { data: { targetType: 'LABORATORY' } })).json();

    const open = await lab.post(`${API_BASE}/dept-cases/open`, {
      data: { category: 'LAB', visitId: r.child.id, services: [] },
    });
    expect(open.status()).toBeGreaterThanOrEqual(400);

    await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await lab.dispose();
  });

  test('§6.6/§6.7 findings upload + all-complete enforces; forwarded case RETURNS to parent', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');
    const lab = await authedContext('lab');

    // Setup: walk-in doctor visit, approve consult, doctor forwards with 2 lab tests, cashier approves referral.
    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId: consultId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const labItems = await (await admin.get(`${API_BASE}/catalogue/items?category=LAB&activeOnly=true`)).json();
    const [s1, s2] = labItems.slice(0, 2);
    const fr = await (await doctor.post(`${API_BASE}/visits/${consultId}/forward-with-tests`, {
      data: { targetType: 'LABORATORY', services: [{ serviceItemId: s1.id, quantity: 1 }, { serviceItemId: s2.id, quantity: 1 }] },
    })).json();
    await approvePendingPaymentFor(cashier, patient.mrn, 'REFERRAL');

    // Upload findings for only one service — case should NOT be closable.
    const upRes = await lab.post(`${API_BASE}/dept-cases/${fr.caseId}/findings`, {
      data: { serviceItemId: s1.id, numericValue: 12.4, unit: 'g/dL', referenceRange: '12-16', flag: 'NORMAL', comments: 'within range' },
    });
    expect(upRes.ok()).toBeTruthy();

    const finalizeEarly = await lab.post(`${API_BASE}/dept-cases/${fr.caseId}/finalize`);
    expect(finalizeEarly.status()).toBeGreaterThanOrEqual(400);

    // Upload second findings, then finalize — should RETURN to parent
    await lab.post(`${API_BASE}/dept-cases/${fr.caseId}/findings`, {
      data: { serviceItemId: s2.id, textFindings: 'No organisms isolated', diagnosis: 'Normal' },
    });
    const fin = await lab.post(`${API_BASE}/dept-cases/${fr.caseId}/finalize`);
    expect(fin.status()).toBe(200);
    const finCase = await fin.json();
    expect(['CLOSED', 'RETURNED']).toContain(finCase.status);
    // Forwarded → RETURNED to originating doctor visit, parent goes back to IN_PROGRESS or TREATMENT_FINISHED
    expect(finCase.status).toBe('RETURNED');

    // Parent visit gets resultsSummary set (notification surface)
    await expect(async () => {
      const parent = await (await admin.get(`${API_BASE}/visits/${consultId}`)).json();
      expect(parent.resultsSummary ?? '').not.toBe('');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await lab.dispose();
  });

  test('§6.6 lab findings carry numeric, unit, range, flag, comments, diagnosis', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');
    const lab = await authedContext('lab');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const s = await findActiveItem(admin, 'LAB');
    const fr = await (await doctor.post(`${API_BASE}/visits/${visitId}/forward-with-tests`, {
      data: { targetType: 'LABORATORY', services: [{ serviceItemId: s.id, quantity: 1 }] },
    })).json();
    await approvePendingPaymentFor(cashier, patient.mrn, 'REFERRAL');

    await lab.post(`${API_BASE}/dept-cases/${fr.caseId}/findings`, {
      data: {
        serviceItemId: s.id,
        numericValue: 5.6,
        unit: 'mmol/L',
        referenceRange: '3.5-5.5',
        flag: 'HIGH',
        comments: 'Slightly elevated',
        diagnosis: 'Borderline hyperglycaemia',
      },
    });
    const c = await (await lab.get(`${API_BASE}/dept-cases/${fr.caseId}`)).json();
    const line = c.services.find((l: any) => l.serviceItemId === s.id);
    expect(Number(line.numericValue)).toBeCloseTo(5.6);
    expect(line.unit).toBe('mmol/L');
    expect(line.referenceRange).toBe('3.5-5.5');
    expect(line.flag).toBe('HIGH');
    expect(line.comments).toContain('elevated');
    expect(line.diagnosis).toBeTruthy();
    expect(line.uploadedAt).toBeTruthy();
    expect(line.uploadedBy).toBeTruthy();

    await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await lab.dispose();
  });
});
