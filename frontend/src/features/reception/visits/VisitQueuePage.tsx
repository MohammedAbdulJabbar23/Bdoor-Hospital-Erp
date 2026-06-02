import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ListOrdered, Search, Wallet, Activity, Clock,
  CheckCircle2, AlertOctagon, X as XIcon, Stethoscope,
  FlaskConical, Scan, HeartPulse, Siren, Baby, Pill,
  CornerDownRight, ChevronRight, type LucideIcon,
} from 'lucide-react';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { searchVisits, Visit, VisitType, VisitStatus } from './api';
import { cn } from '@/shared/ui/cn';

const TYPE_ICON: Record<VisitType, LucideIcon> = {
  DOCTOR_APPOINTMENT: Stethoscope,
  LABORATORY: FlaskConical,
  RADIOLOGY: Scan,
  ECO: HeartPulse,
  EMERGENCY: Siren,
  PREMATURE: Baby,
  PHARMACY: Pill,
};
/** A status grouping with its visual presentation rules. Labels/descriptions resolved via i18n. */
const STATUS_GROUPS: Array<{
  key: 'NEEDS_ACTION' | 'IN_FLIGHT' | 'CASHIER' | 'COMPLETED' | 'CLOSED';
  labelKey: string;
  descKey: string | null;
  matches: VisitStatus[];
  tone: 'danger' | 'warning' | 'info' | 'success' | 'neutral';
  defaultOpen: boolean;
}> = [
  { key: 'NEEDS_ACTION', labelKey: 'visitQueue.group.needsAction',    descKey: 'visitQueue.group.needsActionDesc', matches: ['OUTSTANDING_BALANCE', 'CANCELLED'],                     tone: 'danger',  defaultOpen: true },
  { key: 'CASHIER',      labelKey: 'visitQueue.group.atCashier',      descKey: 'visitQueue.group.atCashierDesc',   matches: ['AWAITING_PAYMENT', 'AWAITING_FINAL_PAYMENT'],            tone: 'warning', defaultOpen: true },
  { key: 'IN_FLIGHT',    labelKey: 'visitQueue.group.inProgress',     descKey: 'visitQueue.group.inProgressDesc',  matches: ['IN_PROGRESS', 'AWAITING_RESULTS', 'TREATMENT_FINISHED'], tone: 'info',    defaultOpen: true },
  { key: 'COMPLETED',    labelKey: 'visitQueue.group.completedToday', descKey: null,                               matches: ['COMPLETED'],                                            tone: 'success', defaultOpen: false },
  { key: 'CLOSED',       labelKey: 'visitQueue.group.createdWaiting', descKey: 'visitQueue.group.createdWaitingDesc', matches: ['CREATED'],                                           tone: 'neutral', defaultOpen: false },
];

const STATUS_TONE: Record<VisitStatus, 'neutral'|'info'|'success'|'warning'|'brand'|'danger'> = {
  CREATED:                'neutral',
  AWAITING_PAYMENT:       'warning',
  IN_PROGRESS:            'info',
  AWAITING_RESULTS:       'info',
  TREATMENT_FINISHED:     'brand',
  AWAITING_FINAL_PAYMENT: 'warning',
  COMPLETED:              'success',
  CANCELLED:              'neutral',
  OUTSTANDING_BALANCE:    'danger',
};

const VISIT_TYPES: VisitType[] = ['DOCTOR_APPOINTMENT', 'LABORATORY', 'RADIOLOGY', 'ECO', 'EMERGENCY', 'PREMATURE'];

export function VisitQueuePage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [typeFilter, setTypeFilter] = useState<VisitType | null>(null);
  const [groupFilter, setGroupFilter] = useState<typeof STATUS_GROUPS[number]['key'] | null>(null);
  const [query, setQuery] = useState('');

  // Fetch a generous page; visits are bounded enough to filter client-side.
  const { data, isLoading } = useQuery({
    queryKey: ['visits-all', typeFilter],
    queryFn: () => searchVisits(typeFilter, null, 0, 100),
    refetchInterval: 12000,
  });

  const all = data?.content ?? [];

  // Search across visit id, patient name, MRN
  const filtered = useMemo(() => {
    if (!query.trim()) return all;
    const n = query.trim().toLowerCase();
    return all.filter((v) =>
      v.visitDisplayId.toLowerCase().includes(n)
      || v.patientName.toLowerCase().includes(n)
      || v.patientMrn.toLowerCase().includes(n));
  }, [all, query]);

  // Group visits by status group; oldest first inside each group (most-attention-worthy on top).
  const groups = useMemo(() => {
    const map = new Map<string, Visit[]>();
    for (const g of STATUS_GROUPS) map.set(g.key, []);
    for (const v of filtered) {
      const g = STATUS_GROUPS.find((g) => g.matches.includes(v.status));
      if (g) map.get(g.key)!.push(v);
    }
    for (const arr of map.values()) {
      arr.sort((a, b) => new Date(a.startedAt).getTime() - new Date(b.startedAt).getTime());
    }
    return map;
  }, [filtered]);

  const counts = useMemo(() => {
    const out: Record<string, number> = {};
    for (const g of STATUS_GROUPS) out[g.key] = (groups.get(g.key) ?? []).length;
    return out;
  }, [groups]);

  const dt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: 'short' }),
    [i18n.language],
  );

  const visibleGroups = STATUS_GROUPS.filter((g) => groupFilter == null || groupFilter === g.key);

  const openVisit = (v: Visit) => {
    if (v.visitType === 'DOCTOR_APPOINTMENT' || v.visitType === 'EMERGENCY' || v.visitType === 'PREMATURE') {
      navigate(`/clinical/exam/${v.id}`);
    } else if (v.visitType === 'LABORATORY') {
      navigate('/departments/laboratory');
    } else if (v.visitType === 'RADIOLOGY') {
      navigate('/departments/radiology');
    } else if (v.visitType === 'ECO') {
      navigate('/departments/eco');
    } else {
      navigate(`/patients/${v.patientId}`);
    }
  };

  return (
    <>
      <PageHeader
        title={t('visitQueue.title')}
        description={t('visitQueue.description')}
      />

      {/* ======================== KPI tiles (clickable filters) ======================== */}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <KpiTile
          icon={ListOrdered} tone="brand" label={t('visitQueue.kpiTotalActive')}
          value={filtered.filter((v) => v.status !== 'COMPLETED' && v.status !== 'CANCELLED').length}
          active={groupFilter === null}
          onClick={() => setGroupFilter(null)}
        />
        <KpiTile
          icon={AlertOctagon} tone="danger" label={t('visitQueue.kpiNeedsAction')}
          value={counts.NEEDS_ACTION}
          active={groupFilter === 'NEEDS_ACTION'}
          onClick={() => setGroupFilter(groupFilter === 'NEEDS_ACTION' ? null : 'NEEDS_ACTION')}
        />
        <KpiTile
          icon={Wallet} tone="warning" label={t('visitQueue.kpiAtCashier')}
          value={counts.CASHIER}
          active={groupFilter === 'CASHIER'}
          onClick={() => setGroupFilter(groupFilter === 'CASHIER' ? null : 'CASHIER')}
        />
        <KpiTile
          icon={Activity} tone="info" label={t('visitQueue.kpiInProgress')}
          value={counts.IN_FLIGHT}
          active={groupFilter === 'IN_FLIGHT'}
          onClick={() => setGroupFilter(groupFilter === 'IN_FLIGHT' ? null : 'IN_FLIGHT')}
        />
        <KpiTile
          icon={CheckCircle2} tone="success" label={t('visitQueue.kpiCompletedToday')}
          value={counts.COMPLETED}
          active={groupFilter === 'COMPLETED'}
          onClick={() => setGroupFilter(groupFilter === 'COMPLETED' ? null : 'COMPLETED')}
        />
      </div>

      <Card>
        {/* ======================== Toolbar ======================== */}
        <div className="space-y-3 border-b border-ink-100 p-3">
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('visitQueue.searchPlaceholder')}
              className="h-9 w-full rounded-lg border border-ink-200 bg-white ps-9 pe-8 text-sm placeholder:text-ink-400 focus:border-brand-500"
            />
            {query && (
              <button type="button" onClick={() => setQuery('')} className="absolute end-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-ink-400 hover:bg-ink-100 hover:text-ink-700">
                <XIcon size={12} />
              </button>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="font-medium text-ink-600">{t('visitQueue.type')}</span>
            <FilterPill active={typeFilter === null} onClick={() => setTypeFilter(null)}>{t('common.all')}</FilterPill>
            {VISIT_TYPES.map((vt) => {
              const Icon = TYPE_ICON[vt];
              return (
                <FilterPill key={vt} active={typeFilter === vt} onClick={() => setTypeFilter(typeFilter === vt ? null : vt)}>
                  <Icon size={11} className="me-1" /> {t(`visitType.${vt}`)}
                </FilterPill>
              );
            })}
            {(query || typeFilter || groupFilter) && (
              <button type="button" onClick={() => { setQuery(''); setTypeFilter(null); setGroupFilter(null); }} className="ms-auto text-ink-500 hover:text-ink-900">
                {t('visitQueue.clearFilters')}
              </button>
            )}
          </div>
        </div>

        {/* ======================== Groups ======================== */}
        {isLoading ? (
          <TableSkeleton />
        ) : filtered.length === 0 ? (
          <EmptyState icon={ListOrdered} title={t('visitQueue.nothingHere')} description={query ? t('visitQueue.noMatch', { query }) : t('visitQueue.noActive')} />
        ) : (
          <div>
            {visibleGroups.map((g) => {
              const rows = groups.get(g.key) ?? [];
              if (rows.length === 0 && !g.defaultOpen && groupFilter == null) return null;
              return (
                <StatusGroup
                  key={g.key}
                  title={t(g.labelKey)}
                  description={g.descKey ? t(g.descKey) : ''}
                  tone={g.tone}
                  count={rows.length}
                  rows={rows}
                  defaultOpen={g.defaultOpen}
                  onOpen={openVisit}
                  dt={dt}
                />
              );
            })}
          </div>
        )}
      </Card>
    </>
  );
}

/* ============================================================== Status group ============================================================== */

function StatusGroup({
  title, description, tone, count, rows, defaultOpen, onOpen, dt,
}: {
  title: string;
  description: string;
  tone: 'danger' | 'warning' | 'info' | 'success' | 'neutral';
  count: number;
  rows: Visit[];
  defaultOpen: boolean;
  onOpen: (v: Visit) => void;
  dt: Intl.DateTimeFormat;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(defaultOpen);
  const accentBar = {
    danger:  'bg-brand-600',
    warning: 'bg-amber-500',
    info:    'bg-sky-500',
    success: 'bg-emerald-500',
    neutral: 'bg-ink-300',
  }[tone];
  const accentText = {
    danger:  'text-brand-700',
    warning: 'text-amber-700',
    info:    'text-sky-700',
    success: 'text-emerald-700',
    neutral: 'text-ink-700',
  }[tone];

  return (
    <section className="border-b border-ink-100 last:border-b-0">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between gap-3 px-5 py-3 text-start hover:bg-ink-50"
      >
        <div className="flex items-center gap-3">
          <span className={cn('h-6 w-1 rounded-full', accentBar)} />
          <div>
            <div className="flex items-center gap-2">
              <h3 className={cn('text-sm font-semibold', accentText)}>{title}</h3>
              <span className="text-xs text-ink-500">{count}</span>
            </div>
            {description && open && <p className="text-[11px] text-ink-500">{description}</p>}
          </div>
        </div>
        <ChevronRight size={14} className={cn('text-ink-400 transition-transform', open && 'rotate-90 rtl:-rotate-90', !open && 'rtl:rotate-180')} />
      </button>
      {open && (
        rows.length === 0 ? (
          <p className="px-5 pb-3 text-xs text-ink-500">{t('visitQueue.nothingInGroup')}</p>
        ) : (
          <table className="w-full text-sm">
            <tbody className="divide-y divide-ink-100">
              {rows.map((v) => <Row key={v.id} v={v} onOpen={onOpen} dt={dt} />)}
            </tbody>
          </table>
        )
      )}
    </section>
  );
}

/* ============================================================== Row ============================================================== */

function Row({ v, onOpen, dt }: { v: Visit; onOpen: (v: Visit) => void; dt: Intl.DateTimeFormat }) {
  const { t } = useTranslation();
  const Icon = TYPE_ICON[v.visitType] ?? ListOrdered;
  const ageMin = visitAgeMinutes(v);
  const ageTone = ageColor(ageMin, v.status);

  return (
    <tr
      onClick={() => onOpen(v)}
      className={cn(
        'group cursor-pointer transition-colors hover:bg-brand-50/30',
        ageTone === 'critical' && 'bg-brand-50/40',
        ageTone === 'warn'     && 'bg-amber-50/40',
      )}
    >
      <td className="ps-5 pe-2 py-2.5">
        <Icon size={14} className="text-ink-500 group-hover:text-brand-600" />
      </td>
      <td className="px-2 py-2.5">
        <span className="font-mono text-xs font-semibold text-ink-900">{v.visitDisplayId}</span>
        {v.parentVisitId && (
          <div className="mt-0.5 inline-flex items-center gap-1 text-[10px] text-ink-500">
            <CornerDownRight size={10} className="rtl:-scale-x-100" /> {t('visitQueue.fromOrigin', { origin: t(`visitType.${v.originatingType ?? 'DOCTOR_APPOINTMENT'}`) })}
          </div>
        )}
      </td>
      <td className="px-2 py-2.5">
        <Link
          to={`/patients/${v.patientId}`}
          onClick={(e) => e.stopPropagation()}
          className="block text-sm font-medium text-ink-900 hover:text-brand-700 hover:underline"
        >
          {v.patientName}
        </Link>
        <div className="font-mono text-[11px] text-ink-500">{v.patientMrn}</div>
      </td>
      <td className="px-2 py-2.5 text-xs text-ink-700">
        <Badge tone="info">{t(`visitType.${v.visitType}`)}</Badge>
      </td>
      <td className="px-2 py-2.5">
        <Badge tone={STATUS_TONE[v.status]} dot>{t(`visitStatus.${v.status}`)}</Badge>
      </td>
      <td className="px-2 py-2.5 text-xs text-ink-500">
        {dt.format(new Date(v.startedAt))}
      </td>
      <td className="px-2 py-2.5 text-end">
        <span className={cn(
          'inline-flex items-center gap-1 font-mono text-xs font-medium',
          ageTone === 'critical' ? 'text-brand-700' :
          ageTone === 'warn'     ? 'text-amber-700' :
                                   'text-ink-600',
        )}>
          {ageTone !== 'normal' && <Clock size={11} />}
          {humanAge(ageMin, t)}
        </span>
      </td>
      <td className="pe-5 ps-2 py-2.5 text-end">
        <ChevronRight size={14} className="text-ink-400 transition-transform group-hover:translate-x-0.5 group-hover:text-brand-600 rtl:rotate-180" />
      </td>
    </tr>
  );
}

/* ============================================================== Helpers ============================================================== */

function visitAgeMinutes(v: Visit): number {
  const ref = v.endedAt ? new Date(v.endedAt) : new Date();
  return Math.max(0, (ref.getTime() - new Date(v.startedAt).getTime()) / 60000);
}

function humanAge(minutes: number, t: TFunction): string {
  const m = Math.round(minutes);
  if (m < 1)  return t('visitQueue.justNow');
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  if (h < 24) return rem ? `${h}h ${rem}m` : `${h}h`;
  return `${Math.floor(h / 24)}d`;
}

/** Returns a wait-tone based on how long the visit has been waiting in its current state. */
function ageColor(minutes: number, status: VisitStatus): 'normal' | 'warn' | 'critical' {
  // Active stuck states deserve attention sooner than passive ones.
  if (status === 'OUTSTANDING_BALANCE') return 'critical';
  if (status === 'AWAITING_PAYMENT' || status === 'AWAITING_FINAL_PAYMENT') {
    if (minutes >= 60) return 'critical';
    if (minutes >= 20) return 'warn';
  }
  if (status === 'AWAITING_RESULTS') {
    if (minutes >= 240) return 'critical';
    if (minutes >= 90)  return 'warn';
  }
  if (status === 'IN_PROGRESS' || status === 'TREATMENT_FINISHED') {
    if (minutes >= 240) return 'warn';
  }
  if (status === 'CREATED') {
    if (minutes >= 30) return 'warn';
  }
  return 'normal';
}

/* ============================================================== UI bits ============================================================== */

function KpiTile({
  icon: Icon, tone, label, value, active, onClick,
}: {
  icon: LucideIcon;
  tone: 'brand' | 'danger' | 'warning' | 'info' | 'success';
  label: string;
  value: number;
  active: boolean;
  onClick: () => void;
}) {
  const cls = {
    brand:   'bg-brand-50 text-brand-700',
    danger:  'bg-brand-100 text-brand-800',
    warning: 'bg-amber-50 text-amber-700',
    info:    'bg-sky-50 text-sky-700',
    success: 'bg-emerald-50 text-emerald-700',
  }[tone];
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex items-center justify-between gap-3 rounded-xl border bg-white p-4 text-start shadow-card transition-all',
        active
          ? 'border-brand-500 ring-2 ring-brand-200'
          : 'border-ink-200 hover:border-ink-300',
      )}
    >
      <div>
        <div className="text-[11px] font-medium uppercase tracking-wide text-ink-500">{label}</div>
        <div className="mt-1 text-2xl font-semibold tabular-nums text-ink-900">{value}</div>
      </div>
      <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', cls)}>
        <Icon size={18} />
      </span>
    </button>
  );
}

function FilterPill({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'inline-flex h-7 items-center rounded-full px-3 text-xs font-medium transition-colors',
        active ? 'bg-brand-600 text-white' : 'border border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
      )}
    >
      {children}
    </button>
  );
}

function TableSkeleton() {
  return (
    <div className="space-y-2 p-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="h-5 w-5" />
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-5 w-16" />
        </div>
      ))}
    </div>
  );
}
