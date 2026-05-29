import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-005 — Premature Unit Visit (admission spine, sub-project A).
 */

async function startPrematureVisit(admin: import('@playwright/test').APIRequestContext) {
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const vr = await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'PREMATURE' },
  });
  expect(vr.status()).toBe(201);
  return { patient, visit: await vr.json() };
}

// Create a dedicated bed per test so specs don't compete for the small seeded bed pool
// (and aren't affected by beds left OCCUPIED by prior runs / other tests). The `premature`
// and `admin` users may create beds.
async function firstAvailableBedId(api: import('@playwright/test').APIRequestContext): Promise<string> {
  const code = `PREM-E2E-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const res = await api.post(`${API_BASE}/premature/beds`, { data: { code, room: 'E2E' } });
  if (!res.ok()) throw new Error(`create bed failed: ${res.status()} ${await res.text()}`);
  return (await res.json()).id;
}

async function pendingPayment(api: import('@playwright/test').APIRequestContext, visitId: string, stage: string) {
  const body = await (await api.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const p = body.content.find((x: any) => x.visitId === visitId && x.stage === stage);
  if (!p) throw new Error(`no pending ${stage} payment for visit ${visitId}`);
  return p;
}

test.describe('REC-005 Premature admission spine', () => {
  test('R1/R4: receptionist starts a PREMATURE visit; it appears in the incoming queue', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const { visit } = await startPrematureVisit(admin);
    expect(visit.visitType).toBe('PREMATURE');
    expect(visit.status).toBe('CREATED');

    const incoming = await (await premature.get(`${API_BASE}/visits?type=PREMATURE&status=CREATED&size=50`)).json();
    expect(incoming.content.some((v: any) => v.id === visit.id)).toBeTruthy();
    await admin.dispose(); await premature.dispose();
  });

  test('P1–P4a: assign bed → initial payment approved → bed OCCUPIED, visit IN_PROGRESS', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);

    const ar = await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 3, stayUnit: 'DAYS' },
    });
    expect(ar.status()).toBe(201);
    const admission = await ar.json();
    expect(admission.status).toBe('AWAITING_ADMISSION_PAYMENT');

    // Bed is reserved.
    const bedsAfterAssign = await (await premature.get(`${API_BASE}/premature/beds`)).json();
    expect(bedsAfterAssign.find((b: any) => b.id === bedId).status).toBe('PENDING_PAYMENT');

    // Approve initial payment.
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    expect((await cashier.post(`${API_BASE}/payments/${pay.id}/approve`, { data: { paymentMethod: 'CASH' } })).ok()).toBeTruthy();

    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
      const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('OCCUPIED');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('IN_PROGRESS');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });

  test('P4b: rejected initial payment releases the bed and cancels the visit', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 2, stayUnit: 'DAYS' },
    });
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${pay.id}/reject`, { data: { reason: 'cannot pay' } });

    await expect(async () => {
      const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('AVAILABLE');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('CANCELLED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });

  test('P8: doctor/nurse extends the period of stay', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');
    const nurse = await authedContext('nurse');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    const admission = await (await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 2, stayUnit: 'DAYS' },
    })).json();
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${pay.id}/approve`, { data: { paymentMethod: 'CASH' } });
    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    const ext = await nurse.post(`${API_BASE}/premature/admissions/${admission.id}/extend-stay`, {
      data: { value: 1, unit: 'DAYS' },
    });
    expect(ext.ok()).toBeTruthy();
    const after = await ext.json();
    expect(new Date(after.stayExpiresAt).getTime()).toBeGreaterThan(new Date(admission.stayExpiresAt).getTime());

    await admin.dispose(); await premature.dispose(); await cashier.dispose(); await nurse.dispose();
  });

  test('P9–P12a: finish treatment → final payment approved → case CLOSED, bed freed, visit COMPLETED', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    const admission = await (await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 1, stayUnit: 'DAYS' },
    })).json();
    const initial = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    const fin = await premature.post(`${API_BASE}/premature/admissions/${admission.id}/finish-treatment`, { data: {} });
    expect(fin.ok()).toBeTruthy();
    expect((await fin.json()).status).toBe('AWAITING_DISCHARGE_PAYMENT');

    const finalPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${finalPay.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const closed = await (await premature.get(`${API_BASE}/premature/admissions?status=CLOSED`)).json();
      expect(closed.some((a: any) => a.id === admission.id)).toBeTruthy();
      const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('AVAILABLE');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('COMPLETED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });

  test('P12b: rejected final payment keeps the case open and re-issuable', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    const admission = await (await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 1, stayUnit: 'DAYS' },
    })).json();
    const initial = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    await premature.post(`${API_BASE}/premature/admissions/${admission.id}/finish-treatment`, { data: {} });
    const finalPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${finalPay.id}/reject`, { data: { reason: 'pay tomorrow' } });

    // Case remains open; visit stays AWAITING_FINAL_PAYMENT (not OUTSTANDING_BALANCE).
    await expect(async () => {
      const open = await (await premature.get(`${API_BASE}/premature/admissions?status=AWAITING_DISCHARGE_PAYMENT`)).json();
      expect(open.some((a: any) => a.id === admission.id)).toBeTruthy();
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('AWAITING_FINAL_PAYMENT');
    }).toPass({ timeout: 10_000 });

    // Re-issue the discharge (final) payment, approve it, and the case closes.
    const reissue = await premature.post(`${API_BASE}/premature/admissions/${admission.id}/reissue-discharge-payment`, { data: {} });
    expect(reissue.ok()).toBeTruthy();

    // pendingPayment returns the first PENDING FINAL for this visit (the rejected one is filtered out by status=PENDING).
    const reissuedPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${reissuedPay.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const closed = await (await premature.get(`${API_BASE}/premature/admissions?status=CLOSED`)).json();
      expect(closed.some((a: any) => a.id === admission.id)).toBeTruthy();
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('COMPLETED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });
});
