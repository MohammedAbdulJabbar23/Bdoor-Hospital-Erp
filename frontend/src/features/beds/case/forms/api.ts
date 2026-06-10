import { api } from '@/shared/api/client';

export type StayDepartment = 'PREMATURE' | 'EMERGENCY';
export type MhSlot = 'SPECIALIST' | 'PERMANENT' | 'RESIDENT';

export type StayPrefill = {
  patientId: string;
  patientName: string;
  patientMrn: string;
  ageText: string | null;
  admittedAt: string;
};

export type SignatureView = { signerName: string | null; signedAt: string | null; present: boolean };

export type MedicalHistory = {
  weightKg: number | null;
  heightCm: number | null;
  doctorName: string | null;
  chiefComplaint: string | null;
  presentIllnessHx: string | null;
  psHx: string | null;
  pmHx: string | null;
  familyHx: string | null;
  allergicHx: string | null;
  socialSmoker: string | null;
  socialAlcohol: string | null;
  socialSleep: string | null;
  drugHx: string | null;
  physicalExamination: string | null;
  specialistSignature: SignatureView;
  permanentSignature: SignatureView;
  residentSignature: SignatureView;
};

export type MedicalHistoryResponse = { prefill: StayPrefill; form: MedicalHistory | null };

export type NursingProcedure = {
  id: string;
  procedureName: string;
  performedAt: string;
  note: string | null;
  nurseName: string | null;
  recordedAt: string;
};

export type TreatmentRow = {
  medicineName: string;
  dose: string | null;
  frequency: string | null;
  timing: (string | null)[];
};

export type TreatmentChart = { chartDate: string; rows: TreatmentRow[]; doctorSignature: SignatureView };

const base = (dept: StayDepartment, stayId: string) => `/bed-stays/${dept}/${stayId}`;

export async function getMedicalHistory(dept: StayDepartment, stayId: string): Promise<MedicalHistoryResponse> {
  const res = await api.get(`${base(dept, stayId)}/medical-history`);
  return res.data;
}

export async function saveMedicalHistory(
  dept: StayDepartment, stayId: string, body: Record<string, unknown>,
): Promise<MedicalHistory> {
  const res = await api.put(`${base(dept, stayId)}/medical-history`, body);
  return res.data;
}

export async function uploadMhSignature(
  dept: StayDepartment, stayId: string, slot: MhSlot, blob: Blob, signerName?: string,
): Promise<SignatureView> {
  const fd = new FormData();
  fd.append('file', blob, 'signature.png');
  if (signerName) fd.append('signerName', signerName);
  const res = await api.post(`${base(dept, stayId)}/medical-history/signatures/${slot}`, fd,
    { headers: { 'Content-Type': 'multipart/form-data' } });
  return res.data;
}

export async function listNursingProcedures(dept: StayDepartment, stayId: string): Promise<NursingProcedure[]> {
  const res = await api.get(`${base(dept, stayId)}/nursing-procedures`);
  return res.data;
}

export async function addNursingProcedure(
  dept: StayDepartment, stayId: string,
  body: { procedureName: string; performedAt: string; note?: string },
): Promise<NursingProcedure> {
  const res = await api.post(`${base(dept, stayId)}/nursing-procedures`, body);
  return res.data;
}

export async function listTreatmentCharts(dept: StayDepartment, stayId: string): Promise<TreatmentChart[]> {
  const res = await api.get(`${base(dept, stayId)}/treatment-charts`);
  return res.data;
}

export async function saveTreatmentChart(
  dept: StayDepartment, stayId: string, date: string,
  rows: { medicineName: string; dose?: string; frequency?: string; timing?: (string | null)[] }[],
): Promise<TreatmentChart> {
  const res = await api.put(`${base(dept, stayId)}/treatment-charts/${date}`, { rows });
  return res.data;
}

export async function uploadChartSignature(
  dept: StayDepartment, stayId: string, date: string, blob: Blob, signerName?: string,
): Promise<SignatureView> {
  const fd = new FormData();
  fd.append('file', blob, 'signature.png');
  if (signerName) fd.append('signerName', signerName);
  const res = await api.post(`${base(dept, stayId)}/treatment-charts/${date}/signature`, fd,
    { headers: { 'Content-Type': 'multipart/form-data' } });
  return res.data;
}
