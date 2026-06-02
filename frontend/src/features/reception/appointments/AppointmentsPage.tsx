import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  CalendarDays,
  Stethoscope,
  Clock,
  Check,
  X as XIcon,
  Search,
  Zap,
  UserCheck,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Input } from '@/shared/ui/Input';
import { extractApiError } from '@/shared/api/client';
import { searchPatients, PatientResponse } from '../api';
import {
  listDoctors,
  listSlots,
  listAppointments,
  bookAppointment,
  cancelAppointment,
  checkInAppointment,
  Doctor,
  Slot,
  Appointment,
} from './api';
import { cn } from '@/shared/ui/cn';

const STATUS_TONE: Record<Appointment['status'], 'neutral' | 'info' | 'success' | 'warning'> = {
  BOOKED:     'info',
  CHECKED_IN: 'warning',
  COMPLETED:  'success',
  CANCELLED:  'neutral',
  NO_SHOW:    'neutral',
};

function todayIso() {
  const d = new Date();
  return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-');
}

export function AppointmentsPage() {
  const { t, i18n } = useTranslation();
  const [doctorId, setDoctorId] = useState<string | null>(null);
  const [date, setDate] = useState<string>(todayIso());
  const [bookSlot, setBookSlot] = useState<Slot | null>(null);
  const [walkinOpen, setWalkinOpen] = useState(false);

  const queryClient = useQueryClient();

  const { data: doctors, isLoading: docsLoading } = useQuery({
    queryKey: ['doctors'],
    queryFn: () => listDoctors(true),
  });

  // Auto-pick first doctor
  useEffect(() => {
    if (!doctorId && doctors && doctors.length > 0) setDoctorId(doctors[0].id);
  }, [doctors, doctorId]);

  const { data: slots, isLoading: slotsLoading } = useQuery({
    queryKey: ['slots', doctorId, date],
    queryFn: () => listSlots(doctorId!, date),
    enabled: !!doctorId,
  });

  const { data: appts } = useQuery({
    queryKey: ['appointments', doctorId, date],
    queryFn: () => listAppointments(doctorId!, date),
    enabled: !!doctorId,
    refetchInterval: 15000,
  });

  const cancelMut = useMutation({
    mutationFn: (id: string) => cancelAppointment(id, 'Cancelled by reception'),
    onSuccess: async () => {
      toast.success(t('appointments.cancelled'));
      await queryClient.invalidateQueries({ queryKey: ['slots', doctorId, date] });
      await queryClient.invalidateQueries({ queryKey: ['appointments', doctorId, date] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? t('appointments.cancelFailed')),
  });
  const checkInMut = useMutation({
    mutationFn: (id: string) => checkInAppointment(id),
    onSuccess: async () => {
      toast.success(t('appointments.checkedIn'));
      await queryClient.invalidateQueries({ queryKey: ['appointments', doctorId, date] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? t('appointments.checkInFailed')),
  });

  const selectedDoctor = doctors?.find((d) => d.id === doctorId);
  const dt = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric' }),
    [i18n.language],
  );

  return (
    <>
      <PageHeader
        title={t('nav.appointments')}
        description={t('appointments.description')}
        actions={
          <Button onClick={() => setWalkinOpen(true)} disabled={!selectedDoctor}>
            <Zap size={14} className="me-1.5" />
            {t('appointments.walkin')}
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[300px_1fr]">
        {/* Doctor + date picker */}
        <div className="space-y-4">
          <Card>
            <div className="border-b border-ink-100 px-5 py-4">
              <h3 className="text-sm font-semibold text-ink-900">{t('appointments.doctor')}</h3>
            </div>
            <div className="p-3">
              {docsLoading ? (
                <div className="space-y-2">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-14" />)}</div>
              ) : !doctors || doctors.length === 0 ? (
                <p className="px-2 py-3 text-sm text-ink-500">{t('appointments.noDoctors')}</p>
              ) : (
                <ul className="space-y-1">
                  {doctors.map((d) => (
                    <li key={d.id}>
                      <button
                        type="button"
                        onClick={() => setDoctorId(d.id)}
                        className={cn(
                          'w-full rounded-lg px-3 py-2 text-start transition-colors',
                          doctorId === d.id
                            ? 'bg-brand-50 text-brand-900 ring-1 ring-inset ring-brand-200'
                            : 'hover:bg-ink-50',
                        )}
                      >
                        <div className="flex items-center gap-2">
                          <Stethoscope size={14} className="text-brand-600" />
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-sm font-medium text-ink-900">{d.fullName}</div>
                            {d.specialty && <div className="truncate text-xs text-ink-500">{d.specialty}</div>}
                          </div>
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </Card>

          <Card>
            <div className="border-b border-ink-100 px-5 py-4">
              <h3 className="text-sm font-semibold text-ink-900">{t('appointments.date')}</h3>
            </div>
            <div className="space-y-2 p-4">
              <Input type="date" min={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} />
              <p className="text-xs text-ink-500">{dt.format(new Date(date))}</p>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => setDate(todayIso())}>{t('common.today')}</Button>
                <Button variant="secondary" size="sm" onClick={() => {
                  const d = new Date(date); d.setDate(d.getDate() + 1);
                  setDate(d.toISOString().slice(0, 10));
                }}>{t('appointments.plusDay')}</Button>
              </div>
            </div>
          </Card>
        </div>

        {/* Slot grid + appointment list */}
        <div className="space-y-4">
          <Card>
            <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
              <div>
                <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-900">
                  <CalendarDays size={14} className="text-brand-600" />
                  {t('appointments.availableSlots')} {selectedDoctor ? `· ${selectedDoctor.fullName}` : ''}
                </h3>
                <p className="mt-0.5 text-xs text-ink-500">{dt.format(new Date(date))}</p>
              </div>
              {selectedDoctor && slots && (
                <Badge tone="info">
                  {t('appointments.slotsFree', { free: slots.filter((s) => s.available).length, total: slots.length })}
                </Badge>
              )}
            </div>
            <div className="p-4">
              {!doctorId ? (
                <p className="px-2 py-8 text-center text-sm text-ink-500">{t('appointments.pickDoctor')}</p>
              ) : slotsLoading ? (
                <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6">
                  {Array.from({ length: 18 }).map((_, i) => <Skeleton key={i} className="h-12" />)}
                </div>
              ) : !slots || slots.length === 0 ? (
                <EmptyState
                  icon={CalendarDays}
                  title={t('appointments.notWorking')}
                  description={t('appointments.notWorkingDesc')}
                />
              ) : (
                <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6">
                  {slots.map((s) => (
                    <button
                      key={s.startsAt}
                      type="button"
                      disabled={!s.available}
                      onClick={() => setBookSlot(s)}
                      className={cn(
                        'flex flex-col items-center justify-center gap-0.5 rounded-lg border p-2 text-xs transition-colors',
                        s.available
                          ? 'border-ink-200 bg-white text-ink-800 hover:border-brand-300 hover:bg-brand-50'
                          : 'cursor-not-allowed border-ink-100 bg-ink-100/60 text-ink-400 line-through',
                      )}
                    >
                      <Clock size={10} className="opacity-60" />
                      <span className="font-mono font-semibold">{s.startsAt.slice(11, 16)}</span>
                      <span className="text-[10px]">{s.durationMinutes}m</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </Card>

          <Card>
            <div className="border-b border-ink-100 px-5 py-4">
              <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-900">
                <UserCheck size={14} className="text-brand-600" />
                {t('appointments.todaysBookings')}
              </h3>
              <p className="mt-0.5 text-xs text-ink-500">
                {appts ? t('appointments.apptCount', { count: appts.length }) : ''}
              </p>
            </div>
            {!appts || appts.length === 0 ? (
              <EmptyState icon={CalendarDays} title={t('appointments.noAppointments')} description={t('appointments.noAppointmentsDesc')} />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-ink-100 bg-ink-50/60 text-[11px] font-semibold uppercase tracking-wide text-ink-500">
                    <tr>
                      <Th>{t('appointments.colTime')}</Th>
                      <Th>{t('appointments.colPatient')}</Th>
                      <Th>{t('appointments.colType')}</Th>
                      <Th>{t('appointments.colStatus')}</Th>
                      <Th>{t('appointments.colVisit')}</Th>
                      <Th className="text-end">{t('common.actions')}</Th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-100">
                    {appts.map((a) => (
                      <tr key={a.id} className="group transition-colors hover:bg-ink-50/60">
                        <Td>
                          <span className="font-mono text-xs font-semibold text-ink-900">{a.scheduledFor.slice(11, 16)}</span>
                          <div className="text-[11px] text-ink-500">{a.durationMinutes}m</div>
                        </Td>
                        <Td>
                          <div className="font-medium text-ink-900">{a.patientName}</div>
                          <div className="font-mono text-[11px] text-ink-500">{a.patientMrn}</div>
                        </Td>
                        <Td>
                          {a.type === 'WALKIN' ? <Badge tone="warning">{t('appointments.walkin')}</Badge> : <Badge tone="info">{t('appointments.booked')}</Badge>}
                        </Td>
                        <Td>
                          <Badge tone={STATUS_TONE[a.status]} dot>{t(`appointmentStatus.${a.status}`)}</Badge>
                        </Td>
                        <Td>
                          <span className="font-mono text-[11px] text-ink-700">{a.visitId.slice(0, 8)}…</span>
                        </Td>
                        <Td className="text-end">
                          {a.status === 'BOOKED' && (
                            <div className="inline-flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                              <button
                                type="button"
                                onClick={() => checkInMut.mutate(a.id)}
                                className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-700"
                              >
                                <Check size={12} /> {t('appointments.checkIn')}
                              </button>
                              <button
                                type="button"
                                onClick={() => cancelMut.mutate(a.id)}
                                className="inline-flex items-center gap-1 rounded-md border border-brand-200 bg-white px-2 py-1 text-xs font-medium text-brand-700 hover:bg-brand-50"
                              >
                                <XIcon size={12} /> {t('common.cancel')}
                              </button>
                            </div>
                          )}
                        </Td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      </div>

      {bookSlot && selectedDoctor && (
        <BookingDialog
          slot={bookSlot}
          doctor={selectedDoctor}
          mode="BOOKED"
          onClose={() => setBookSlot(null)}
          onBooked={async () => {
            await queryClient.invalidateQueries({ queryKey: ['slots', doctorId, date] });
            await queryClient.invalidateQueries({ queryKey: ['appointments', doctorId, date] });
          }}
        />
      )}
      {walkinOpen && selectedDoctor && (
        <BookingDialog
          slot={null}
          doctor={selectedDoctor}
          mode="WALKIN"
          onClose={() => setWalkinOpen(false)}
          onBooked={async () => {
            await queryClient.invalidateQueries({ queryKey: ['slots', doctorId, date] });
            await queryClient.invalidateQueries({ queryKey: ['appointments', doctorId, date] });
          }}
        />
      )}
    </>
  );
}

function BookingDialog({
  slot,
  doctor,
  mode,
  onClose,
  onBooked,
}: {
  slot: Slot | null;
  doctor: Doctor;
  mode: 'BOOKED' | 'WALKIN';
  onClose: () => void;
  onBooked: () => Promise<void>;
}) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [picked, setPicked] = useState<PatientResponse | null>(null);
  const [notes, setNotes] = useState('');

  const { data: matches } = useQuery({
    queryKey: ['patient-search', query],
    queryFn: () => searchPatients(query, 0, 8),
    enabled: query.trim().length >= 2 && !picked,
  });

  const bookMut = useMutation({
    mutationFn: () =>
      bookAppointment({
        doctorId: doctor.id,
        patientId: picked!.id,
        scheduledFor: mode === 'BOOKED' ? slot!.startsAt : undefined,
        type: mode,
        notes: notes || undefined,
      }),
    onSuccess: async (a) => {
      toast.success(
        mode === 'WALKIN'
          ? t('appointments.walkinQueued', { time: a.scheduledFor.slice(11, 16) })
          : t('appointments.bookedToast', { time: a.scheduledFor.slice(11, 16) }),
      );
      await onBooked();
      onClose();
    },
    onError: (err) => {
      toast.error(extractApiError(err)?.message ?? t('appointments.bookingFailed'));
    },
  });

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-lg overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">
              {mode === 'WALKIN' ? t('appointments.walkinTitle') : t('appointments.bookTitle')}
            </h2>
            <p className="mt-0.5 text-xs text-ink-500">
              {doctor.fullName}
              {slot ? ` · ${slot.startsAt.slice(0, 10)} ${slot.startsAt.slice(11, 16)}` : t('appointments.queuedNextSlot')}
            </p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
            <XIcon size={18} />
          </button>
        </div>

        <div className="space-y-4 p-5">
          {!picked ? (
            <>
              <div className="relative">
                <Search size={14} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400" />
                <input
                  type="search"
                  autoFocus
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder={t('appointments.searchPatient')}
                  className="h-10 w-full rounded-lg border border-ink-200 bg-white ps-9 pe-3 text-sm placeholder:text-ink-400 focus:border-brand-500"
                />
              </div>
              {matches && matches.content.length > 0 && (
                <ul className="max-h-72 space-y-1 overflow-y-auto rounded-lg border border-ink-100">
                  {matches.content.map((p) => (
                    <li key={p.id}>
                      <button
                        type="button"
                        onClick={() => setPicked(p)}
                        className="w-full rounded-md px-3 py-2 text-start text-sm hover:bg-ink-50"
                      >
                        <div className="flex items-center justify-between">
                          <div>
                            <div className="font-medium text-ink-900">{p.fullName}</div>
                            <div className="font-mono text-[11px] text-ink-500">{p.mrn}</div>
                          </div>
                          {p.vip && <Badge tone="brand">VIP</Badge>}
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : (
            <div className="rounded-lg border border-ink-100 bg-ink-50/50 p-3">
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-medium text-ink-900">{picked.fullName}</div>
                  <div className="font-mono text-[11px] text-ink-500">{picked.mrn}</div>
                </div>
                <button type="button" onClick={() => setPicked(null)} className="text-xs text-ink-500 hover:underline">
                  {t('appointments.change')}
                </button>
              </div>
            </div>
          )}

          {picked && (
            <Input
              label={t('appointments.notes')}
              placeholder={t('appointments.notesPlaceholder')}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button
            type="button"
            onClick={() => bookMut.mutate()}
            disabled={!picked || bookMut.isPending}
          >
            {bookMut.isPending ? t('appointments.booking') : (mode === 'WALKIN' ? t('appointments.queueWalkin') : t('appointments.confirmBooking'))}
          </Button>
        </div>
      </div>
    </div>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={cn('px-4 py-3 text-start font-semibold', className)}>{children}</th>;
}
function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn('px-4 py-3 align-middle', className)}>{children}</td>;
}
