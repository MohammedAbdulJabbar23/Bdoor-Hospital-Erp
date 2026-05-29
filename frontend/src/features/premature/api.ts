import { api } from '@/shared/api/client';
import type { VisitType } from '@/features/reception/visits/api';

export type BedStatus = 'AVAILABLE' | 'PENDING_PAYMENT' | 'OCCUPIED';
export type StayUnit = 'HOURS' | 'DAYS';
export type AdmissionStatus =
  | 'AWAITING_ADMISSION_PAYMENT'
  | 'UNDER_CARE'
  | 'TREATMENT_FINISHED'
  | 'AWAITING_DISCHARGE_PAYMENT'
  | 'CLOSED'
  | 'CANCELLED';

export type BedOccupant = {
  admissionId: string;
  visitId: string;
  visitDisplayId: string;
  patientName: string;
  patientMrn: string;
  admissionStatus: AdmissionStatus;
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

export type Admission = {
  id: string;
  visitId: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  bedId: string;
  bedCode: string;
  status: AdmissionStatus;
  stayValue: number;
  stayUnit: StayUnit;
  admittedAt: string;
  stayExpiresAt: string;
  treatmentFinishedAt: string | null;
  closedAt: string | null;
  initialPaymentId: string | null;
  finalPaymentId: string | null;
};

export type PrematureVisit = {
  id: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  visitType: VisitType;
  status: string;
  startedAt: string;
};

export async function listBeds(): Promise<Bed[]> {
  const res = await api.get('/premature/beds');
  return res.data;
}

export async function createBed(body: { code: string; room?: string }): Promise<Bed> {
  const res = await api.post('/premature/beds', body);
  return res.data;
}

export async function updateBed(id: string, body: { room?: string; active: boolean }): Promise<Bed> {
  const res = await api.put(`/premature/beds/${id}`, body);
  return res.data;
}

export async function listAdmissions(status?: AdmissionStatus): Promise<Admission[]> {
  const params = status ? { status } : {};
  const res = await api.get('/premature/admissions', { params });
  return res.data;
}

/** Incoming queue = PREMATURE visits not yet admitted (CREATED). */
export async function listIncomingPremature(): Promise<PrematureVisit[]> {
  const res = await api.get('/visits', { params: { type: 'PREMATURE', status: 'CREATED', size: 50 } });
  return (res.data.content ?? []) as PrematureVisit[];
}

export async function admitPatient(body: {
  visitId: string;
  bedId: string;
  stayValue: number;
  stayUnit: StayUnit;
}): Promise<Admission> {
  const res = await api.post('/premature/admissions', body);
  return res.data;
}

export async function extendStay(admissionId: string, value: number, unit: StayUnit): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/extend-stay`, { value, unit });
  return res.data;
}

export async function finishTreatment(admissionId: string): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/finish-treatment`, {});
  return res.data;
}
