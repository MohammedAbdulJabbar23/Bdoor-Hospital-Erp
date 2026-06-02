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
  dischargeNote?: string | null;
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

export async function finishTreatment(admissionId: string, override = false, overrideReason?: string): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/finish-treatment`, { override, overrideReason });
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

export async function listOrders(admissionId: string): Promise<Order[]> {
  const res = await api.get(`/premature/admissions/${admissionId}/orders`);
  return res.data;
}

export async function orderWorkup(admissionId: string, targetType: 'LABORATORY' | 'RADIOLOGY' | 'ECO'): Promise<Order> {
  const res = await api.post(`/premature/admissions/${admissionId}/orders`, { targetType });
  return res.data;
}

export async function setDischargeNote(admissionId: string, note: string): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/discharge-note`, { note });
  return res.data;
}

export async function reissueDischargePayment(admissionId: string): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/reissue-discharge-payment`, {});
  return res.data;
}

export type RespSupport = 'MV' | 'CPAP' | 'HFNC' | 'NC' | 'ROOM_AIR';
export type TourType = 'MORNING' | 'NIGHT';
export type SignatureSlot = 'CLINICAL_PHARMACY' | 'RESIDENT' | 'SENIOR_RESIDENT';

export type PrematureForm = {
  id: string; admissionId: string; ageText: string | null;
  birthWeightKg: number | null; birthWeightDate: string | null;
  currentWeightKg: number | null; currentWeightDate: string | null;
  gestationalAgeWeeks: number | null; gestationalAgeDays: number | null;
  correctedGaWeeks: number | null; correctedGaDays: number | null;
  lengthCm: number | null; lengthDate: string | null; ofcCm: number | null; ofcDate: string | null;
  feedingType: string | null; kcalPerOz: number | null; enteralPerKg: number | null;
  kcalPerKg: number | null; gir: number | null; pharmacyOthers: string | null;
  lastCultureDate: string | null; sampleType: string | null; cultureResult: string | null;
  prescriptionNotes: string | null; specialistDoctorNotes: string | null;
  clinicalPharmacySignature: SigMeta; residentSignature: SigMeta; seniorResidentSignature: SigMeta;
};
export type SigMeta = { present: boolean; signerName: string | null; signedBy: string | null; signedAt: string | null };

export type Tour = {
  id: string; tourType: TourType; recordedAt: string; recordedBy: string | null;
  respRate: number | null; spo2: number | null; pulseRate: number | null; respSupport: RespSupport[];
  bowelMotion: string | null; uop: string | null; feeding: string | null; vomiting: string | null;
  jaundice: string | null; ivAccess: string | null; ivFluid: string | null;
  babyTempC: number | null; incubatorTempC: number | null; humidity: number | null;
  nasalSeptum: string | null; rbs: number | null; others: string | null;
};

export type Prefill = {
  ageText: string | null; birthWeightKg: number | null;
  gestationalAgeWeeks: number | null; gestationalAgeDays: number | null;
  lengthCm: number | null; ofcCm: number | null;
};

export type PrematureCase = { admission: Admission; form: PrematureForm | null; prefill: Prefill; tours: Tour[] };

export async function getPrematureCase(admissionId: string): Promise<PrematureCase> {
  const res = await api.get(`/premature/admissions/${admissionId}/case`);
  return res.data;
}
export async function upsertPrematureForm(admissionId: string, body: Record<string, unknown>): Promise<PrematureForm> {
  const res = await api.put(`/premature/admissions/${admissionId}/form`, body);
  return res.data;
}
export async function recordTour(admissionId: string, body: Record<string, unknown>): Promise<Tour> {
  const res = await api.post(`/premature/admissions/${admissionId}/tours`, body);
  return res.data;
}
export async function uploadSignature(admissionId: string, slot: SignatureSlot, file: Blob, signerName: string): Promise<void> {
  const fd = new FormData();
  fd.append('file', file, 'signature.png');
  fd.append('signerName', signerName);
  await api.post(`/premature/admissions/${admissionId}/form/signatures/${slot}`, fd,
    { headers: { 'Content-Type': 'multipart/form-data' } });
}
/** Fetch a stored signature as a blob object-URL (the <img> can't carry the Bearer token). */
export async function fetchSignatureUrl(admissionId: string, slot: SignatureSlot): Promise<string> {
  const res = await api.get(`/premature/admissions/${admissionId}/form/signatures/${slot}`, { responseType: 'blob' });
  return URL.createObjectURL(res.data as Blob);
}
