import { APIRequestContext } from '@playwright/test';

const API = 'http://localhost:8080/api';

export type Seeded = {
  patient: any;
  doctor: any;
};

export async function registerPatient(api: APIRequestContext, opts: { vip?: boolean; gender?: 'MALE' | 'FEMALE' } = {}): Promise<any> {
  const stamp = Date.now() + Math.floor(Math.random() * 10_000);
  const res = await api.post(`${API}/patients`, {
    data: {
      fullName: `Test Patient ${stamp}`,
      gender: opts.gender ?? 'MALE',
      dateOfBirth: '1985-07-20',
      mobileNumber: `077${String(stamp).slice(-7)}`,
      vip: opts.vip ?? false,
    },
  });
  if (!res.ok()) throw new Error(`registerPatient failed: ${res.status()} ${await res.text()}`);
  return await res.json();
}

export async function findDoctor(api: APIRequestContext, fullNameFragment: string): Promise<any> {
  const res = await api.get(`${API}/doctors`);
  const list = await res.json();
  const d = list.find((x: any) => x.fullName.toLowerCase().includes(fullNameFragment.toLowerCase()));
  if (!d) throw new Error(`No doctor matching ${fullNameFragment}`);
  return d;
}

export async function startWalkInAndCheckIn(api: APIRequestContext, patientId: string, doctorId: string): Promise<{ appt: any; visitId: string }> {
  const aRes = await api.post(`${API}/appointments`, {
    data: { patientId, doctorId, type: 'WALKIN' },
  });
  if (!aRes.ok()) throw new Error(`book appt failed: ${aRes.status()} ${await aRes.text()}`);
  const appt = await aRes.json();
  const cRes = await api.post(`${API}/appointments/${appt.id}/check-in`);
  if (!cRes.ok()) throw new Error(`check-in failed: ${cRes.status()} ${await cRes.text()}`);
  return { appt, visitId: appt.visitId };
}

export async function approvePendingPaymentFor(api: APIRequestContext, patientMrn: string, stage: 'INITIAL' | 'REFERRAL' | 'PHARMACY' | 'FINAL'): Promise<any> {
  const list = await api.get(`${API}/payments?status=PENDING&size=100`);
  const body = await list.json();
  const p = body.content.find((x: any) => x.patientMrn === patientMrn && x.stage === stage);
  if (!p) throw new Error(`No PENDING ${stage} payment for ${patientMrn}`);
  const ar = await api.post(`${API}/payments/${p.id}/approve`, { data: { paymentMethod: 'CASH' } });
  if (!ar.ok()) throw new Error(`approve failed: ${ar.status()} ${await ar.text()}`);
  return await ar.json();
}

export async function rejectPendingPaymentFor(api: APIRequestContext, patientMrn: string, stage: string, reason: string): Promise<any> {
  const list = await api.get(`${API}/payments?status=PENDING&size=100`);
  const body = await list.json();
  const p = body.content.find((x: any) => x.patientMrn === patientMrn && x.stage === stage);
  if (!p) throw new Error(`No PENDING ${stage} payment for ${patientMrn}`);
  const rr = await api.post(`${API}/payments/${p.id}/reject`, { data: { reason } });
  if (!rr.ok()) throw new Error(`reject failed: ${rr.status()} ${await rr.text()}`);
  return await rr.json();
}

export async function findActiveItem(api: APIRequestContext, category: 'LAB' | 'IMAGING' | 'ECO' | 'DRUG'): Promise<any> {
  const r = await api.get(`${API}/catalogue/items?category=${category}&activeOnly=true`);
  const items = await r.json();
  if (!items.length) throw new Error(`No active catalogue items for ${category}`);
  return items[0];
}

export const API_BASE = API;
