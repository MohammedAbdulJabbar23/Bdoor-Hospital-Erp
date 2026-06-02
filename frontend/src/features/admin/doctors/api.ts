import { api } from '@/shared/api/client';
import { Doctor, DayOfWeek } from '@/features/reception/appointments/api';

export type { Doctor };

export type CreateDoctorBody = {
  userId?: string;
  fullName: string;
  specialty?: string;
  consultationFee?: number;
  currency?: string;
  phone?: string;
};

export type ScheduleBlock = {
  dayOfWeek: DayOfWeek;
  startTime: string; // HH:mm
  endTime: string;   // HH:mm
  slotMinutes: number;
};

export async function createDoctor(body: CreateDoctorBody): Promise<Doctor> {
  const res = await api.post('/doctors', body);
  return res.data;
}

export type UpdateDoctorBody = {
  fullName?: string;
  specialty?: string;
  consultationFee?: number;
  currency?: string;
  phone?: string;
};

/** PUT /api/doctors/{id} — update profile fields (ADMIN). */
export async function updateDoctor(id: string, body: UpdateDoctorBody): Promise<Doctor> {
  const res = await api.put(`/doctors/${id}`, body);
  return res.data;
}

/** POST /api/doctors/{id}/activate (ADMIN). */
export async function activateDoctor(id: string): Promise<Doctor> {
  const res = await api.post(`/doctors/${id}/activate`);
  return res.data;
}

/** POST /api/doctors/{id}/deactivate — hides the doctor from booking, keeps history (ADMIN). */
export async function deactivateDoctor(id: string): Promise<Doctor> {
  const res = await api.post(`/doctors/${id}/deactivate`);
  return res.data;
}

export async function setSchedule(doctorId: string, blocks: ScheduleBlock[]): Promise<Doctor> {
  // Backend expects HH:mm:ss; pad if needed
  const normalised = blocks.map((b) => ({
    ...b,
    startTime: b.startTime.length === 5 ? `${b.startTime}:00` : b.startTime,
    endTime: b.endTime.length === 5 ? `${b.endTime}:00` : b.endTime,
  }));
  const res = await api.put(`/doctors/${doctorId}/schedule`, { blocks: normalised });
  return res.data;
}

export async function addDayOff(doctorId: string, date: string, reason: string | null): Promise<Doctor> {
  const res = await api.post(`/doctors/${doctorId}/days-off`, { date, reason });
  return res.data;
}

export async function removeDayOff(doctorId: string, date: string): Promise<Doctor> {
  const res = await api.delete(`/doctors/${doctorId}/days-off/${date}`);
  return res.data;
}

/**
 * GET /api/doctors/me — the Doctor profile linked to the current user.
 *
 * Returns `null` when the authenticated user has no linked profile (e.g. an admin
 * or cashier viewing /my-schedule). The backend signals this with 200 + an empty
 * body (or 204 No Content), so callers treat `null` as "no doctor profile linked"
 * rather than as an error.
 */
export async function getMyDoctorProfile(): Promise<Doctor | null> {
  const res = await api.get('/doctors/me');
  // 200 + empty body arrives as '' (axios) and 204 as undefined/null — normalise to null.
  return res.data ? (res.data as Doctor) : null;
}
