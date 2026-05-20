import { api } from '@/shared/api/client';
import type { Visit, VisitOrigin } from '@/features/reception/visits/api';

export type DepartmentCategory = 'LAB' | 'RADIOLOGY' | 'ECO';

export type CaseLineStatus = 'PENDING' | 'RESULT_UPLOADED';

export type CaseStatus =
  | 'NEW' | 'AWAITING_PAYMENT' | 'AWAITING_STUDY'
  | 'FINDINGS_COMPLETE' | 'CLOSED' | 'RETURNED' | 'CANCELLED';

export type CaseLine = {
  serviceItemId: string;
  code: string;
  name: string;
  fee: number | null;
  status: CaseLineStatus;
  textFindings: string | null;
  numericValue: number | null;
  unit: string | null;
  referenceRange: string | null;
  flag: string | null;
  measurements: string | null;
  comments: string | null;
  diagnosis: string | null;
  uploadedAt: string | null;
  uploadedBy: string | null;
};

export type DepartmentCase = {
  id: string;
  category: DepartmentCategory;
  visitId: string;
  visitDisplayId: string;
  visitOrigin: VisitOrigin;
  parentVisitId: string | null;
  patientId: string;
  patientMrn: string;
  patientName: string;
  status: CaseStatus;
  paymentId: string | null;
  finalizedAt: string | null;
  resultsSummary: string | null;
  createdAt: string;
  services: CaseLine[];
};

export async function listCases(category: DepartmentCategory, status?: CaseStatus): Promise<DepartmentCase[]> {
  const params: Record<string, string> = { category };
  if (status) params.status = status;
  const res = await api.get('/dept-cases', { params });
  return res.data;
}

export async function getCase(id: string): Promise<DepartmentCase> {
  const res = await api.get(`/dept-cases/${id}`);
  return res.data;
}

export async function getCaseByVisit(visitId: string): Promise<DepartmentCase | null> {
  try {
    const res = await api.get(`/dept-cases/by-visit/${visitId}`);
    return res.data;
  } catch (err: any) {
    if (err?.response?.status === 404) return null;
    throw err;
  }
}

export async function openCase(body: {
  category: DepartmentCategory;
  visitId: string;
  services: { serviceItemId: string; quantity: number }[];
}): Promise<DepartmentCase> {
  const res = await api.post('/dept-cases/open', body);
  return res.data;
}

export type FindingsBody = {
  serviceItemId: string;
  textFindings?: string;
  numericValue?: number;
  unit?: string;
  referenceRange?: string;
  flag?: string;
  measurements?: string;
  /** REC-003 §7.1 — Radiology body region (e.g. "Chest", "Right knee"). */
  bodyRegion?: string;
  comments?: string;
  diagnosis?: string;
};

export async function uploadFindings(caseId: string, body: FindingsBody): Promise<DepartmentCase> {
  const res = await api.post(`/dept-cases/${caseId}/findings`, body);
  return res.data;
}


export async function finalizeCase(caseId: string): Promise<DepartmentCase> {
  const res = await api.post(`/dept-cases/${caseId}/finalize`);
  return res.data;
}

/* ---------------- Case attachments ---------------- */

export type CaseAttachment = {
  id: string;
  caseId: string;
  serviceItemId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
  uploadedBy: string | null;
};

export async function listCaseAttachments(caseId: string): Promise<CaseAttachment[]> {
  const res = await api.get(`/dept-cases/${caseId}/attachments`);
  return res.data;
}

export async function uploadAttachment(
  caseId: string,
  serviceItemId: string,
  file: File,
): Promise<CaseAttachment> {
  const fd = new FormData();
  fd.append('file', file);
  const res = await api.post(
    `/dept-cases/${caseId}/services/${serviceItemId}/attachments`,
    fd,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return res.data;
}

export async function deleteAttachment(id: string): Promise<void> {
  await api.delete(`/dept-cases/attachments/${id}`);
}

export function attachmentDownloadUrl(id: string): string {
  // Backend base is configured in axios client; just return the relative path.
  return `/api/dept-cases/attachments/${id}/file`;
}

export type IncomingVisit = Visit & { hasCase?: boolean };

/** Lists visits of a given visit-type that aren't terminal — the dept's incoming queue. */
export async function listIncomingVisits(
  visitType: 'LABORATORY' | 'RADIOLOGY' | 'ECO',
): Promise<Visit[]> {
  // Reuse the visit search; client filters out terminal statuses.
  const res = await api.get('/visits', { params: { type: visitType, size: 50 } });
  const page = res.data as { content: Visit[] };
  return page.content.filter((v) => v.status !== 'COMPLETED' && v.status !== 'CANCELLED');
}
