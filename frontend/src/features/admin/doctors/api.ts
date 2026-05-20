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

export async function getMyDoctorProfile(): Promise<Doctor> {
  const res = await api.get('/doctors/me');
  return res.data;
}
