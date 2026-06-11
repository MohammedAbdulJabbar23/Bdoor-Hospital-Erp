import { api } from '@/shared/api/client';
import type { StayDepartment } from './api';

export type DocumentSource = 'UPLOAD' | 'LABORATORY' | 'RADIOLOGY' | 'ECO';

export type StayDoc = {
  id: string;
  source: DocumentSource;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  label: string | null;
  uploadedBy: string | null;
  uploadedAt: string;
  archived: boolean;
  fileUrl: string;
  sha256: string | null;
};

export async function listDocuments(dept: StayDepartment, stayId: string): Promise<StayDoc[]> {
  const res = await api.get(`/bed-stays/${dept}/${stayId}/documents`);
  return res.data;
}

export async function uploadDocument(
  dept: StayDepartment, stayId: string, file: File, label?: string,
): Promise<StayDoc> {
  const fd = new FormData();
  fd.append('file', file);
  if (label) fd.append('label', label);
  const res = await api.post(`/bed-stays/${dept}/${stayId}/documents`, fd,
    { headers: { 'Content-Type': 'multipart/form-data' } });
  return res.data;
}

export async function archiveDocument(dept: StayDepartment, stayId: string, id: string): Promise<StayDoc> {
  const res = await api.post(`/bed-stays/${dept}/${stayId}/documents/${id}/archive`, {});
  return res.data;
}

/** fileUrl from the API includes the /api prefix; the axios client baseURL is /api — strip it. */
export async function fetchDocumentBlobUrl(fileUrl: string): Promise<string> {
  const path = fileUrl.startsWith('/api/') ? fileUrl.slice(4) : fileUrl;
  const res = await api.get(path, { responseType: 'blob' });
  return URL.createObjectURL(res.data);
}
