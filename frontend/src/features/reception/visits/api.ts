import { api } from '@/shared/api/client';

export type VisitType =
  | 'DOCTOR_APPOINTMENT'
  | 'LABORATORY'
  | 'RADIOLOGY'
  | 'ECO'
  | 'EMERGENCY'
  | 'PREMATURE'
  | 'PHARMACY';

export type VisitOrigin = 'DIRECT_NEW' | 'DIRECT_RETURNING' | 'FORWARDED';

export type VisitStatus =
  | 'CREATED'
  | 'AWAITING_PAYMENT'
  | 'IN_PROGRESS'
  | 'AWAITING_RESULTS'
  | 'TREATMENT_FINISHED'
  | 'AWAITING_FINAL_PAYMENT'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'OUTSTANDING_BALANCE';

export type Visit = {
  id: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  visitType: VisitType;
  origin: VisitOrigin;
  status: VisitStatus;
  parentVisitId: string | null;
  originatingType: VisitType | null;
  assignedDoctorId: string | null;
  startedAt: string;
  endedAt: string | null;
  closureReason: string | null;
  resultsSummary: string | null;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function searchVisits(
  type: VisitType | null,
  status: VisitStatus | null,
  page = 0,
  size = 20,
): Promise<Page<Visit>> {
  const params: Record<string, string | number> = { page, size };
  if (type) params.type = type;
  if (status) params.status = status;
  const res = await api.get('/visits', { params });
  return res.data;
}

export async function createVisit(
  patientId: string,
  visitType: VisitType,
): Promise<Visit> {
  const res = await api.post('/visits', { patientId, visitType });
  return res.data;
}

export async function transitionVisit(
  id: string,
  target: VisitStatus,
  reason?: string,
): Promise<Visit> {
  const res = await api.post(`/visits/${id}/transition`, { target, reason });
  return res.data;
}

export async function forwardVisit(
  id: string,
  targetType: VisitType,
): Promise<{ parent: Visit; child: Visit }> {
  const res = await api.post(`/visits/${id}/forward`, { targetType });
  return res.data;
}

export async function returnVisit(
  id: string,
  resultsSummary: string,
): Promise<{ parent: Visit; child: Visit }> {
  const res = await api.post(`/visits/${id}/return`, { resultsSummary });
  return res.data;
}
