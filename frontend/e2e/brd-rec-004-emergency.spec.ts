import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-004 — Emergency Visit (admission spine).
 */

async function startEmergencyVisit(admin: import('@playwright/test').APIRequestContext) {
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const vr = await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'EMERGENCY' },
  });
  expect(vr.status()).toBe(201);
  return { patient, visit: await vr.json() };
}

// Create a dedicated bed per test so specs don't compete for the small seeded bed pool
// (and aren't affected by beds left OCCUPIED by prior runs / other tests).
async function freshBed(api: import('@playwright/test').APIRequestContext): Promise<string> {
  const code = `EMRG-E2E-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const res = await api.post(`${API_BASE}/emergency/beds`, { data: { code, room: 'E2E' } });
  if (!res.ok()) throw new Error(`create bed failed: ${res.status()} ${await res.text()}`);
  return (await res.json()).id;
}

async function firstService(api: import('@playwright/test').APIRequestContext): Promise<any> {
  const res = await api.get(`${API_BASE}/emergency/services`);
  if (!res.ok()) throw new Error(`list services failed: ${res.status()} ${await res.text()}`);
  const services = await res.json();
  if (!services.length) throw new Error('No emergency services found');
  return services[0];
}

async function serviceId(api: import('@playwright/test').APIRequestContext): Promise<string> {
  return (await firstService(api)).id;
}

async function pendingPayment(api: import('@playwright/test').APIRequestContext, visitId: string, stage: string) {
  const body = await (await api.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const p = body.content.find((x: any) => x.visitId === visitId && x.stage === stage);
  if (!p) throw new Error(`no pending ${stage} payment for visit ${visitId}`);
  return p;
}

test.describe('REC-004 Emergency admission spine', () => {
  test('R1: receptionist starts an EMERGENCY visit; it appears in the incoming queue', async () => {
    const admin = await authedContext('admin');
    const emergency = await authedContext('emergency');
    const { visit } = await startEmergencyVisit(admin);
    expect(visit.visitType).toBe('EMERGENCY');
    expect(visit.status).toBe('CREATED');

    const incoming = await (await emergency.get(`${API_BASE}/visits?type=EMERGENCY&status=CREATED&size=50`)).json();
    expect(incoming.content.some((v: any) => v.id === visit.id)).toBeTruthy();
    await admin.dispose(); await emergency.dispose();
  });

  test('P1–P4a: assign bed → initial payment approved → bed OCCUPIED, visit IN_PROGRESS', async () => {
    const admin = await authedContext('admin');
    const emergency = await authedContext('emergency');
    const cashier = await authedContext('cashier');

    const { visit } = await startEmergencyVisit(admin);
    const bedId = await freshBed(emergency);
    const service = await firstService(emergency);
    const svcId = service.id;
    const svcFee = Number(service.fee);

    // Assert initial pending payment exists before admission
    const ar = await emergency.post(`${API_BASE}/emergency/cases`, {
      data: { visitId: visit.id, bedId, serviceItemId: svcId, stayValue: 6, stayUnit: 'HOURS' },
    });
    expect(ar.status()).toBe(201);
    const emergCase = await ar.json();
    expect(emergCase.status).toBe('AWAITING_INITIAL_PAYMENT');

    // Verify INITIAL pending payment exists for this visit before approval, and that it
    // bills exactly the selected service's fee.
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    expect(pay).toBeDefined();
    expect(Math.abs(Number(pay.totalDue) - svcFee)).toBeLessThan(0.01);

    // Approve initial payment.
    expect(
      (await cashier.post(`${API_BASE}/payments/${pay.id}/approve`, { data: { paymentMethod: 'CASH' } })).ok(),
    ).toBeTruthy();

    await expect(async () => {
      const cases = await (await emergency.get(`${API_BASE}/emergency/cases?status=UNDER_TREATMENT`)).json();
      expect(cases.some((c: any) => c.id === emergCase.id)).toBeTruthy();
      const beds = await (await emergency.get(`${API_BASE}/emergency/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('OCCUPIED');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('IN_PROGRESS');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await emergency.dispose(); await cashier.dispose();
  });

  test('P4b: rejected initial payment releases the bed and cancels the visit', async () => {
    const admin = await authedContext('admin');
    const emergency = await authedContext('emergency');
    const cashier = await authedContext('cashier');

    const { visit } = await startEmergencyVisit(admin);
    const bedId = await freshBed(emergency);
    const svcId = await serviceId(emergency);

    await emergency.post(`${API_BASE}/emergency/cases`, {
      data: { visitId: visit.id, bedId, serviceItemId: svcId, stayValue: 6, stayUnit: 'HOURS' },
    });
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${pay.id}/reject`, { data: { reason: 'cannot pay' } });

    await expect(async () => {
      const beds = await (await emergency.get(`${API_BASE}/emergency/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('AVAILABLE');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('CANCELLED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await emergency.dispose(); await cashier.dispose();
  });

  test('P8: nurse extends the emergency stay', async () => {
    const admin = await authedContext('admin');
    const emergency = await authedContext('emergency');
    const cashier = await authedContext('cashier');
    const nurse = await authedContext('nurse');

    const { visit } = await startEmergencyVisit(admin);
    const bedId = await freshBed(emergency);
    const svcId = await serviceId(emergency);

    const emergCase = await (await emergency.post(`${API_BASE}/emergency/cases`, {
      data: { visitId: visit.id, bedId, serviceItemId: svcId, stayValue: 6, stayUnit: 'HOURS' },
    })).json();

    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${pay.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const cases = await (await emergency.get(`${API_BASE}/emergency/cases?status=UNDER_TREATMENT`)).json();
      expect(cases.some((c: any) => c.id === emergCase.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    const ext = await nurse.post(`${API_BASE}/emergency/cases/${emergCase.id}/extend-stay`, {
      data: { value: 1, unit: 'DAYS' },
    });
    expect(ext.ok()).toBeTruthy();
    const after = await ext.json();
    expect(new Date(after.stayExpiresAt).getTime()).toBeGreaterThan(new Date(emergCase.stayExpiresAt).getTime());

    await admin.dispose(); await emergency.dispose(); await cashier.dispose(); await nurse.dispose();
  });

  test('P9–P12a: finish treatment → final payment approved → case CLOSED, bed freed, visit COMPLETED', async () => {
    const admin = await authedContext('admin');
    const emergency = await authedContext('emergency');
    const cashier = await authedContext('cashier');

    const { visit } = await startEmergencyVisit(admin);
    const bedId = await freshBed(emergency);
    const svcId = await serviceId(emergency);

    const emergCase = await (await emergency.post(`${API_BASE}/emergency/cases`, {
      data: { visitId: visit.id, bedId, serviceItemId: svcId, stayValue: 6, stayUnit: 'HOURS' },
    })).json();

    const initial = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const cases = await (await emergency.get(`${API_BASE}/emergency/cases?status=UNDER_TREATMENT`)).json();
      expect(cases.some((c: any) => c.id === emergCase.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    const fin = await emergency.post(`${API_BASE}/emergency/cases/${emergCase.id}/finish-treatment`, { data: {} });
    expect(fin.ok()).toBeTruthy();
    expect((await fin.json()).status).toBe('AWAITING_DISCHARGE_PAYMENT');

    const finalPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${finalPay.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const closed = await (await emergency.get(`${API_BASE}/emergency/cases?status=CLOSED`)).json();
      expect(closed.some((c: any) => c.id === emergCase.id)).toBeTruthy();
      const beds = await (await emergency.get(`${API_BASE}/emergency/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('AVAILABLE');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('COMPLETED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await emergency.dispose(); await cashier.dispose();
  });

  test('P12b: rejected final payment keeps the case open and re-issuable', async () => {
    const admin = await authedContext('admin');
    const emergency = await authedContext('emergency');
    const cashier = await authedContext('cashier');

    const { visit } = await startEmergencyVisit(admin);
    const bedId = await freshBed(emergency);
    const svcId = await serviceId(emergency);

    const emergCase = await (await emergency.post(`${API_BASE}/emergency/cases`, {
      data: { visitId: visit.id, bedId, serviceItemId: svcId, stayValue: 6, stayUnit: 'HOURS' },
    })).json();

    const initial = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const cases = await (await emergency.get(`${API_BASE}/emergency/cases?status=UNDER_TREATMENT`)).json();
      expect(cases.some((c: any) => c.id === emergCase.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    await emergency.post(`${API_BASE}/emergency/cases/${emergCase.id}/finish-treatment`, { data: {} });
    const finalPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${finalPay.id}/reject`, { data: { reason: 'pay tomorrow' } });

    // Case remains open; visit stays AWAITING_FINAL_PAYMENT.
    await expect(async () => {
      const open = await (await emergency.get(`${API_BASE}/emergency/cases?status=AWAITING_DISCHARGE_PAYMENT`)).json();
      expect(open.some((c: any) => c.id === emergCase.id)).toBeTruthy();
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('AWAITING_FINAL_PAYMENT');
    }).toPass({ timeout: 10_000 });

    // Re-issue the discharge payment, approve it, and the case closes.
    const reissue = await emergency.post(`${API_BASE}/emergency/cases/${emergCase.id}/reissue-discharge-payment`, { data: {} });
    expect(reissue.ok()).toBeTruthy();

    // pendingPayment returns the first PENDING FINAL for this visit (the rejected one is filtered out by status=PENDING).
    const reissuedPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${reissuedPay.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const closed = await (await emergency.get(`${API_BASE}/emergency/cases?status=CLOSED`)).json();
      expect(closed.some((c: any) => c.id === emergCase.id)).toBeTruthy();
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('COMPLETED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await emergency.dispose(); await cashier.dispose();
  });
});
