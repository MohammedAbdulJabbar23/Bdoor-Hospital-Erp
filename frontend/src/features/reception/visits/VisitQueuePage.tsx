import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  ListOrdered,
  Filter,
  ChevronLeft,
  ChevronRight,
  CornerDownRight,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { cn } from '@/shared/ui/cn';
import { searchVisits, Visit, VisitType, VisitStatus } from './api';

const VISIT_TYPES: { value: VisitType; label: string }[] = [
  { value: 'DOCTOR_APPOINTMENT', label: 'Doctor' },
  { value: 'LABORATORY',         label: 'Lab' },
  { value: 'RADIOLOGY',          label: 'Radiology' },
  { value: 'ECO',                label: 'ECO' },
  { value: 'EMERGENCY',          label: 'Emergency' },
  { value: 'PREMATURE',          label: 'Premature' },
];

const STATUS_TONE: Record<VisitStatus, { tone: 'neutral'|'info'|'success'|'warning'|'danger'|'brand'; label: string }> = {
  CREATED:                { tone: 'neutral', label: 'Created' },
  AWAITING_PAYMENT:       { tone: 'warning', label: 'Awaiting payment' },
  IN_PROGRESS:            { tone: 'info',    label: 'In progress' },
  AWAITING_RESULTS:       { tone: 'info',    label: 'Awaiting results' },
  TREATMENT_FINISHED:     { tone: 'brand',   label: 'Treatment finished' },
  AWAITING_FINAL_PAYMENT: { tone: 'warning', label: 'Awaiting final payment' },
  COMPLETED:              { tone: 'success', label: 'Completed' },
  CANCELLED:              { tone: 'neutral', label: 'Cancelled' },
  OUTSTANDING_BALANCE:    { tone: 'danger',  label: 'Outstanding balance' },
};

const TYPE_LABEL: Record<VisitType, string> = {
  DOCTOR_APPOINTMENT: 'Doctor',
  LABORATORY: 'Lab',
  RADIOLOGY: 'Radiology',
  ECO: 'ECO',
  EMERGENCY: 'Emergency',
  PREMATURE: 'Premature',
  PHARMACY: 'Pharmacy',
};

export function VisitQueuePage() {
  const { t, i18n } = useTranslation();
  const [type, setType] = useState<VisitType | null>(null);
  const [status, setStatus] = useState<VisitStatus | null>(null);
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['visits', type, status, page],
    queryFn: () => searchVisits(type, status, page, 20),
    placeholderData: (prev) => prev,
    refetchInterval: 15000,
  });

  const dt = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', {
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: 'short',
      }),
    [i18n.language],
  );

  return (
    <>
      <PageHeader
        title="Visit queue"
        description="All active visits across reception and departments. Auto-refreshes every 15 seconds."
      />

      <Card>
        <div className="flex flex-wrap items-center gap-3 border-b border-ink-100 p-3">
          <Filter size={14} className="text-ink-400" />
          <span className="text-xs font-medium text-ink-600">{t('common.filters')}:</span>

          <FilterPill active={type === null} onClick={() => { setType(null); setPage(0); }}>
            {t('common.all')}
          </FilterPill>
          {VISIT_TYPES.map((v) => (
            <FilterPill
              key={v.value}
              active={type === v.value}
              onClick={() => { setType(v.value); setPage(0); }}
            >
              {v.label}
            </FilterPill>
          ))}

          <span className="mx-1 h-4 w-px bg-ink-200" />

          <select
            value={status ?? ''}
            onChange={(e) => { setStatus((e.target.value || null) as VisitStatus | null); setPage(0); }}
            className="h-7 rounded-md border border-ink-200 bg-white px-2 text-xs"
          >
            <option value="">All statuses</option>
            {(Object.keys(STATUS_TONE) as VisitStatus[]).map((s) => (
              <option key={s} value={s}>{STATUS_TONE[s].label}</option>
            ))}
          </select>
        </div>

        {isLoading ? (
          <TableSkeleton />
        ) : !data || data.content.length === 0 ? (
          <EmptyState
            icon={ListOrdered}
            title="No visits in the queue"
            description="Create a visit from the Reception screen and it'll show up here."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-ink-100 bg-ink-50/60 text-[11px] font-semibold uppercase tracking-wide text-ink-500">
                <tr>
                  <Th>Visit ID</Th>
                  <Th>Patient</Th>
                  <Th>Type</Th>
                  <Th>Status</Th>
                  <Th>Origin</Th>
                  <Th>Started</Th>
                  <Th>Age</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {data.content.map((v) => (
                  <Row key={v.id} v={v} dt={dt} />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-ink-100 px-4 py-3 text-sm text-ink-600">
            <span className="text-xs">
              <span className="font-medium text-ink-900">
                {data.number * data.size + 1}–{data.number * data.size + data.content.length}
              </span>{' '}
              of <span className="font-medium text-ink-900">{data.totalElements}</span>
            </span>
            <div className="flex gap-1">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                <ChevronLeft size={14} className="rtl:rotate-180" />
                {t('common.previous')}
              </Button>
              <Button size="sm" variant="secondary" disabled={page >= data.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
                {t('common.next')}
                <ChevronRight size={14} className="rtl:rotate-180" />
              </Button>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}

function Row({ v, dt }: { v: Visit; dt: Intl.DateTimeFormat }) {
  const tone = STATUS_TONE[v.status];
  return (
    <tr className="group transition-colors hover:bg-ink-50/60">
      <Td>
        <span className="font-mono text-xs font-semibold text-ink-900">{v.visitDisplayId}</span>
        {v.parentVisitId && (
          <div className="mt-0.5 inline-flex items-center gap-1 text-[10px] text-ink-500">
            <CornerDownRight size={10} className="rtl:-scale-x-100" />
            forwarded
          </div>
        )}
      </Td>
      <Td>
        <div className="font-medium text-ink-900">{v.patientName}</div>
        <div className="font-mono text-[11px] text-ink-500">{v.patientMrn}</div>
      </Td>
      <Td>
        <Badge tone="info">{TYPE_LABEL[v.visitType]}</Badge>
      </Td>
      <Td>
        <Badge tone={tone.tone} dot>{tone.label}</Badge>
      </Td>
      <Td>
        {v.origin === 'FORWARDED' && v.originatingType ? (
          <span className="text-xs text-ink-600">
            from <span className="font-medium">{TYPE_LABEL[v.originatingType]}</span>
          </span>
        ) : v.origin === 'DIRECT_NEW' ? (
          <span className="text-xs text-ink-500">Walk-in (new)</span>
        ) : (
          <span className="text-xs text-ink-500">Walk-in</span>
        )}
      </Td>
      <Td>
        <span className="text-xs text-ink-700">{dt.format(new Date(v.startedAt))}</span>
      </Td>
      <Td>
        <span className={cn('text-xs', visitAgeMinutes(v) > 60 ? 'text-amber-700' : 'text-ink-500')}>
          {humanAge(v)}
        </span>
      </Td>
    </tr>
  );
}

function visitAgeMinutes(v: Visit): number {
  const ref = v.endedAt ? new Date(v.endedAt) : new Date();
  return Math.max(0, (ref.getTime() - new Date(v.startedAt).getTime()) / 60000);
}
function humanAge(v: Visit) {
  const m = Math.round(visitAgeMinutes(v));
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem ? `${h}h ${rem}m` : `${h}h`;
}

function FilterPill({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'inline-flex h-7 items-center rounded-full px-3 text-xs font-medium transition-colors',
        active
          ? 'bg-brand-600 text-white'
          : 'border border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
      )}
    >
      {children}
    </button>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={cn('px-4 py-3 text-start font-semibold', className)}>{children}</th>;
}
function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn('px-4 py-3 align-middle', className)}>{children}</td>;
}

function TableSkeleton() {
  return (
    <div className="space-y-2 p-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-5 w-48" />
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-5 w-16" />
        </div>
      ))}
    </div>
  );
}
