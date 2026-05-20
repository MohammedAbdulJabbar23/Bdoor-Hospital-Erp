import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Plus, X, Stethoscope, CalendarDays, Pencil, BadgeIcon as BadgeI, CalendarOff,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';
import { extractApiError } from '@/shared/api/client';
import { listDoctors, Doctor, DayOfWeek } from '@/features/reception/appointments/api';
import { createDoctor, setSchedule, addDayOff, removeDayOff, ScheduleBlock } from './api';
import { listUsers, AppUser } from '@/features/admin/users/api';
import { ScheduleEditor } from './ScheduleEditor';
import { cn } from '@/shared/ui/cn';

const DAY_LABEL: Record<DayOfWeek, string> = {
  SUNDAY: 'Sun', MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat',
};

export function DoctorsPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [selected, setSelected] = useState<Doctor | null>(null);

  const queryClient = useQueryClient();

  const { data: doctors, isLoading } = useQuery({
    queryKey: ['doctors-admin'],
    queryFn: () => listDoctors(false),
  });

  return (
    <>
      <PageHeader
        title="Doctors"
        description="Manage doctor profiles, schedules, and days off. Each doctor can optionally be linked to an HMS login."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus size={14} className="me-1.5" />
            New doctor
          </Button>
        }
      />

      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-48" />)}
        </div>
      ) : !doctors || doctors.length === 0 ? (
        <Card>
          <EmptyState icon={Stethoscope} title="No doctors yet" description="Add the first doctor to start scheduling appointments."
            action={<Button onClick={() => setCreateOpen(true)}><Plus size={14} className="me-1.5" /> Add doctor</Button>} />
        </Card>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {doctors.map((d) => (
            <DoctorCard key={d.id} doctor={d} onEdit={() => setSelected(d)} />
          ))}
        </div>
      )}

      {createOpen && (
        <CreateDoctorDialog
          onClose={() => setCreateOpen(false)}
          onCreated={async (d) => {
            toast.success(`${d.fullName} created`);
            await queryClient.invalidateQueries({ queryKey: ['doctors-admin'] });
            await queryClient.invalidateQueries({ queryKey: ['doctors'] });
            setCreateOpen(false);
            setSelected(d);
          }}
        />
      )}

      {selected && (
        <DoctorDetailDialog
          doctor={selected}
          onClose={() => setSelected(null)}
          onChange={async () => {
            await queryClient.invalidateQueries({ queryKey: ['doctors-admin'] });
            await queryClient.invalidateQueries({ queryKey: ['doctors'] });
          }}
        />
      )}
    </>
  );
}

function DoctorCard({ doctor, onEdit }: { doctor: Doctor; onEdit: () => void }) {
  const grouped = doctor.weeklyHours.reduce<Record<DayOfWeek, number>>((acc, h) => {
    acc[h.dayOfWeek] = (acc[h.dayOfWeek] ?? 0) + 1;
    return acc;
  }, {} as Record<DayOfWeek, number>);

  return (
    <Card>
      <div className="border-b border-ink-100 px-5 py-4">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                <Stethoscope size={16} />
              </span>
              <h3 className="truncate text-sm font-semibold text-ink-900">{doctor.fullName}</h3>
            </div>
            <p className="mt-1 text-xs text-ink-500">{doctor.specialty ?? '—'}</p>
          </div>
          <div className="flex items-center gap-1">
            {doctor.active ? <Badge tone="success" dot>Active</Badge> : <Badge tone="neutral" dot>Inactive</Badge>}
            <button type="button" onClick={onEdit} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100 hover:text-ink-900" aria-label="Edit">
              <Pencil size={14} />
            </button>
          </div>
        </div>
      </div>
      <div className="space-y-3 p-5 text-sm">
        <div className="flex items-center justify-between text-xs">
          <span className="text-ink-500">Consultation fee</span>
          <span className="font-mono font-semibold text-ink-900">
            {doctor.consultationFee != null ? `${doctor.consultationFee.toLocaleString()} ${doctor.currency}` : '—'}
          </span>
        </div>
        {doctor.phone && (
          <div className="flex items-center justify-between text-xs">
            <span className="text-ink-500">Phone</span>
            <span className="font-mono text-ink-700">{doctor.phone}</span>
          </div>
        )}
        <div>
          <div className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-ink-500">Weekly schedule</div>
          {doctor.weeklyHours.length === 0 ? (
            <p className="text-xs text-ink-400">No working hours yet — click edit to add.</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {(Object.keys(grouped) as DayOfWeek[]).map((d) => (
                <Badge key={d} tone="info">{DAY_LABEL[d]} ×{grouped[d]}</Badge>
              ))}
            </div>
          )}
        </div>
        {doctor.daysOff.length > 0 && (
          <div className="flex items-center gap-1.5 text-xs text-amber-700">
            <CalendarOff size={12} /> {doctor.daysOff.length} day{doctor.daysOff.length === 1 ? '' : 's'} off scheduled
          </div>
        )}
        {doctor.userId && (
          <div className="flex items-center gap-1.5 text-xs text-ink-500">
            <BadgeI size={12} /> linked to HMS account
          </div>
        )}
      </div>
    </Card>
  );
}

const createSchema = z.object({
  fullName: z.string().min(1).max(200),
  specialty: z.string().max(200).optional(),
  consultationFee: z.coerce.number().min(0).optional(),
  currency: z.string().max(10).optional(),
  phone: z.string().max(30).optional(),
});
type CreateForm = z.infer<typeof createSchema>;

function CreateDoctorDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (d: Doctor) => Promise<void> }) {
  const { data: users } = useQuery({ queryKey: ['users'], queryFn: listUsers });
  const [linkUserId, setLinkUserId] = useState<string>('');

  const { register, handleSubmit, formState: { errors } } = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { currency: 'IQD' },
  });

  const mutation = useMutation({
    mutationFn: (v: CreateForm) =>
      createDoctor({
        userId: linkUserId || undefined,
        fullName: v.fullName,
        specialty: v.specialty || undefined,
        consultationFee: v.consultationFee,
        currency: v.currency || 'IQD',
        phone: v.phone || undefined,
      }),
    onSuccess: (d) => onCreated(d),
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Create failed'),
  });

  const onSubmit = handleSubmit((v) => mutation.mutate(v));

  const doctorRoleUsers: AppUser[] = (users ?? []).filter((u) => u.roles.includes('DOCTOR'));

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-xl overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">New doctor</h2>
            <p className="mt-0.5 text-xs text-ink-500">You can link an existing HMS user with the DOCTOR role, or leave unlinked.</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={onSubmit} className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <Input label="Full name *" error={errors.fullName && 'Required'} {...register('fullName')} />
          </div>
          <Input label="Specialty" placeholder="e.g. Cardiology" {...register('specialty')} />
          <Input label="Phone" {...register('phone')} />
          <Input label="Consultation fee" type="number" step="100" {...register('consultationFee')} />
          <Input label="Currency" {...register('currency')} />
          <div className="sm:col-span-2">
            <Select label="Linked HMS user (optional)" value={linkUserId} onChange={(e) => setLinkUserId(e.target.value)}>
              <option value="">— None —</option>
              {doctorRoleUsers.map((u) => (
                <option key={u.id} value={u.id}>@{u.username} · {u.fullName}</option>
              ))}
            </Select>
            <p className="mt-1 text-xs text-ink-500">Only users with the DOCTOR role are listed.</p>
          </div>
        </form>

        <div className="flex items-center justify-end gap-2 border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="button" onClick={onSubmit} disabled={mutation.isPending}>
            {mutation.isPending ? 'Creating…' : 'Create doctor'}
          </Button>
        </div>
      </div>
    </div>
  );
}

function DoctorDetailDialog({ doctor, onClose, onChange }: { doctor: Doctor; onClose: () => void; onChange: () => Promise<void> }) {
  const [tab, setTab] = useState<'schedule' | 'days-off'>('schedule');

  const scheduleMut = useMutation({
    mutationFn: (blocks: ScheduleBlock[]) => setSchedule(doctor.id, blocks),
    onSuccess: async () => { toast.success('Schedule saved'); await onChange(); },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Save failed'),
  });

  const addDayOffMut = useMutation({
    mutationFn: ({ date, reason }: { date: string; reason: string }) => addDayOff(doctor.id, date, reason || null),
    onSuccess: async () => { toast.success('Day off added'); await onChange(); },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Add failed'),
  });
  const removeDayOffMut = useMutation({
    mutationFn: (date: string) => removeDayOff(doctor.id, date),
    onSuccess: async () => { toast.success('Day off removed'); await onChange(); },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Remove failed'),
  });

  const [newDate, setNewDate] = useState('');
  const [newReason, setNewReason] = useState('');

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">{doctor.fullName}</h2>
            <p className="mt-0.5 text-xs text-ink-500">{doctor.specialty ?? '—'}{doctor.phone ? ' · ' + doctor.phone : ''}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
            <X size={18} />
          </button>
        </div>

        <div className="flex gap-1 border-b border-ink-100 px-5 pt-3">
          {(['schedule', 'days-off'] as const).map((k) => (
            <button
              key={k}
              type="button"
              onClick={() => setTab(k)}
              className={cn(
                'inline-flex items-center gap-1.5 rounded-t-md px-3 py-2 text-sm font-medium transition-colors',
                tab === k ? 'border-b-2 border-brand-600 text-brand-700' : 'text-ink-500 hover:text-ink-900',
              )}
            >
              {k === 'schedule' ? <CalendarDays size={14} /> : <CalendarOff size={14} />}
              {k === 'schedule' ? 'Weekly schedule' : 'Days off'}
            </button>
          ))}
        </div>

        <div className="space-y-4 p-5">
          {tab === 'schedule' ? (
            <ScheduleEditor
              initial={doctor.weeklyHours}
              onSave={(blocks) => scheduleMut.mutate(blocks)}
              saving={scheduleMut.isPending}
            />
          ) : (
            <div className="space-y-4">
              <div className="flex flex-wrap items-end gap-2 rounded-lg border border-ink-200 p-3">
                <div className="min-w-[160px]"><Input type="date" label="Date" value={newDate} onChange={(e) => setNewDate(e.target.value)} /></div>
                <div className="flex-1 min-w-[200px]"><Input label="Reason (optional)" value={newReason} onChange={(e) => setNewReason(e.target.value)} placeholder="e.g. conference" /></div>
                <Button
                  type="button"
                  size="sm"
                  onClick={() => {
                    if (!newDate) { toast.error('Pick a date'); return; }
                    addDayOffMut.mutate({ date: newDate, reason: newReason });
                    setNewDate(''); setNewReason('');
                  }}
                  disabled={addDayOffMut.isPending}
                >
                  Add
                </Button>
              </div>
              {doctor.daysOff.length === 0 ? (
                <p className="text-sm text-ink-500">No days off scheduled.</p>
              ) : (
                <ul className="divide-y divide-ink-100 rounded-lg border border-ink-200">
                  {doctor.daysOff.map((d) => (
                    <li key={d.date} className="flex items-center justify-between px-4 py-2 text-sm">
                      <div>
                        <span className="font-mono">{d.date}</span>
                        {d.reason && <span className="ms-2 text-ink-500">— {d.reason}</span>}
                      </div>
                      <button
                        type="button"
                        onClick={() => removeDayOffMut.mutate(d.date)}
                        className="rounded-md px-2 py-1 text-xs text-brand-700 hover:bg-brand-50"
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        <div className="flex items-center justify-end border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose}>Close</Button>
        </div>
      </div>
    </div>
  );
}
