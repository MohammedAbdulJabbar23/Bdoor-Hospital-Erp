import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import {
  registerPatient, findDoctor, findActiveItem, startWalkInAndCheckIn,
  approvePendingPaymentFor, rejectPendingPaymentFor, API_BASE,
} from './helpers/seeds';

/**
 * Pharmacy module — locked client decision: full prescription-driven dispense flow.
 * Doctor prescribes catalogue drug in exam → dispense auto-created via
 * ExamFinalizedToDispenseBridge → pharmacist charges → cashier approves → mark given.
 */

test.describe('Pharmacy module (BRD locked decision)', () => {
  test('exam prescription with catalogue drug → dispense PENDING after finalize', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');
    const pharmacist = await authedContext('pharmacist');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const drug = await findActiveItem(admin, 'DRUG');
    const upRes = await doctor.put(`${API_BASE}/exams`, {
      data: {
        visitId,
        chiefComplaint: 'Mild pain',
        diagnoses: [{ code: null, description: 'Soft tissue pain', primary: true, notes: null }],
        prescriptions: [{
          drugServiceItemId: drug.id, drugCode: drug.code, drugName: drug.nameEn,
          strength: null, dose: '1 tab', frequency: 'BID', duration: '5d', route: 'PO', notes: null,
        }],
      },
    });
    const exam = await upRes.json();
    await doctor.post(`${API_BASE}/exams/${exam.id}/finalize`);

    // A pharmacy dispense should appear in PENDING for this patient
    await expect(async () => {
      const list = await pharmacist.get(`${API_BASE}/dispenses?status=PENDING&size=50`);
      const body = await list.json();
      const mine = body.content.filter((d: any) => d.patientMrn === patient.mrn);
      expect(mine.length).toBe(1);
      expect(mine[0].lines.length).toBe(1);
      expect(mine[0].lines[0].drugCode).toBe(drug.code);
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await pharmacist.dispose();
  });

  test('charge → cashier approve → READY_TO_GIVE → mark given', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');
    const pharmacist = await authedContext('pharmacist');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const drug = await findActiveItem(admin, 'DRUG');
    // Stock the drug so mark-given succeeds
    const exp = new Date(); exp.setFullYear(exp.getFullYear() + 1);
    await pharmacist.post(`${API_BASE}/pharmacy/inventory/batches`, {
      data: { drugServiceItemId: drug.id, batchNo: `RX-${Date.now()}`, expiryDate: exp.toISOString().slice(0, 10), qty: 50 },
    });

    const exam = await (await doctor.put(`${API_BASE}/exams`, {
      data: {
        visitId,
        chiefComplaint: 'cough',
        diagnoses: [{ code: null, description: 'URI', primary: true, notes: null }],
        prescriptions: [{
          drugServiceItemId: drug.id, drugCode: drug.code, drugName: drug.nameEn,
          strength: null, dose: '1', frequency: 'OD', duration: '3d', route: 'PO', notes: null,
        }],
      },
    })).json();
    await doctor.post(`${API_BASE}/exams/${exam.id}/finalize`);

    // Pick up the just-created PENDING dispense
    let dispense: any;
    await expect(async () => {
      const list = await pharmacist.get(`${API_BASE}/dispenses?status=PENDING&size=50`);
      const body = await list.json();
      dispense = body.content.find((d: any) => d.patientMrn === patient.mrn);
      expect(dispense).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    // Charge
    const cRes = await pharmacist.post(`${API_BASE}/dispenses/${dispense.id}/charge`);
    expect(cRes.ok()).toBeTruthy();
    expect((await cRes.json()).status).toBe('AWAITING_PAYMENT');

    // Cashier approves
    await approvePendingPaymentFor(cashier, patient.mrn, 'PHARMACY');

    // Dispense should be READY_TO_GIVE
    await expect(async () => {
      const d = await (await pharmacist.get(`${API_BASE}/dispenses/${dispense.id}`)).json();
      expect(d.status).toBe('READY_TO_GIVE');
    }).toPass({ timeout: 10_000 });

    // Mark given
    const gRes = await pharmacist.post(`${API_BASE}/dispenses/${dispense.id}/mark-given`);
    expect(gRes.ok()).toBeTruthy();
    expect((await gRes.json()).status).toBe('DISPENSED');

    await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await pharmacist.dispose();
  });

  test('cashier rejects pharmacy charge → dispense returns to PENDING (re-charge allowed)', async () => {
    const admin = await authedContext('admin');
    const cashier = await authedContext('cashier');
    const doctor = await authedContext('doctor');
    const pharmacist = await authedContext('pharmacist');

    const patient = await registerPatient(admin);
    const doc = await findDoctor(admin, 'Kareem');
    const { visitId } = await startWalkInAndCheckIn(admin, patient.id, doc.id);
    await approvePendingPaymentFor(cashier, patient.mrn, 'INITIAL');

    const drug = await findActiveItem(admin, 'DRUG');
    const exam = await (await doctor.put(`${API_BASE}/exams`, {
      data: {
        visitId,
        chiefComplaint: 'fever',
        diagnoses: [{ code: null, description: 'Viral fever', primary: true, notes: null }],
        prescriptions: [{
          drugServiceItemId: drug.id, drugCode: drug.code, drugName: drug.nameEn,
          strength: null, dose: '500mg', frequency: 'TDS', duration: '5d', route: 'PO', notes: null,
        }],
      },
    })).json();
    await doctor.post(`${API_BASE}/exams/${exam.id}/finalize`);

    let dispense: any;
    await expect(async () => {
      const list = await pharmacist.get(`${API_BASE}/dispenses?status=PENDING&size=50`);
      dispense = (await list.json()).content.find((d: any) => d.patientMrn === patient.mrn);
      expect(dispense).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    await pharmacist.post(`${API_BASE}/dispenses/${dispense.id}/charge`);
    await rejectPendingPaymentFor(cashier, patient.mrn, 'PHARMACY', 'Patient changed their mind');

    await expect(async () => {
      const d = await (await pharmacist.get(`${API_BASE}/dispenses/${dispense.id}`)).json();
      expect(d.status).toBe('PENDING');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await cashier.dispose(); await doctor.dispose(); await pharmacist.dispose();
  });
});
