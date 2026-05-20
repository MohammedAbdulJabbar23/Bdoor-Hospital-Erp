import { api } from '@/shared/api/client';

export type DayOfWeek =
  | 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export type WeeklyHour = {
  dayOfWeek: DayOfWeek;
  startTime: string; // HH:mm:ss
  endTime: string;
  slotMinutes: number;
};

export type DoctorDayOff = {
  date: string;
  reason: string | null;
};

export type Doctor = {
  id: string;
  userId: string | null;
  fullName: string;
  specialty: string | null;
  consultationFee: number | null;
  currency: string;
  phone: string | null;
  active: boolean;
  weeklyHours: WeeklyHour[];
  daysOff: DoctorDayOff[];
};

export type Slot = {
  startsAt: string;     // ISO local datetime
  endsAt: string;
  durationMinutes: number;
  available: boolean;
};

export type AppointmentType = 'BOOKED' | 'WALKIN';

export type AppointmentStatus =
  | 'BOOKED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export type Appointment = {
  id: string;
  doctorId: string;
  doctorName: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  visitId: string;
  scheduledFor: string;
  scheduledDate: string;
  durationMinutes: number;
  type: AppointmentType;
  status: AppointmentStatus;
  notes: string | null;
  cancellationReason: string | null;
  checkedInAt: string | null;
  completedAt: string | null;
};

export async function listDoctors(activeOnly = true): Promise<Doctor[]> {
  const res = await api.get('/doctors', { params: { activeOnly } });
  return res.data;
}

export async function listSlots(doctorId: string, date: string): Promise<Slot[]> {
  const res = await api.get(`/doctors/${doctorId}/slots`, { params: { date } });
  return res.data;
}

export async function listAppointments(doctorId: string, date: string): Promise<Appointment[]> {
  const res = await api.get('/appointments', { params: { doctorId, date } });
  return res.data;
}

export async function bookAppointment(body: {
  doctorId: string;
  patientId: string;
  scheduledFor?: string;
  type: AppointmentType;
  notes?: string;
}): Promise<Appointment> {
  const res = await api.post('/appointments', body);
  return res.data;
}

export async function cancelAppointment(id: string, reason: string): Promise<Appointment> {
  const res = await api.post(`/appointments/${id}/cancel`, { reason });
  return res.data;
}

export async function checkInAppointment(id: string): Promise<Appointment> {
  const res = await api.post(`/appointments/${id}/check-in`);
  return res.data;
}
