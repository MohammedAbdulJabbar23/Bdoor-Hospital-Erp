import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Stethoscope, CalendarDays, Clock, AlertCircle, BadgeCheck } from 'lucide-react';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Input } from '@/shared/ui/Input';
import { getMyDoctorProfile } from '@/features/admin/doctors/api';
import { listAppointments, Appointment, DayOfWeek, WeeklyHour } from '@/features/reception/appointments/api';
import { cn } from '@/shared/ui/cn';

const DAY_LABEL: Record<DayOfWeek, string> = {
  SUNDAY: 'Sun', MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat',
};
const DAY_ORDER: DayOfWeek[] = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

function todayIso() {
  const d = new Date();
  return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-');
}

export function MySchedulePage() {
  const { i18n } = useTranslation();
  const [date, setDate] = useState(todayIso());

  const { data: doctor, isLoading: docLoading, error: docError } = useQuery({
    queryKey: ['doctor-me'],
    queryFn: getMyDoctorProfile,
    retry: false,
  });

  const { data: appts, isLoading: apptsLoading } = useQuery({
    queryKey: ['appointments', doctor?.id, date],
    queryFn: () => listAppointments(doctor!.id, date),
    enabled: !!doctor?.id,
    refetchInterval: 15000,
  });

  const dt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric' }),
    [i18n.language],
  );

  const grouped = useMemo(() => {
    if (!doctor) return null;
    const m: Record<DayOfWeek, WeeklyHour[]> = {} as Record<DayOfWeek, WeeklyHour[]>;
    for (const dow of DAY_ORDER) m[dow] = [];
    doctor.weeklyHours.forEach((h) => m[h.dayOfWeek].push(h));
    return m;
  }, [doctor]);

  if (docLoading) {
    return (
      <>
        <PageHeader title="My schedule" description="Your upcoming day and weekly availability." />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-48" />)}
        </div>
      </>
    );
  }

  if (docError || !doctor) {
    return (
      <>
        <PageHeader title="My schedule" />
        <Card>
          <EmptyState
            icon={AlertCircle}
            title="No doctor profile linked"
            description="Your account is not linked to a Doctor record. Ask an admin to create or link one in /admin/doctors."
          />
        </Card>
      </>
    );
  }

  const today = appts ?? [];
  const upcoming = today.filter((a) => a.status === 'BOOKED' || a.status === 'CHECKED_IN');
  const completed = today.filter((a) => a.status === 'COMPLETED').length;
  const cancelled = today.filter((a) => a.status === 'CANCELLED' || a.status === 'NO_SHOW').length;

  return (
    <>
      <PageHeader
        title={`Welcome, ${doctor.fullName}`}
        description={doctor.specialty ?? 'Your upcoming day and weekly availability.'}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Stat icon={Stethoscope} tone="brand"   label="Today's bookings" value={today.length} />
        <Stat icon={Clock}        tone="info"    label="Upcoming"         value={upcoming.length} />
        <Stat icon={BadgeCheck}   tone="success" label="Completed today"  value={completed} />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[1fr_360px]">
        <div className="space-y-4">
          <Card>
            <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
              <div>
                <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-900">
                  <CalendarDays size={14} className="text-brand-600" />
                  Patient queue
                </h3>
                <p className="mt-0.5 text-xs text-ink-500">{dt.format(new Date(date))}</p>
              </div>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="!h-9 w-44" />
            </div>
            {apptsLoading ? (
              <div className="space-y-2 p-4">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-12" />)}</div>
            ) : today.length === 0 ? (
              <EmptyState icon={CalendarDays} title="Nothing on the calendar" description="No bookings on this day." />
            ) : (
              <PatientQueue appts={today} />
            )}
          </Card>
        </div>

        <Card>
          <div className="border-b border-ink-100 px-5 py-4">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-900">
              <CalendarDays size={14} className="text-brand-600" />
              Weekly schedule
            </h3>
            <p className="mt-0.5 text-xs text-ink-500">As configured by admin.</p>
          </div>
          <ul className="divide-y divide-ink-100">
            {DAY_ORDER.map((dow) => {
              const blocks = grouped?.[dow] ?? [];
              return (
                <li key={dow} className="flex items-start gap-3 px-5 py-3">
                  <span className="mt-0.5 inline-flex h-6 w-10 items-center justify-center rounded-md bg-ink-100 text-[11px] font-semibold text-ink-700">
                    {DAY_LABEL[dow]}
                  </span>
                  {blocks.length === 0 ? (
                    <span className="text-xs text-ink-400">off</span>
                  ) : (
                    <div className="flex flex-wrap gap-1">
                      {blocks.map((b, i) => (
                        <Badge key={i} tone="info">
                          {b.startTime.slice(0, 5)}–{b.endTime.slice(0, 5)} · {b.slotMinutes}m
                        </Badge>
                      ))}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
          {doctor.daysOff.length > 0 && (
            <div className="border-t border-ink-100 px-5 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-ink-500">Days off</div>
              <ul className="mt-1 space-y-0.5 text-xs">
                {doctor.daysOff.slice(0, 5).map((d) => (
                  <li key={d.date} className="text-ink-700">
                    <span className="font-mono">{d.date}</span>
                    {d.reason && <span className="text-ink-500"> — {d.reason}</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Card>
      </div>

      {cancelled > 0 && (
        <p className="mt-3 text-xs text-ink-500">{cancelled} cancelled / no-show today.</p>
      )}
    </>
  );
}

function Stat({ icon: Icon, tone, label, value }: { icon: typeof Stethoscope; tone: 'brand' | 'info' | 'success'; label: string; value: number }) {
  const cls = { brand: 'bg-brand-50 text-brand-700', info: 'bg-sky-50 text-sky-700', success: 'bg-emerald-50 text-emerald-700' }[tone];
  return (
    <Card>
      <div className="flex items-center justify-between p-5">
        <div>
          <div className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</div>
          <div className="mt-1 text-2xl font-semibold text-ink-900">{value}</div>
        </div>
        <span className={cn('flex h-10 w-10 items-center justify-center rounded-lg', cls)}>
          <Icon size={18} />
        </span>
      </div>
    </Card>
  );
}

function ApptRow({ a }: { a: Appointment }) {
  const STATUS_TONE: Record<Appointment['status'], { tone: 'neutral' | 'info' | 'success' | 'warning'; label: string }> = {
    BOOKED:     { tone: 'info',    label: 'Booked' },
    CHECKED_IN: { tone: 'warning', label: 'Checked in' },
    COMPLETED:  { tone: 'success', label: 'Completed' },
    CANCELLED:  { tone: 'neutral', label: 'Cancelled' },
    NO_SHOW:    { tone: 'neutral', label: 'No-show' },
  };
  const tone = STATUS_TONE[a.status];
  return (
    <li>
      <a
        href={`/clinical/exam/${a.visitId}`}
        className="flex items-center gap-4 px-5 py-3 transition-colors hover:bg-ink-50"
      >
        <span className="font-mono text-sm font-semibold text-ink-900">{a.scheduledFor.slice(11, 16)}</span>
        <div className="min-w-0 flex-1">
          <div className="font-medium text-ink-900">{a.patientName}</div>
          <div className="font-mono text-[11px] text-ink-500">{a.patientMrn}</div>
        </div>
        {a.type === 'WALKIN' && <Badge tone="warning">Walk-in</Badge>}
        <Badge tone={tone.tone} dot>{tone.label}</Badge>
        <span className="text-xs text-ink-500">{a.durationMinutes}m</span>
      </a>
    </li>
  );
}

/* ---------------------------------------------------------------- Grouped patient queue ---------------------------------------------------------------- */

function PatientQueue({ appts }: { appts: Appointment[] }) {
  const checkedIn = appts.filter((a) => a.status === 'CHECKED_IN');
  const booked   = appts.filter((a) => a.status === 'BOOKED').sort((a, b) => a.scheduledFor.localeCompare(b.scheduledFor));
  const completed = appts.filter((a) => a.status === 'COMPLETED');
  const other    = appts.filter((a) => a.status === 'CANCELLED' || a.status === 'NO_SHOW');

  return (
    <div className="space-y-2 p-2">
      <QueueGroup
        title="Now seeing" subtitle={`${checkedIn.length} patient${checkedIn.length === 1 ? '' : 's'} ready`}
        accent="warning" icon="●"
        appts={checkedIn}
        emptyMsg="No one is checked in yet — reception checks patients in when they arrive."
      />
      <QueueGroup
        title="Up next" subtitle={`${booked.length} booked`}
        accent="info"
        appts={booked}
        emptyMsg="No upcoming bookings."
      />
      {completed.length > 0 && (
        <QueueGroup
          title="Completed today" subtitle={`${completed.length} done`}
          accent="success" collapsible defaultOpen={false}
          appts={completed}
        />
      )}
      {other.length > 0 && (
        <QueueGroup
          title="Cancelled / no-show" subtitle={`${other.length}`}
          accent="neutral" collapsible defaultOpen={false}
          appts={other}
        />
      )}
    </div>
  );
}

function QueueGroup({
  title, subtitle, accent, appts, emptyMsg, collapsible = false, defaultOpen = true, icon,
}: {
  title: string;
  subtitle: string;
  accent: 'warning' | 'info' | 'success' | 'neutral';
  appts: Appointment[];
  emptyMsg?: string;
  collapsible?: boolean;
  defaultOpen?: boolean;
  icon?: string;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const accentBg = {
    warning: 'bg-amber-50 text-amber-800',
    info: 'bg-sky-50 text-sky-800',
    success: 'bg-emerald-50 text-emerald-800',
    neutral: 'bg-ink-100 text-ink-700',
  }[accent];

  return (
    <div className="rounded-lg border border-ink-200">
      <button
        type="button"
        onClick={() => collapsible && setOpen((v) => !v)}
        className={cn(
          'flex w-full items-center justify-between px-3 py-2 text-start',
          collapsible && 'hover:bg-ink-50',
        )}
      >
        <div className="flex items-center gap-2">
          <span className={cn('inline-flex h-6 items-center justify-center rounded-md px-2 text-[11px] font-semibold uppercase tracking-wide', accentBg)}>
            {icon && <span className="me-1">{icon}</span>}
            {title}
          </span>
          <span className="text-xs text-ink-500">{subtitle}</span>
        </div>
      </button>
      {open && (
        appts.length === 0 ? (
          emptyMsg ? <p className="px-4 py-3 text-xs text-ink-500">{emptyMsg}</p> : null
        ) : (
          <ul className="divide-y divide-ink-100 border-t border-ink-100">
            {appts.map((a) => <ApptRow key={a.id} a={a} />)}
          </ul>
        )
      )}
    </div>
  );
}

