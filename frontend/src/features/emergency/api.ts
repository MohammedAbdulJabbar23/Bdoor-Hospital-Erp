import { api } from '@/shared/api/client';
import type { VisitType } from '@/features/reception/visits/api';

export type BedStatus = 'AVAILABLE' | 'PENDING_PAYMENT' | 'OCCUPIED';
export type StayUnit = 'HOURS' | 'DAYS';
export type CaseStatus =
  | 'AWAITING_INITIAL_PAYMENT'
  | 'UNDER_TREATMENT'
  | 'TREATMENT_FINISHED'
  | 'AWAITING_DISCHARGE_PAYMENT'
  | 'CLOSED'
  | 'CANCELLED';

export type EmergencyService = {
  id: string;
  code: string;
  nameEn: string;
  nameAr: string;
  fee: number;
  currency: string;
};

export type BedOccupant = {
  caseId: string;
  visitId: string;
  visitDisplayId: string;
  patientName: string;
  patientMrn: string;
  caseStatus: CaseStatus;
  stayExpiresAt: string;
};

export type Bed = {
  id: string;
  code: string;
  room: string | null;
  status: BedStatus;
  active: boolean;
  occupant: BedOccupant | null;
};

export type Case = {
  id: string;
  visitId: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  bedId: string;
  bedCode: string;
  serviceCode: string;
  serviceName: string;
  status: CaseStatus;
  stayValue: number;
  stayUnit: StayUnit;
  admittedAt: string;
  stayExpiresAt: string;
  treatmentFinishedAt: string | null;
  closedAt: string | null;
  initialPaymentId: string | null;
  finalPaymentId: string | null;
  dischargeNote?: string | null;
};

export type EmergencyVisit = {
  id: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  visitType: VisitType;
  status: string;
  startedAt: string;
};

export async function listEmergencyServices(): Promise<EmergencyService[]> {
  const res = await api.get('/emergency/services');
  return res.data;
}

export async function listBeds(): Promise<Bed[]> {
  const res = await api.get('/emergency/beds');
  return res.data;
}

export async function createBed(body: { code: string; room?: string }): Promise<Bed> {
  const res = await api.post('/emergency/beds', body);
  return res.data;
}

export async function updateBed(id: string, body: { room?: string; active: boolean }): Promise<Bed> {
  const res = await api.put(`/emergency/beds/${id}`, body);
  return res.data;
}

export async function listCases(status?: CaseStatus): Promise<Case[]> {
  const params = status ? { status } : {};
  const res = await api.get('/emergency/cases', { params });
  return res.data;
}

/** Incoming queue = EMERGENCY visits not yet assigned a case (CREATED). */
export async function listIncomingEmergency(): Promise<EmergencyVisit[]> {
  const res = await api.get('/visits', { params: { type: 'EMERGENCY', status: 'CREATED', size: 50 } });
  return (res.data.content ?? []) as EmergencyVisit[];
}

export async function admitPatient(body: {
  visitId: string;
  bedId: string;
  serviceItemId: string;
  stayValue: number;
  stayUnit: StayUnit;
}): Promise<Case> {
  const res = await api.post('/emergency/cases', body);
  return res.data;
}

export async function extendStay(caseId: string, value: number, unit: StayUnit): Promise<Case> {
  const res = await api.post(`/emergency/cases/${caseId}/extend-stay`, { value, unit });
  return res.data;
}

export async function finishTreatment(caseId: string, override = false, overrideReason?: string): Promise<Case> {
  const res = await api.post(`/emergency/cases/${caseId}/finish-treatment`, { override, overrideReason });
  return res.data;
}

export type Order = {
  visitId: string;
  visitDisplayId: string;
  visitType: string;
  status: string;
  resultsSummary?: string | null;
  startedAt: string;
};

export async function listOrders(caseId: string): Promise<Order[]> {
  const res = await api.get(`/emergency/cases/${caseId}/orders`);
  return res.data;
}

export async function orderWorkup(caseId: string, targetType: 'LABORATORY' | 'RADIOLOGY' | 'ECO'): Promise<Order> {
  const res = await api.post(`/emergency/cases/${caseId}/orders`, { targetType });
  return res.data;
}

export async function setDischargeNote(caseId: string, note: string): Promise<Case> {
  const res = await api.post(`/emergency/cases/${caseId}/discharge-note`, { note });
  return res.data;
}

export async function reissueDischargePayment(caseId: string): Promise<Case> {
  const res = await api.post(`/emergency/cases/${caseId}/reissue-discharge-payment`, {});
  return res.data;
}
