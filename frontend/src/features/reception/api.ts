import { api } from '@/shared/api/client';

export type Gender = 'MALE' | 'FEMALE';
export type PatientType = 'ADULT' | 'INFANT';

export type PatientResponse = {
  id: string;
  mrn: string;
  type: PatientType;
  fullName: string;
  gender: Gender;
  dateOfBirth: string;
  vip: boolean;
  archived: boolean;
  adult: AdultPart | null;
  infant: InfantPart | null;
};

export type AdultPart = {
  nationalId: string | null;
  mobileNumber: string | null;
  address: string | null;
  occupation: string | null;
  emergencyContactName: string | null;
  emergencyContactMobile: string | null;
};

export type InfantPart = {
  motherPatientId: string | null;
  motherName: string | null;
  motherNationalId: string | null;
  motherMobile: string | null;
  fatherName: string | null;
  fatherMobile: string | null;
  dobTime: string | null;
  placeOfBirth: string | null;
  deliveryType: string | null;
  apgar1Min: number | null;
  apgar5Min: number | null;
  birthWeightKg: string | null;
  lengthCm: string | null;
  ofcCm: string | null;
  gestationalAgeWeeks: number | null;
  gestationalAgeDays: number | null;
  guardianName: string | null;
  guardianRelationship: string | null;
  guardianMobile: string | null;
  guardianNationalId: string | null;
};

export type RegisterAdultBody = {
  fullName: string;
  gender: Gender;
  dateOfBirth: string;
  nationalId?: string;
  mobileNumber?: string;
  address?: string;
  occupation?: string;
  emergencyContactName?: string;
  emergencyContactMobile?: string;
  vip: boolean;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function searchPatients(q: string, page = 0, size = 20): Promise<Page<PatientResponse>> {
  const res = await api.get('/patients', { params: { q, page, size } });
  return res.data;
}

export async function registerAdult(body: RegisterAdultBody): Promise<PatientResponse> {
  const res = await api.post('/patients', body);
  return res.data;
}

export async function toggleVip(id: string, vip: boolean): Promise<PatientResponse> {
  const res = await api.put(`/patients/${id}/vip`, { vip });
  return res.data;
}
