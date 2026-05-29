import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  ArrowLeft, Activity, Pill, ClipboardList,
  Stethoscope, FlaskConical, Scan, HeartPulse, Siren, Baby,
  TrendingUp, ChevronRight, Crown, Phone, MapPin, IdCard,
  Calendar, FileText, Search, Printer, X as XIcon,
  Inbox,
  type LucideIcon,
} from 'lucide-react';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Sparkline } from '@/shared/ui/Sparkline';
import { Button } from '@/shared/ui/Button';
import { api, extractApiError } from '@/shared/api/client';
import { PatientResponse, toggleVip, archivePatient, unarchivePatient } from '@/features/reception/api';
import { createVisit, VisitType } from '@/features/reception/visits/api';
import { useNavigate } from 'react-router-dom';
import { Archive } from 'lucide-react';
import {
  getPatientHistory, PatientHistory, HistoryEntry, Vitals,
} from '@/features/clinical/api';
import {
  deriveActiveMedications, deriveProblemList, deriveVitalsTrend, deriveRecentResults,
} from '@/features/clinical/derive';
import { cn } from '@/shared/ui/cn';

type VisitTypeFilter = 'ALL' | 'DOCTOR_APPOINTMENT' | 'LABORATORY' | 'RADIOLOGY' | 'ECO' | 'EMERGENCY' | 'PREMATURE';

const TYPE_ICON: Record<string, LucideIcon> = {
  DOCTOR_APPOINTMENT: Stethoscope,
  LABORATORY: FlaskConical,
  RADIOLOGY: Scan,
  ECO: HeartPulse,
  EMERGENCY: Siren,
  PREMATURE: Baby,
  PHARMACY: Pill,
};

const TYPE_LABEL: Record<string, string> = {
  DOCTOR_APPOINTMENT: 'Doctor',
  LABORATORY: 'Lab',
  RADIOLOGY: 'Radiology',
  ECO: 'ECO',
  EMERGENCY: 'Emergency',
  PREMATURE: 'Premature',
  PHARMACY: 'Pharmacy',
};

async function getPatient(id: string): Promise<PatientResponse> {
  const res = await api.get(`/patients/${id}`);
  return res.data;
}

export function PatientProfilePage() {
  const { id } = useParams<{ id: string }>();
  const { i18n, t } = useTranslation();
  const queryClient = useQueryClient();

  const { data: patient, isLoading: patientLoading } = useQuery({
    queryKey: ['patient', id],
    queryFn: () => getPatient(id!),
    enabled: !!id,
  });

  const navigate = useNavigate();

  const vipMut = useMutation({
    mutationFn: (vip: boolean) => toggleVip(id!, vip),
    onSuccess: async (saved) => {
      toast.success(saved.vip ? 'Marked as VIP — payments now auto-approved' : 'VIP flag removed');
      await queryClient.invalidateQueries({ queryKey: ['patient', id] });
      await queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Failed to update VIP flag'),
  });

  const startVisitMut = useMutation({
    mutationFn: (visitType: VisitType) => createVisit(id!, visitType),
    onSuccess: async (visit) => {
      toast.success(`Visit ${visit.visitDisplayId} started`);
      await queryClient.invalidateQueries({ queryKey: ['clinical-history', id] });
      navigate(visit.visitType === 'PREMATURE' ? '/departments/premature' : '/reception/queue');
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Could not start visit'),
  });

  const archiveMut = useMutation({
    mutationFn: () => patient!.archived ? unarchivePatient(id!) : archivePatient(id!),
    onSuccess: async (saved) => {
      toast.success(saved.archived ? 'Patient archived' : 'Patient un-archived');
      await queryClient.invalidateQueries({ queryKey: ['patient', id] });
      await queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Archive failed'),
  });

  const [startVisitOpen, setStartVisitOpen] = useState(false);

  const { data: history, isLoading: historyLoading } = useQuery({
    queryKey: ['clinical-history', id],
    queryFn: () => getPatientHistory(id!),
    enabled: !!id,
  });

  const dt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { day: '2-digit', month: 'short', year: 'numeric' }),
    [i18n.language],
  );
  const dtt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
    [i18n.language],
  );

  const activeMeds = useMemo(() => deriveActiveMedications(history), [history]);
  const problems = useMemo(() => deriveProblemList(history), [history]);
  const trend = useMemo(() => deriveVitalsTrend(history, 12), [history]);
  const recentResults = useMemo(() => deriveRecentResults(history, 6), [history]);
  const activeOrders = useMemo(() => {
    if (!history) return [] as HistoryEntry[];
    return history.entries.filter((e) => e.parentVisitId != null && (
      e.status === 'AWAITING_PAYMENT' || e.status === 'AWAITING_RESULTS'
      || e.status === 'IN_PROGRESS' || e.status === 'AWAITING_FINAL_PAYMENT'
    ));
  }, [history]);

  if (patientLoading || !patient) {
    return (
      <>
        <PageHeader title="Patient profile" />
        <div className="space-y-4"><Skeleton className="h-32" /><Skeleton className="h-64" /></div>
      </>
    );
  }

  const age = computeAge(patient.dateOfBirth);

  return (
    <div className="space-y-4">
      <div className="print-hide flex items-center justify-between">
        <Link to="/reception/patients" className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-900">
          <ArrowLeft size={12} className="rtl:rotate-180" /> Patients
        </Link>
        <div className="flex items-center gap-2">
          <Button
            variant="primary"
            size="sm"
            disabled={startVisitMut.isPending}
            onClick={() => setStartVisitOpen((v) => !v)}
          >
            <Activity size={14} className="me-1.5" />
            {t('patient.startVisit')}
          </Button>
          <Button
            variant={patient.vip ? 'danger' : 'secondary'}
            size="sm"
            disabled={vipMut.isPending}
            onClick={() => vipMut.mutate(!patient.vip)}
            title={patient.vip ? 'Remove VIP — patient will be charged like everyone else' : 'Mark VIP — all payments auto-approved'}
          >
            <Crown size={14} className="me-1.5" />
            {patient.vip ? t('patient.removeVip') : t('patient.markVip')}
          </Button>
          <Button variant="secondary" size="sm" onClick={() => window.print()}>
            <Printer size={14} className="me-1.5" /> Print profile
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={archiveMut.isPending}
            onClick={() => {
              if (!patient.archived && !window.confirm(t('patient.archiveConfirm'))) return;
              archiveMut.mutate();
            }}
            title={patient.archived ? t('patient.unarchive') : t('patient.archiveTooltip')}
          >
            <Archive size={14} className="me-1.5" />
            {patient.archived ? t('patient.unarchive') : t('patient.archive')}
          </Button>
        </div>
      </div>

      {startVisitOpen && (
        <Card>
          <div className="flex items-center justify-between border-b border-ink-100 px-5 py-3">
            <div>
              <h3 className="text-sm font-semibold text-ink-900">Start a new visit</h3>
              <p className="text-xs text-ink-500">Direct walk-in for non-doctor departments. For doctor consultations, use the Appointments page.</p>
            </div>
            <button type="button" onClick={() => setStartVisitOpen(false)} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
              <XIcon size={16} />
            </button>
          </div>
          <div className="flex flex-wrap gap-2 p-4">
            {(['LABORATORY', 'RADIOLOGY', 'ECO', 'PREMATURE'] as VisitType[]).map((vt) => {
              const Icon = TYPE_ICON[vt];
              return (
                <Button
                  key={vt}
                  variant="secondary"
                  size="sm"
                  disabled={startVisitMut.isPending}
                  onClick={() => { setStartVisitOpen(false); startVisitMut.mutate(vt); }}
                >
                  <Icon size={14} className="me-1.5" />
                  {TYPE_LABEL[vt]}
                </Button>
              );
            })}
          </div>
        </Card>
      )}

      <div className="print-only">
        <h1 className="text-2xl font-bold">Patient Summary — Albudoor Hospital</h1>
        <p className="mt-1 text-xs">Generated {new Date().toLocaleString()}</p>
      </div>

      {/* ----- HEADER STRIP ----- */}
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-4 p-5">
          <div className="flex items-start gap-3">
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-brand-50 text-brand-700 text-lg font-semibold">
              {initials(patient.fullName)}
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-2xl font-semibold tracking-tight text-ink-900">{patient.fullName}</h1>
                {patient.vip && <Badge tone="brand"><Crown size={11} className="me-0.5" />VIP</Badge>}
                {patient.archived && <Badge tone="neutral">Archived</Badge>}
                <Badge tone={patient.type === 'INFANT' ? 'warning' : 'info'}>{patient.type === 'INFANT' ? 'Infant' : 'Adult'}</Badge>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
                <KV icon={IdCard} label="MRN"><span className="font-mono">{patient.mrn}</span></KV>
                <KV label="Gender">{patient.gender === 'MALE' ? 'Male' : 'Female'}</KV>
                <KV icon={Calendar} label="DOB">{dt.format(new Date(patient.dateOfBirth))} <span className="text-ink-500">· {age}</span></KV>
                {patient.adult?.mobileNumber && <KV icon={Phone} label="Mobile"><span className="font-mono">{patient.adult.mobileNumber}</span></KV>}
                {patient.adult?.nationalId && <KV label="National ID"><span className="font-mono">{patient.adult.nationalId}</span></KV>}
              </div>
              {patient.adult?.address && (
                <div className="mt-1 inline-flex items-center gap-1 text-xs text-ink-500">
                  <MapPin size={12} /> {patient.adult.address}
                </div>
              )}
            </div>
          </div>
          <div className="flex flex-col items-end gap-2 text-end">
            <div className="text-2xl font-semibold text-ink-900">{history?.totalVisits ?? 0}</div>
            <div className="text-[11px] uppercase tracking-wide text-ink-500">Total visits</div>
          </div>
        </div>
        {patient.adult?.emergencyContactName && (
          <div className="border-t border-ink-100 px-5 py-3 text-xs">
            <span className="font-semibold uppercase tracking-wide text-ink-500">Emergency contact:</span>
            <span className="ms-2 text-ink-700">{patient.adult.emergencyContactName}</span>
            {patient.adult.emergencyContactMobile && <span className="ms-2 font-mono text-ink-500">{patient.adult.emergencyContactMobile}</span>}
          </div>
        )}
      </Card>

      {/* ----- SUMMARY GRID ----- */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <SummaryTile
          icon={ClipboardList} tone="brand"
          label="Active problems" value={problems.length}
          hint={problems.filter((p) => p.chronic).length > 0 ? `${problems.filter((p) => p.chronic).length} recurring` : undefined}
        />
        <SummaryTile
          icon={Pill} tone="info"
          label="Active medications" value={activeMeds.length}
          hint={activeMeds.length > 0 ? `Last Rx ${relativeDate(activeMeds[0].prescribedAt)}` : undefined}
        />
        <SummaryTile
          icon={Activity} tone="success"
          label="Recorded vitals" value={trend.length}
          hint={trend[0] ? `Last ${relativeDate(trend[0].recordedAt)}` : undefined}
        />
      </div>

      {/* ----- TWO-COL: visits left, snapshot right ----- */}
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[1fr_400px]">
        <VisitsTimelineCard
          history={history} loading={historyLoading} dt={dt} dtt={dtt}
        />

        <div className="space-y-4">
          {activeOrders.length > 0 && (
            <ActiveOrdersCard orders={activeOrders} dt={dt} />
          )}
          <ProblemListCard problems={problems} dt={dt} />
          <ActiveMedicationsCard meds={activeMeds} dt={dt} />
          <VitalsTrendCard trend={trend} dt={dt} />
          {recentResults.length > 0 && (
            <RecentResultsCard results={recentResults} dt={dt} />
          )}
        </div>
      </div>
    </div>
  );
}

/* ============================================================== Visits timeline ============================================================== */

function VisitsTimelineCard({
  history, loading, dt, dtt,
}: {
  history: PatientHistory | undefined;
  loading: boolean;
  dt: Intl.DateTimeFormat;
  dtt: Intl.DateTimeFormat;
}) {
  const [filter, setFilter] = useState<VisitTypeFilter>('ALL');
  const [expanded, setExpanded] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  const entries = useMemo(() => {
    if (!history) return [];
    let list = history.entries;
    if (filter !== 'ALL') list = list.filter((e) => e.visitType === filter);
    if (query.trim()) list = list.filter((e) => entryMatches(e, query));
    return list;
  }, [history, filter, query]);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 px-5 py-4 print-hide">
        <div>
          <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-900">
            <Calendar size={14} className="text-brand-600" /> Visit timeline
          </h3>
          <p className="text-xs text-ink-500">All encounters, newest first. Click a row to expand.</p>
        </div>
      </div>

      <div className="space-y-3 border-b border-ink-100 bg-ink-50/40 px-5 py-3 print-hide">
        <div className="relative">
          <Search size={14} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search complaints, diagnoses, drugs, visit ID, doctor…"
            className="h-9 w-full rounded-lg border border-ink-200 bg-white ps-9 pe-8 text-sm placeholder:text-ink-400 focus:border-brand-500"
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery('')}
              className="absolute end-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-ink-400 hover:bg-ink-100 hover:text-ink-700"
              aria-label="Clear search"
            >
              <XIcon size={12} />
            </button>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="font-medium text-ink-600">Filter:</span>
          {(['ALL', 'DOCTOR_APPOINTMENT', 'LABORATORY', 'RADIOLOGY', 'ECO', 'EMERGENCY', 'PREMATURE'] as VisitTypeFilter[]).map((f) => (
            <button
              key={f} type="button" onClick={() => setFilter(f)}
              className={cn(
                'inline-flex h-7 items-center rounded-full px-3 font-medium transition-colors',
                filter === f ? 'bg-brand-600 text-white' : 'border border-ink-200 bg-white text-ink-600 hover:bg-ink-50'
              )}
            >
              {f === 'ALL' ? 'All' : TYPE_LABEL[f]}
            </button>
          ))}
          {(query || filter !== 'ALL') && (
            <span className="ms-auto text-ink-500">
              {entries.length} result{entries.length === 1 ? '' : 's'}
            </span>
          )}
        </div>
      </div>

      {loading ? (
        <div className="space-y-2 p-4">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-16" />)}</div>
      ) : entries.length === 0 ? (
        <div className="px-5 py-10 text-center">
          <Inbox size={28} className="mx-auto text-ink-300" />
          <p className="mt-2 text-sm text-ink-600">
            {history?.entries.length === 0 ? 'No visits recorded yet.' :
              query ? `No visits match “${query}”.` : 'No visits match this filter.'}
          </p>
          {(query || filter !== 'ALL') && (
            <button type="button" onClick={() => { setQuery(''); setFilter('ALL'); }} className="mt-2 text-xs font-medium text-brand-700 hover:underline">
              Clear filters
            </button>
          )}
        </div>
      ) : (
        <ol className="relative ms-5 border-s border-ink-200">
          {entries.map((e) => (
            <TimelineRow
              key={e.visitId} entry={e}
              expanded={expanded === e.visitId}
              onToggle={() => setExpanded((v) => v === e.visitId ? null : e.visitId)}
              dt={dt} dtt={dtt}
            />
          ))}
        </ol>
      )}
    </Card>
  );
}

function TimelineRow({
  entry, expanded, onToggle, dt, dtt,
}: {
  entry: HistoryEntry;
  expanded: boolean;
  onToggle: () => void;
  dt: Intl.DateTimeFormat;
  dtt: Intl.DateTimeFormat;
}) {
  const Icon = TYPE_ICON[entry.visitType] ?? Calendar;
  const tone = statusTone(entry.status);
  const hasExam = entry.exam != null;

  return (
    <li className="relative ms-2 py-3 ps-6">
      <span className="absolute -start-[7px] top-5 flex h-3 w-3 items-center justify-center rounded-full bg-brand-600 ring-4 ring-white" />
      <button
        type="button" onClick={onToggle}
        className="block w-full rounded-lg px-3 py-2 text-start hover:bg-ink-50"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-sm">
              <Icon size={14} className="text-ink-500" />
              <span className="font-medium text-ink-900">{TYPE_LABEL[entry.visitType] ?? entry.visitType}</span>
              {entry.parentVisitId && (
                <Badge tone="warning">forwarded</Badge>
              )}
              <Badge tone={tone}>{entry.status.replace(/_/g, ' ')}</Badge>
            </div>
            <div className="mt-0.5 font-mono text-[11px] text-ink-500">{entry.visitDisplayId}</div>
            {hasExam && entry.exam!.diagnoses.length > 0 && (
              <div className="mt-1 truncate text-xs text-ink-700">
                <span className="font-semibold">Dx:</span>{' '}
                {entry.exam!.diagnoses.find((d) => d.primary)?.description ?? entry.exam!.diagnoses[0].description}
                {entry.exam!.diagnoses.length > 1 && <span className="ms-1 text-ink-500">+{entry.exam!.diagnoses.length - 1}</span>}
              </div>
            )}
            {!hasExam && entry.resultsSummary && (
              <div className="mt-1 truncate text-xs text-ink-600">{entry.resultsSummary.split('\n')[0]}</div>
            )}
          </div>
          <div className="text-end">
            <div className="text-xs text-ink-700">{dt.format(new Date(entry.startedAt))}</div>
            {hasExam && entry.exam!.doctorName && (
              <div className="text-[11px] text-ink-500">{entry.exam!.doctorName}</div>
            )}
          </div>
        </div>
      </button>

      {expanded && (
        <div className="ms-3 mt-2 space-y-3 rounded-lg border border-ink-200 bg-white p-3">
          <div className="flex items-center justify-between">
            <div className="text-xs">
              <span className="text-ink-500">Started</span>{' '}
              <span className="font-mono">{dtt.format(new Date(entry.startedAt))}</span>
              {entry.endedAt && <>{' · '}<span className="text-ink-500">Ended</span> <span className="font-mono">{dtt.format(new Date(entry.endedAt))}</span></>}
            </div>
            <Link to={`/clinical/exam/${entry.visitId}`} className="inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:underline">
              Open exam <ChevronRight size={12} className="rtl:rotate-180" />
            </Link>
          </div>

          {hasExam && (
            <ExamDetailsBlock exam={entry.exam!} />
          )}
          {!hasExam && entry.resultsSummary && (
            <div className="rounded bg-ink-50 p-2 font-mono text-[11px] whitespace-pre-line text-ink-700">{entry.resultsSummary}</div>
          )}
          {!hasExam && !entry.resultsSummary && (
            <p className="text-xs text-ink-500">No clinical detail recorded for this visit.</p>
          )}
        </div>
      )}
    </li>
  );
}

function ExamDetailsBlock({ exam }: { exam: NonNullable<HistoryEntry['exam']> }) {
  return (
    <div className="space-y-2 text-sm">
      {exam.chiefComplaint && (
        <Detail label="Chief complaint">{exam.chiefComplaint}</Detail>
      )}
      {exam.historyOfPresentIllness && (
        <Detail label="History of present illness">{exam.historyOfPresentIllness}</Detail>
      )}
      {exam.examinationNotes && (
        <Detail label="Examination">{exam.examinationNotes}</Detail>
      )}
      {hasAnyVital(exam.vitals) && (
        <Detail label="Vitals">
          <VitalsLine v={exam.vitals} />
        </Detail>
      )}
      {exam.diagnoses.length > 0 && (
        <Detail label="Diagnoses">
          <ul className="list-inside list-disc">
            {exam.diagnoses.map((d, i) => (
              <li key={i} className="text-ink-800">
                {d.code && <span className="me-1 font-mono text-[11px] text-ink-500">{d.code}</span>}
                {d.description}
                {d.primary && <span className="ms-1 text-brand-600">★ primary</span>}
              </li>
            ))}
          </ul>
        </Detail>
      )}
      {exam.prescriptions.length > 0 && (
        <Detail label="Prescriptions">
          <ul className="list-inside list-disc">
            {exam.prescriptions.map((p, i) => (
              <li key={i} className="text-ink-800">
                {p.drugName} {p.strength && <span className="text-ink-500">{p.strength}</span>}
                {p.dose && <> — {p.dose}</>}
                {p.frequency && <> · {p.frequency}</>}
                {p.duration && <> · {p.duration}</>}
                {p.route && <span className="ms-1 text-ink-500">({p.route})</span>}
              </li>
            ))}
          </ul>
        </Detail>
      )}
      {exam.plan && <Detail label="Plan">{exam.plan}</Detail>}
      {exam.referralInstructions && <Detail label="Referral">{exam.referralInstructions}</Detail>}
    </div>
  );
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[11px] font-semibold uppercase tracking-wide text-ink-500">{label}</div>
      <div className="mt-0.5 text-sm text-ink-800">{children}</div>
    </div>
  );
}

function VitalsLine({ v }: { v: Vitals }) {
  const parts: string[] = [];
  if (v.systolicBp != null && v.diastolicBp != null) parts.push(`BP ${v.systolicBp}/${v.diastolicBp}`);
  if (v.heartRate != null) parts.push(`HR ${v.heartRate}`);
  if (v.respiratoryRate != null) parts.push(`RR ${v.respiratoryRate}`);
  if (v.temperatureC != null) parts.push(`T ${v.temperatureC}°C`);
  if (v.oxygenSaturation != null) parts.push(`SpO₂ ${v.oxygenSaturation}%`);
  if (v.weightKg != null) parts.push(`W ${v.weightKg}kg`);
  if (v.heightCm != null) parts.push(`H ${v.heightCm}cm`);
  if (v.bmi != null) parts.push(`BMI ${v.bmi}`);
  return <span className="font-mono text-xs text-ink-700">{parts.join('  ·  ')}</span>;
}

/* ============================================================== Right rail panels ============================================================== */

function ProblemListCard({ problems, dt }: { problems: ReturnType<typeof deriveProblemList>; dt: Intl.DateTimeFormat }) {
  return (
    <Card>
      <PanelHead icon={ClipboardList} title="Problem list" subtitle={`${problems.length} unique diagnoses`} />
      {problems.length === 0 ? (
        <p className="px-5 pb-4 text-xs text-ink-500">None recorded.</p>
      ) : (
        <ul className="divide-y divide-ink-100">
          {problems.slice(0, 12).map((p) => (
            <li key={(p.code ?? '') + p.description} className="px-5 py-3 text-sm">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    {p.code && <span className="font-mono text-[10px] text-ink-500">{p.code}</span>}
                    <span className="font-medium text-ink-900">{p.description}</span>
                    {p.chronic && <Badge tone="warning">recurring</Badge>}
                    {p.primaryCount > 0 && <span className="text-brand-600" title="Was primary diagnosis">★</span>}
                  </div>
                  <div className="mt-0.5 text-[11px] text-ink-500">
                    First seen {dt.format(new Date(p.firstSeen))}
                    {p.firstSeen !== p.lastSeen && <> · last {dt.format(new Date(p.lastSeen))}</>}
                    {p.occurrences > 1 && <> · {p.occurrences} visits</>}
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function ActiveMedicationsCard({ meds, dt }: { meds: ReturnType<typeof deriveActiveMedications>; dt: Intl.DateTimeFormat }) {
  return (
    <Card>
      <PanelHead icon={Pill} title="Active medications" subtitle={`${meds.length} most-recent prescriptions per drug`} />
      {meds.length === 0 ? (
        <p className="px-5 pb-4 text-xs text-ink-500">No medications on record.</p>
      ) : (
        <ul className="divide-y divide-ink-100">
          {meds.slice(0, 10).map((m, i) => (
            <li key={i} className="px-5 py-3 text-sm">
              <div className="font-medium text-ink-900">{m.drugName} {m.strength && <span className="text-ink-500">{m.strength}</span>}</div>
              <div className="text-[12px] text-ink-700">
                {[m.dose, m.frequency, m.duration].filter(Boolean).join(' · ')}
                {m.route && <span className="ms-1 text-ink-500">({m.route})</span>}
              </div>
              <div className="mt-0.5 text-[11px] text-ink-500">
                Prescribed {dt.format(new Date(m.prescribedAt))} · {m.prescribedByDoctor} · <span className="font-mono">{m.prescribedByVisit}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function VitalsTrendCard({ trend, dt }: { trend: ReturnType<typeof deriveVitalsTrend>; dt: Intl.DateTimeFormat }) {
  // Reverse to chronological for sparklines (left → right = older → newer)
  const chrono = useMemo(() => [...trend].reverse(), [trend]);
  if (trend.length === 0) {
    return (
      <Card>
        <PanelHead icon={TrendingUp} title="Vitals trend" />
        <p className="px-5 pb-4 text-xs text-ink-500">No vitals recorded.</p>
      </Card>
    );
  }

  const sysVals = chrono.map((t) => t.vitals.systolicBp ?? null);
  const diaVals = chrono.map((t) => t.vitals.diastolicBp ?? null);
  const hrVals = chrono.map((t) => t.vitals.heartRate ?? null);
  const tempVals = chrono.map((t) => t.vitals.temperatureC != null ? Number(t.vitals.temperatureC) : null);
  const spo2Vals = chrono.map((t) => t.vitals.oxygenSaturation ?? null);
  const wtVals = chrono.map((t) => t.vitals.weightKg != null ? Number(t.vitals.weightKg) : null);

  const last = trend[0].vitals;
  return (
    <Card>
      <PanelHead icon={TrendingUp} title="Vitals trend" subtitle={`${trend.length} recording${trend.length === 1 ? '' : 's'} · ${dt.format(new Date(trend[0].recordedAt))}`} />
      <div className="grid grid-cols-2 gap-px bg-ink-100">
        <SparklineTile
          label="BP"
          value={last.systolicBp != null ? `${last.systolicBp}/${last.diastolicBp ?? '—'}` : '—'}
          unit="mmHg"
          tone={bpTone(last.systolicBp, last.diastolicBp)}
          chart={
            <Sparkline series={[
              { values: sysVals, color: 'text-brand-600' },
              { values: diaVals, color: 'text-sky-600' },
            ]} normalRange={[80, 130]} />
          }
        />
        <SparklineTile
          label="Heart rate"
          value={last.heartRate ?? '—'}
          unit="bpm"
          tone={hrTone(last.heartRate)}
          chart={<Sparkline series={[{ values: hrVals, color: 'text-emerald-600' }]} normalRange={[60, 100]} />}
        />
        <SparklineTile
          label="Temp"
          value={last.temperatureC ?? '—'}
          unit="°C"
          tone={tempTone(last.temperatureC != null ? Number(last.temperatureC) : null)}
          chart={<Sparkline series={[{ values: tempVals, color: 'text-amber-600' }]} normalRange={[36, 37.2]} />}
        />
        <SparklineTile
          label="SpO₂"
          value={last.oxygenSaturation ?? '—'}
          unit="%"
          tone={spo2Tone(last.oxygenSaturation)}
          chart={<Sparkline series={[{ values: spo2Vals, color: 'text-sky-600' }]} normalRange={[95, 100]} />}
        />
        <SparklineTile
          label="Weight"
          value={last.weightKg ?? '—'}
          unit="kg"
          chart={<Sparkline series={[{ values: wtVals, color: 'text-ink-700' }]} />}
        />
        <SparklineTile
          label="BMI"
          value={last.bmi ?? '—'}
          unit="kg/m²"
          tone={bmiTone(last.bmi != null ? Number(last.bmi) : null)}
        />
      </div>
      <details className="border-t border-ink-100 px-5 py-2 text-xs">
        <summary className="cursor-pointer select-none text-ink-600">Show all measurements</summary>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full text-xs">
            <thead className="text-[10px] font-semibold uppercase tracking-wide text-ink-500">
              <tr>
                <th className="px-2 py-1 text-start">Date</th>
                <th className="px-2 py-1 text-end">BP</th>
                <th className="px-2 py-1 text-end">HR</th>
                <th className="px-2 py-1 text-end">T°</th>
                <th className="px-2 py-1 text-end">SpO₂</th>
                <th className="px-2 py-1 text-end">Wt</th>
                <th className="px-2 py-1 text-end">BMI</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-100">
              {trend.map((t) => (
                <tr key={t.visitDisplayId}>
                  <td className="px-2 py-1 text-ink-700">{dt.format(new Date(t.recordedAt))}</td>
                  <td className="px-2 py-1 text-end font-mono">{t.vitals.systolicBp != null ? `${t.vitals.systolicBp}/${t.vitals.diastolicBp ?? '—'}` : '—'}</td>
                  <td className="px-2 py-1 text-end font-mono">{t.vitals.heartRate ?? '—'}</td>
                  <td className="px-2 py-1 text-end font-mono">{t.vitals.temperatureC ?? '—'}</td>
                  <td className="px-2 py-1 text-end font-mono">{t.vitals.oxygenSaturation ?? '—'}</td>
                  <td className="px-2 py-1 text-end font-mono">{t.vitals.weightKg ?? '—'}</td>
                  <td className="px-2 py-1 text-end font-mono">{t.vitals.bmi ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </Card>
  );
}

function SparklineTile({
  label, value, unit, tone, chart,
}: {
  label: string;
  value: string | number;
  unit: string;
  tone?: 'normal' | 'warn' | 'critical';
  chart?: React.ReactNode;
}) {
  const cls = tone === 'critical' ? 'bg-brand-50/60' : tone === 'warn' ? 'bg-amber-50/60' : 'bg-white';
  return (
    <div className={cn('flex items-center justify-between gap-2 px-4 py-3', cls)}>
      <div>
        <div className="text-[10px] font-semibold uppercase tracking-wide text-ink-500">{label}</div>
        <div className="mt-0.5 flex items-baseline gap-1">
          <span className={cn('font-mono text-sm font-semibold',
            tone === 'critical' ? 'text-brand-700' : tone === 'warn' ? 'text-amber-700' : 'text-ink-900')}>
            {value}
          </span>
          <span className="text-[10px] text-ink-500">{unit}</span>
        </div>
      </div>
      {chart && <div className="text-ink-400">{chart}</div>}
    </div>
  );
}

function ActiveOrdersCard({ orders, dt }: { orders: HistoryEntry[]; dt: Intl.DateTimeFormat }) {
  return (
    <Card>
      <PanelHead icon={Activity} title="Active orders" subtitle={`${orders.length} in flight across all visits`} />
      <ul className="divide-y divide-ink-100">
        {orders.map((o) => {
          const Icon = TYPE_ICON[o.visitType] ?? FileText;
          return (
            <li key={o.visitId} className="px-5 py-3 text-sm">
              <div className="flex items-start gap-2">
                <Icon size={12} className="mt-1 text-ink-500" />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5">
                    <span className="font-medium text-ink-900">{TYPE_LABEL[o.visitType] ?? o.visitType}</span>
                    <Badge tone={statusTone(o.status)} dot>{o.status.replace(/_/g, ' ')}</Badge>
                  </div>
                  <div className="font-mono text-[11px] text-ink-500">{o.visitDisplayId} · started {dt.format(new Date(o.startedAt))}</div>
                </div>
                <Link to={`/clinical/exam/${o.parentVisitId}`} className="text-[11px] font-medium text-brand-700 hover:underline">
                  parent visit
                </Link>
              </div>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function bpTone(sys: number | null, dia: number | null): 'normal' | 'warn' | 'critical' | undefined {
  if (sys == null) return undefined;
  if (sys >= 180 || (dia != null && dia >= 110) || sys < 90) return 'critical';
  if (sys >= 140 || (dia != null && dia >= 90) || sys < 100) return 'warn';
  return 'normal';
}
function hrTone(hr: number | null): 'normal' | 'warn' | 'critical' | undefined {
  if (hr == null) return undefined;
  if (hr >= 130 || hr < 40) return 'critical';
  if (hr > 100 || hr < 60) return 'warn';
  return 'normal';
}
function tempTone(t: number | null): 'normal' | 'warn' | 'critical' | undefined {
  if (t == null) return undefined;
  if (t >= 39 || t < 35) return 'critical';
  if (t >= 38) return 'warn';
  return 'normal';
}
function spo2Tone(s: number | null): 'normal' | 'warn' | 'critical' | undefined {
  if (s == null) return undefined;
  if (s < 90) return 'critical';
  if (s < 95) return 'warn';
  return 'normal';
}
function bmiTone(b: number | null): 'normal' | 'warn' | 'critical' | undefined {
  if (b == null) return undefined;
  if (b >= 35 || b < 16) return 'critical';
  if (b >= 30 || b < 18.5) return 'warn';
  return 'normal';
}

function entryMatches(e: HistoryEntry, q: string): boolean {
  const needle = q.toLowerCase();
  if (e.visitDisplayId.toLowerCase().includes(needle)) return true;
  if (e.visitType.toLowerCase().includes(needle)) return true;
  if (e.resultsSummary && e.resultsSummary.toLowerCase().includes(needle)) return true;
  if (e.exam) {
    if (e.exam.doctorName.toLowerCase().includes(needle)) return true;
    if (e.exam.chiefComplaint?.toLowerCase().includes(needle)) return true;
    if (e.exam.historyOfPresentIllness?.toLowerCase().includes(needle)) return true;
    if (e.exam.examinationNotes?.toLowerCase().includes(needle)) return true;
    if (e.exam.plan?.toLowerCase().includes(needle)) return true;
    if (e.exam.diagnoses.some((d) => d.description.toLowerCase().includes(needle) || (d.code ?? '').toLowerCase().includes(needle))) return true;
    if (e.exam.prescriptions.some((p) => p.drugName.toLowerCase().includes(needle))) return true;
  }
  return false;
}

function RecentResultsCard({ results, dt }: { results: ReturnType<typeof deriveRecentResults>; dt: Intl.DateTimeFormat }) {
  return (
    <Card>
      <PanelHead icon={FileText} title="Recent results" subtitle="Returned from forwarded visits" />
      <ul className="divide-y divide-ink-100">
        {results.map((r) => {
          const Icon = TYPE_ICON[r.visitType] ?? FileText;
          return (
            <li key={r.visitDisplayId} className="px-5 py-3">
              <div className="flex items-center gap-2 text-sm">
                <Icon size={12} className="text-ink-500" />
                <span className="font-medium text-ink-900">{TYPE_LABEL[r.visitType] ?? r.visitType}</span>
                <span className="font-mono text-[10px] text-ink-500">{r.visitDisplayId}</span>
                {r.endedAt && <span className="ms-auto text-[11px] text-ink-500">{dt.format(new Date(r.endedAt))}</span>}
              </div>
              <pre className="mt-1 whitespace-pre-line rounded bg-ink-50 p-2 font-mono text-[11px] text-ink-700">{r.summary}</pre>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

/* ============================================================== Bits ============================================================== */

function PanelHead({ icon: Icon, title, subtitle }: { icon: LucideIcon; title: string; subtitle?: string }) {
  return (
    <div className="flex items-start gap-3 border-b border-ink-100 px-5 py-4">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
        <Icon size={16} />
      </span>
      <div>
        <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
        {subtitle && <p className="text-[11px] text-ink-500">{subtitle}</p>}
      </div>
    </div>
  );
}

function SummaryTile({
  icon: Icon, tone, label, value, hint,
}: {
  icon: LucideIcon;
  tone: 'brand' | 'info' | 'success';
  label: string;
  value: number;
  hint?: string;
}) {
  const cls = { brand: 'bg-brand-50 text-brand-700', info: 'bg-sky-50 text-sky-700', success: 'bg-emerald-50 text-emerald-700' }[tone];
  return (
    <Card>
      <div className="flex items-start justify-between gap-3 p-5">
        <div>
          <div className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</div>
          <div className="mt-1 text-2xl font-semibold text-ink-900">{value}</div>
          {hint && <div className="mt-0.5 text-[11px] text-ink-500">{hint}</div>}
        </div>
        <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', cls)}>
          <Icon size={18} />
        </span>
      </div>
    </Card>
  );
}

function KV({ icon: Icon, label, children }: { icon?: LucideIcon; label: string; children: React.ReactNode }) {
  return (
    <span className="inline-flex items-baseline gap-1.5 text-xs">
      {Icon && <Icon size={12} className="text-ink-400" />}
      <span className="text-ink-500">{label}:</span>
      <span className="font-medium text-ink-700">{children}</span>
    </span>
  );
}

function statusTone(s: string): 'neutral' | 'info' | 'success' | 'warning' | 'brand' | 'danger' {
  switch (s) {
    case 'COMPLETED': case 'RETURNED': return 'success';
    case 'AWAITING_PAYMENT': case 'AWAITING_FINAL_PAYMENT': return 'warning';
    case 'OUTSTANDING_BALANCE': return 'danger';
    case 'IN_PROGRESS': case 'AWAITING_RESULTS': case 'AWAITING_STUDY': return 'info';
    case 'TREATMENT_FINISHED': case 'FINDINGS_COMPLETE': return 'brand';
    default: return 'neutral';
  }
}

function hasAnyVital(v: Vitals) {
  return v.systolicBp != null || v.heartRate != null || v.temperatureC != null
      || v.weightKg != null || v.heightCm != null || v.oxygenSaturation != null;
}

function initials(name: string) {
  const p = name.trim().split(/\s+/).filter(Boolean);
  if (p.length === 0) return '?';
  if (p.length === 1) return p[0][0].toUpperCase();
  return (p[0][0] + p[p.length - 1][0]).toUpperCase();
}

function computeAge(dob: string): string {
  const birth = new Date(dob);
  const now = new Date();
  let years = now.getFullYear() - birth.getFullYear();
  const m = now.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) years--;
  if (years < 1) {
    const months = (now.getFullYear() - birth.getFullYear()) * 12 + m;
    return `${months}mo`;
  }
  return `${years}y`;
}

function relativeDate(iso: string): string {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
  if (days === 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 30) return `${days}d ago`;
  if (days < 365) return `${Math.floor(days / 30)}mo ago`;
  return `${Math.floor(days / 365)}y ago`;
}
