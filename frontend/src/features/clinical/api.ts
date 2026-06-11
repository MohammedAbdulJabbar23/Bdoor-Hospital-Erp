import { api } from '@/shared/api/client';
import type { VisitType, VisitStatus } from '@/features/reception/visits/api';

export type ExamStatus = 'DRAFT' | 'FINALIZED';

export type Vitals = {
  systolicBp: number | null;
  diastolicBp: number | null;
  heartRate: number | null;
  respiratoryRate: number | null;
  temperatureC: number | null;
  oxygenSaturation: number | null;
  weightKg: number | null;
  heightCm: number | null;
  bmi: number | null;
  notes: string | null;
};

export type Diagnosis = {
  code: string | null;
  description: string;
  primary: boolean;
  notes: string | null;
};

export type Prescription = {
  drugServiceItemId: string | null;
  drugCode: string | null;
  drugName: string;
  strength: string | null;
  dose: string | null;
  frequency: string | null;
  duration: string | null;
  route: string | null;
  notes: string | null;
};

export type DoctorExam = {
  id: string;
  visitId: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  vitals: Vitals;
  chiefComplaint: string | null;
  historyOfPresentIllness: string | null;
  examinationNotes: string | null;
  plan: string | null;
  referralInstructions: string | null;
  diagnoses: Diagnosis[];
  prescriptions: Prescription[];
  status: ExamStatus;
  finalizedAt: string | null;
  finalizedBy: string | null;
  createdAt: string;
  updatedAt: string | null;
};

export type UpsertBody = {
  visitId: string;
  vitals?: Partial<Omit<Vitals, 'bmi'>>;
  chiefComplaint?: string;
  historyOfPresentIllness?: string;
  examinationNotes?: string;
  plan?: string;
  referralInstructions?: string;
  diagnoses?: Diagnosis[];
  prescriptions?: Prescription[];
};

export async function upsertExam(body: UpsertBody): Promise<DoctorExam> {
  const res = await api.put('/exams', body);
  return res.data;
}

export async function getExamByVisit(visitId: string): Promise<DoctorExam | null> {
  try {
    const res = await api.get(`/exams/by-visit/${visitId}`);
    return res.data;
  } catch (err: any) {
    if (err?.response?.status === 404) return null;
    throw err;
  }
}

export async function finalizeExam(examId: string): Promise<DoctorExam> {
  const res = await api.post(`/exams/${examId}/finalize`);
  return res.data;
}

export async function reopenExam(examId: string): Promise<DoctorExam> {
  const res = await api.post(`/exams/${examId}/reopen`);
  return res.data;
}

export type HistoryEntry = {
  visitId: string;
  visitDisplayId: string;
  visitType: VisitType;
  status: VisitStatus;
  parentVisitId: string | null;
  originatingType: VisitType | null;
  resultsSummary: string | null;
  startedAt: string;
  endedAt: string | null;
  exam: DoctorExam | null;
};

export type HistoryEntryType = 'VISIT' | 'ADMISSION' | 'EXAM' | 'FORM' | 'DOCUMENT' | 'ORDER';

export type TimelineEntry = {
  at: string;
  type: HistoryEntryType;
  /** VisitType name (PREMATURE, EMERGENCY, LABORATORY, …) or 'CLINICAL'. */
  department: string;
  title: string;
  detail: string | null;
  /** Localization kind (visit, examFinalized, admissionOpened, …) or null for legacy entries. */
  kind: string | null;
  /** Interpolation params for the kind's i18n template (visitType, displayId, bed, count, fileName). */
  params: Record<string, string> | null;
  refs: {
    visitId: string | null;
    stayId: string | null;
    documentId: string | null;
    fileUrl: string | null;
  };
};

export type PatientHistory = {
  patientId: string;
  totalVisits: number;
  entries: HistoryEntry[];
  timeline: TimelineEntry[];
};

export async function getPatientHistory(patientId: string): Promise<PatientHistory> {
  const res = await api.get(`/patients/${patientId}/clinical-history`);
  return res.data;
}

/* ---------------- Department case results (lab / radiology / eco / emergency) ---------------- */

export type DeptCategory = 'LAB' | 'RADIOLOGY' | 'ECO';
export type DeptCaseStatus =
  | 'NEW' | 'AWAITING_PAYMENT' | 'AWAITING_STUDY'
  | 'FINDINGS_COMPLETE' | 'CLOSED' | 'RETURNED' | 'CANCELLED';
export type LineStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export type DeptCaseLine = {
  serviceItemId: string;
  code: string;
  name: string;
  fee: number | null;
  status: LineStatus;
  textFindings: string | null;
  numericValue: number | null;
  unit: string | null;
  referenceRange: string | null;
  flag: string | null;
  measurements: string | null;
  bodyRegion: string | null;
  comments: string | null;
  diagnosis: string | null;
  uploadedAt: string | null;
  uploadedBy: string | null;
};

export type DeptCase = {
  id: string;
  category: DeptCategory;
  visitId: string;
  visitDisplayId: string;
  status: DeptCaseStatus;
  finalizedAt: string | null;
  resultsSummary: string | null;
  createdAt: string;
  services: DeptCaseLine[];
};

/* ---------------- Pharmacy dispense ---------------- */

export type DispenseStatus =
  | 'PENDING' | 'AWAITING_PAYMENT' | 'READY_TO_GIVE' | 'DISPENSED' | 'CANCELLED';

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
  doctorName: string;
  status: DispenseStatus;
  paidAt: string | null;
  givenAt: string | null;
  cancelledAt: string | null;
  cancelledReason: string | null;
  billableTotal: number;
  createdAt: string;
  lines: DispenseLine[];
};

/* ---------------- Aggregated record ---------------- */

export type PatientRecord = {
  patientId: string;
  totalVisits: number;
  visits: HistoryEntry[];
  departmentCases: DeptCase[];
  pharmacyDispenses: Dispense[];
};

export async function getPatientRecord(patientId: string): Promise<PatientRecord> {
  const res = await api.get(`/patients/${patientId}/record`);
  return res.data;
}

export async function getChildVisits(visitId: string): Promise<import('@/features/reception/visits/api').Visit[]> {
  const res = await api.get(`/visits/${visitId}/children`);
  return res.data;
}
