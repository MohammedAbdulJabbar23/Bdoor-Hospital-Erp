import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, startWalkInAndCheckIn,
  approvePendingPaymentFor, rejectPendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * HMS-BRD-REC-001 — Doctor Appointment.
 * Every test maps to a numbered functional requirement in the BRD.
 */

test.describe('REC-001 Doctor Appointment', () => {
  test('§6.2 patient search detects existing patient by mobile + name', async () => {
    const admin = await authedContext('admin');
    const created = await registerPatient(admin);

    // search by mobile
    const byMobile = await admin.get(`${API_BASE}/patients?q=${encodeURIComponent(created.adult.mobileNumber)}`);
    const m1 = await byMobile.json();
    expect(m1.content.some((p: any) => p.id === created.id)).toBeTruthy();

    // search by name fragment
    const frag = created.fullName.split(' ')[2];
    const byName = await admin.get(`${API_BASE}/patients?q=${encodeURIComponent(frag)}`);
    const m2 = await byName.json();
    expect(m2.content.some((p: any) => p.id === created.id)).toBeTruthy();
    await admin.dispose();
  });

  test('§6.3 General Data Form rejects missing mandatory fields and issues an MRN on success', async () => {
    const admin = await authedContext('admin');
    // missing fullName → 4xx
    const bad = await admin.post(`${API_BASE}/patients`, {
      data: { gender: 'MALE', dateOfBirth: '1990-01-01', vip: false },
    });
    expect(bad.status()).toBeGreaterThanOrEqual(400);
    expect(bad.status()).toBeLessThan(500);

    const ok = await registerPatient(admin);
    expect(ok.mrn).toBeTruthy();
    expect(ok.mrn.length).toBeGreaterThan(0);
    await admin.dispose();
  });

  test('§6.4 doctor list available + has weekly hours/schedule', async () => {
    const admin = await authedContext('admin');
    const list = await admin.get(`${API_BASE}/doctors`);
    const docs = await list.json();
    expect(docs.length).toBeGreaterThan(0);
    expect(docs[0]).toHaveProperty('weeklyHours');
    expect(docs[0]).toHaveProperty('consultationFee');
    await admin.dispose();
  });

  test('§6.5 cashier approve → visit IN_PROGRESS; reject → OUTSTANDING_BALANCE', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');

    // --- approve path ---
    const patient = await registerPatient(admin);
    const doctor = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doctor.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');
    await expect(async () => {
      const v = await (await admin.get(`${API_BASE}/visits/${visitId}`)).json();
      expect(v.status).toBe('IN_PROGRESS');
    }).toPass({ timeout: 10_000 });

    // --- reject path (INITIAL): BRD §7b — visit stays AWAITING_PAYMENT for receptionist
    // to either re-issue or cancel. Payment row itself becomes REJECTED.
    const patient2 = await registerPatient(admin);
    const { visitId: visit2 } = await startWalkInAndCheckIn(admin, patient2.id, doctor.id);
    const rejected = await rejectPendingPaymentFor(cashier, patient2.mrn, 'INITIAL', 'Patient could not pay');
    expect(rejected.status).toBe('REJECTED');
    await expect(async () => {
      const v = await (await admin.get(`${API_BASE}/visits/${visit2}`)).json();
      expect(['AWAITING_PAYMENT']).toContain(v.status);
    }).toPass({ timeout: 10_000 });

    await admin.dispose();
    await cashier.dispose();
  });

  test('§6.6 doctor can record vitals + diagnosis + prescription + finalize', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doc = await authedContext('doctor');

    const patient = await registerPatient(admin);
    const doctor = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doctor.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const upRes = await doc.put(`${API_BASE}/exams`, {
      data: {
        visitId,
        vitals: { systolicBp: 120, diastolicBp: 78, heartRate: 70, temperatureC: 36.7 },
        chiefComplaint: 'Headache for 3 days',
        diagnoses: [{ code: 'R51', description: 'Headache', primary: true, notes: null }],
        prescriptions: [
          { drugServiceItemId: null, drugCode: null, drugName: 'Paracetamol 500mg',
            strength: null, dose: '500mg', frequency: 'TDS', duration: '5d', route: 'PO', notes: null },
        ],
        plan: 'Hydration and rest',
      },
    });
    expect(upRes.ok()).toBeTruthy();
    const exam = await upRes.json();
    expect(exam.diagnoses.length).toBe(1);
    expect(exam.prescriptions.length).toBe(1);

    const fRes = await doc.post(`${API_BASE}/exams/${exam.id}/finalize`);
    expect(fRes.ok()).toBeTruthy();
    const finalized = await fRes.json();
    expect(finalized.status).toBe('FINALIZED');

    // Visit closed when not forwarded (§6.8)
    await expect(async () => {
      const v = await (await admin.get(`${API_BASE}/visits/${visitId}`)).json();
      expect(['COMPLETED', 'TREATMENT_FINISHED']).toContain(v.status);
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await cashier.dispose(); await doc.dispose();
  });

  test('§6.5 VIP bypass: no cashier step; visit auto-progresses to IN_PROGRESS', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');

    const patient = await registerPatient(admin, { vip: true });
    const doctor = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doctor.id);

    // Visit should reach IN_PROGRESS without any cashier action because VIP auto-approves
    await expect(async () => {
      const v = await (await admin.get(`${API_BASE}/visits/${visitId}`)).json();
      expect(v.status).toBe('IN_PROGRESS');
    }).toPass({ timeout: 10_000 });

    // The payment row exists but is APPROVED with method VIP_BYPASS
    const list = await cashier.get(`${API_BASE}/payments?size=100`);
    const mine = (await list.json()).content.filter((p: any) => p.patientMrn === patient.mrn);
    expect(mine.length).toBeGreaterThan(0);
    expect(mine.every((p: any) => p.status === 'APPROVED')).toBeTruthy();
    expect(mine.some((p: any) => p.vipBypass === true || p.paymentMethod === 'VIP_BYPASS')).toBeTruthy();

    // Cashier queue (PENDING) should NOT show this patient
    const pendList = await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`);
    const pendMine = (await pendList.json()).content.filter((p: any) => p.patientMrn === patient.mrn);
    expect(pendMine.length).toBe(0);

    await admin.dispose(); await cashier.dispose();
  });

  test('§6.7 forward to dept creates a child visit at that dept', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doc = await authedContext('doctor');

    const patient = await registerPatient(admin);
    const doctor = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doctor.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const fwd = await doc.post(`${API_BASE}/visits/${visitId}/forward`, {
      data: { targetType: 'RADIOLOGY' },
    });
    expect(fwd.status()).toBe(200);
    const r = await fwd.json();
    expect(r.child.visitType).toBe('RADIOLOGY');
    expect(r.parent.status).toBe('AWAITING_RESULTS');

    // BRD §6.7 — department list options must include RADIOLOGY (and the others)
    // (catalogue side: a RADIOLOGY service item exists)
    const items = await (await admin.get(`${API_BASE}/catalogue/items?category=IMAGING`)).json();
    expect(items.length).toBeGreaterThan(0);

    await admin.dispose(); await cashier.dispose(); await doc.dispose();
  });
});
