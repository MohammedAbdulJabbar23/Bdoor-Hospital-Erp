import { api } from '@/shared/api/client';

export type DispenseStatus =
  | 'PENDING'
  | 'AWAITING_PAYMENT'
  | 'READY_TO_GIVE'
  | 'DISPENSED'
  | 'CANCELLED';

export type DispenseLine = {
  drugServiceItemId: string | null;
  drugCode: string | null;
  drugName: string;
  strength: string | null;
  dose: string | null;
  frequency: string | null;
  duration: string | null;
  route: string | null;
  notes: string | null;
  unitFee: number | null;
  quantity: number;
  lineTotal: number | null;
  billable: boolean;
};

export type Dispense = {
  id: string;
  dispenseDisplayId: string;
  examId: string;
  visitId: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  status: DispenseStatus;
  chargePaymentId: string | null;
  chargedAt: string | null;
  paidAt: string | null;
  givenAt: string | null;
  givenByUserId: string | null;
  cancelledAt: string | null;
  cancelledReason: string | null;
  billableTotal: number;
  createdAt: string;
  lines: DispenseLine[];
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function searchDispenses(status: DispenseStatus | null, page = 0, size = 50): Promise<Page<Dispense>> {
  const params: Record<string, string | number> = { page, size };
  if (status) params.status = status;
  const res = await api.get('/dispenses', { params });
  return res.data;
}

export async function getDispense(id: string): Promise<Dispense> {
  const res = await api.get(`/dispenses/${id}`);
  return res.data;
}

export async function chargeDispense(id: string): Promise<Dispense> {
  const res = await api.post(`/dispenses/${id}/charge`);
  return res.data;
}

export async function markGivenDispense(id: string): Promise<Dispense> {
  const res = await api.post(`/dispenses/${id}/mark-given`);
  return res.data;
}

export async function cancelDispense(id: string, reason: string): Promise<Dispense> {
  const res = await api.post(`/dispenses/${id}/cancel`, { reason });
  return res.data;
}

export async function getDispensesByPatient(patientId: string): Promise<Dispense[]> {
  const res = await api.get(`/dispenses/by-patient/${patientId}`);
  return res.data;
}
