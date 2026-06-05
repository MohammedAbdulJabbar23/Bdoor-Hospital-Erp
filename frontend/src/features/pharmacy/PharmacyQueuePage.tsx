import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  Pill, Hourglass, Wallet, PackageCheck, AlertOctagon,
  Search, X as XIcon, ChevronRight, Clock, Receipt, Hand,
  StickyNote, AlertTriangle, FileText,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Card } from '@/shared/ui/Card';
import { Button } from '@/shared/ui/Button';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Input } from '@/shared/ui/Input';
import { extractApiError } from '@/shared/api/client';
import { cn } from '@/shared/ui/cn';
import {
  searchDispenses, dispenseSummary, chargeDispense, markGivenDispense, cancelDispense,
  Dispense, DispenseStatus,
} from './api';

type GroupKey = 'PENDING' | 'AWAITING_PAYMENT' | 'READY_TO_GIVE' | 'DISPENSED' | 'CANCELLED';

const STATUS_GROUPS: Array<{
  key: GroupKey;
  label: string;
  description: string;
  status: DispenseStatus;
  tone: 'danger' | 'warning' | 'info' | 'success' | 'neutral';
  defaultOpen: boolean;
}> = [
  { key: 'PENDING',          label: 'New prescriptions',  description: 'Send to cashier or cancel',         status: 'PENDING',          tone: 'danger',  defaultOpen: true  },
  { key: 'AWAITING_PAYMENT', label: 'At cashier',         description: 'Waiting on payment approval',        status: 'AWAITING_PAYMENT', tone: 'warning', defaultOpen: true  },
  { key: 'READY_TO_GIVE',    label: 'Ready to dispense',  description: 'Paid — hand meds to the patient',    status: 'READY_TO_GIVE',    tone: 'info',    defaultOpen: true  },
  { key: 'DISPENSED',        label: 'Dispensed today',    description: '',                                   status: 'DISPENSED',        tone: 'success', defaultOpen: false },
  { key: 'CANCELLED',        label: 'Cancelled',          description: '',                                   status: 'CANCELLED',        tone: 'neutral', defaultOpen: false },
];

export function PharmacyQueuePage() {
  const { i18n } = useTranslation();
  const [query, setQuery] = useState('');
  const [groupFilter, setGroupFilter] = useState<GroupKey | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const queryClient = useQueryClient();

  // Paginated row listing (capped at 200 server-side). Used only to render rows, NOT for KPI counts.
  const { data, isLoading } = useQuery({
    queryKey: ['dispenses-all'],
    queryFn: () => searchDispenses(null, 0, 200),
    refetchInterval: 12000,
  });

  // Server-computed KPI counts (DB-aggregated) so the tiles stay correct beyond the 200-row cap.
  const { data: summary } = useQuery({
    queryKey: ['dispenses-summary'],
    queryFn: () => dispenseSummary(),
    refetchInterval: 12000,
  });

  const all = data?.content ?? [];

  const filtered = useMemo(() => {
    if (!query.trim()) return all;
    const n = query.trim().toLowerCase();
    return all.filter((d) =>
      d.dispenseDisplayId.toLowerCase().includes(n)
      || d.patientName.toLowerCase().includes(n)
      || d.patientMrn.toLowerCase().includes(n)
      || d.visitDisplayId.toLowerCase().includes(n));
  }, [all, query]);

  const groups = useMemo(() => {
    const map = new Map<GroupKey, Dispense[]>();
    for (const g of STATUS_GROUPS) map.set(g.key, []);
    for (const d of filtered) {
      const g = STATUS_GROUPS.find((g) => g.status === d.status);
      if (g) map.get(g.key)!.push(d);
    }
    // Active groups: oldest first; terminal: most-recent first.
    for (const g of STATUS_GROUPS) {
      const arr = map.get(g.key)!;
      if (g.key === 'DISPENSED' || g.key === 'CANCELLED') {
        arr.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      } else {
        arr.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
      }
    }
    return map;
  }, [filtered]);

  // KPI tile counts come from the SERVER summary (authoritative DB totals across the whole queue),
  // so they're correct even past the 200-row listing cap. When a search query is active the summary
  // can't reflect it (search is client-side over the loaded window), so fall back to the counts of
  // the locally-filtered rows. "Dispensed today" always uses the date-scoped server count.
  const searching = query.trim().length > 0;
  const byStatus = summary?.byStatus ?? {};
  const localCount = (k: GroupKey) => (groups.get(k) ?? []).length;
  const counts: Record<GroupKey, number> = {
    PENDING:          searching ? localCount('PENDING')          : (byStatus.PENDING          ?? 0),
    AWAITING_PAYMENT: searching ? localCount('AWAITING_PAYMENT') : (byStatus.AWAITING_PAYMENT ?? 0),
    READY_TO_GIVE:    searching ? localCount('READY_TO_GIVE')    : (byStatus.READY_TO_GIVE    ?? 0),
    DISPENSED:        searching ? localCount('DISPENSED')        : (byStatus.DISPENSED        ?? 0),
    CANCELLED:        searching ? localCount('CANCELLED')        : (byStatus.CANCELLED        ?? 0),
  };

  // Today's dispensed (for the "Dispensed today" KPI) — server date-scoped count, uncapped. While
  // searching, fall back to the locally-loaded DISPENSED-today rows.
  const dispensedTodayCount = useMemo(() => {
    if (searching) {
      const today = new Date(); today.setHours(0, 0, 0, 0);
      return all.filter((d) => d.status === 'DISPENSED' && d.givenAt && new Date(d.givenAt) >= today).length;
    }
    return summary?.dispensedToday ?? 0;
  }, [all, summary, searching]);

  const fmt = useMemo(
    () => new Intl.NumberFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-US', { maximumFractionDigits: 0 }),
    [i18n.language],
  );
  const dt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: 'short' }),
    [i18n.language],
  );

  const onMutationSuccess = async (msg: string) => {
    toast.success(msg);
    await queryClient.invalidateQueries({ queryKey: ['dispenses-all'] });
    await queryClient.invalidateQueries({ queryKey: ['payments'] });
    await queryClient.invalidateQueries({ queryKey: ['payments-pending-overview'] });
  };
  const onMutationError = (err: unknown) => toast.error(extractApiError(err)?.message ?? 'Action failed');

  const chargeMut    = useMutation({ mutationFn: (id: string) => chargeDispense(id),    onSuccess: (d) => onMutationSuccess(`${d.dispenseDisplayId} sent to cashier`), onError: onMutationError });
  const giveMut      = useMutation({ mutationFn: (id: string) => markGivenDispense(id), onSuccess: (d) => onMutationSuccess(`${d.dispenseDisplayId} dispensed`),         onError: onMutationError });
  const cancelMut    = useMutation({ mutationFn: ({ id, reason }: { id: string; reason: string }) => cancelDispense(id, reason),
                                     onSuccess: (d) => onMutationSuccess(`${d.dispenseDisplayId} cancelled`), onError: onMutationError });

  const visibleGroups = STATUS_GROUPS.filter((g) => groupFilter == null || groupFilter === g.key);

  return (
    <>
      <PageHeader
        title="Pharmacy"
        description="Dispense queue for prescriptions from finalized doctor exams. Auto-refreshes every 12s."
      />

      {/* ======================== KPI tiles ======================== */}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <KpiTile
          icon={AlertOctagon} tone="danger" label="New prescriptions"
          value={counts.PENDING}
          active={groupFilter === 'PENDING'}
          onClick={() => setGroupFilter(groupFilter === 'PENDING' ? null : 'PENDING')}
          hint={counts.PENDING === 0 ? 'No backlog' : 'Send to cashier or cancel'}
        />
        <KpiTile
          icon={Wallet} tone="warning" label="At cashier"
          value={counts.AWAITING_PAYMENT}
          active={groupFilter === 'AWAITING_PAYMENT'}
          onClick={() => setGroupFilter(groupFilter === 'AWAITING_PAYMENT' ? null : 'AWAITING_PAYMENT')}
          hint="Awaiting cashier approval"
        />
        <KpiTile
          icon={Hourglass} tone="info" label="Ready to dispense"
          value={counts.READY_TO_GIVE}
          active={groupFilter === 'READY_TO_GIVE'}
          onClick={() => setGroupFilter(groupFilter === 'READY_TO_GIVE' ? null : 'READY_TO_GIVE')}
          hint="Paid — hand to patient"
        />
        <KpiTile
          icon={PackageCheck} tone="success" label="Dispensed today"
          value={dispensedTodayCount}
          active={groupFilter === 'DISPENSED'}
          onClick={() => setGroupFilter(groupFilter === 'DISPENSED' ? null : 'DISPENSED')}
          hint={`${counts.DISPENSED} dispensed all-time`}
        />
      </div>

      <Card>
        {/* ======================== Toolbar ======================== */}
        <div className="space-y-3 border-b border-ink-100 p-3">
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="search" value={query} onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by dispense ID, patient name, MRN, or visit…"
              className="h-9 w-full rounded-lg border border-ink-200 bg-white ps-9 pe-8 text-sm placeholder:text-ink-400 focus:border-brand-500"
            />
            {query && (
              <button type="button" onClick={() => setQuery('')} className="absolute end-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-ink-400 hover:bg-ink-100 hover:text-ink-700">
                <XIcon size={12} />
              </button>
            )}
          </div>
          {(query || groupFilter) && (
            <div className="flex justify-end">
              <button type="button" onClick={() => { setQuery(''); setGroupFilter(null); }} className="text-xs text-ink-500 hover:text-ink-900">
                Clear filters
              </button>
            </div>
          )}
        </div>

        {/* ======================== Groups ======================== */}
        {isLoading ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3"><Skeleton className="h-5 flex-1" /></div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={Pill}
            title={query ? 'No matches' : 'No dispenses'}
            description={
              query
                ? `Nothing matches "${query}".`
                : 'Dispenses appear here when a doctor finalizes an exam with prescriptions.'
            }
          />
        ) : (
          <div>
            {visibleGroups.map((g) => {
              const rows = groups.get(g.key) ?? [];
              if (rows.length === 0 && !g.defaultOpen && groupFilter == null) return null;
              return (
                <StatusGroup key={g.key} title={g.label} description={g.description} tone={g.tone} count={rows.length} defaultOpen={g.defaultOpen}>
                  {rows.length === 0 ? (
                    <p className="px-5 pb-3 text-xs text-ink-500">Nothing in this group.</p>
                  ) : (
                    <ul className="divide-y divide-ink-100">
                      {rows.map((d) => (
                        <DispenseRow
                          key={d.id}
                          d={d}
                          expanded={expandedId === d.id}
                          onToggle={() => setExpandedId((v) => (v === d.id ? null : d.id))}
                          onCharge={() => chargeMut.mutate(d.id)}
                          onMarkGiven={() => giveMut.mutate(d.id)}
                          onCancel={(reason) => cancelMut.mutate({ id: d.id, reason })}
                          chargeLoading={chargeMut.isPending && chargeMut.variables === d.id}
                          giveLoading={giveMut.isPending && giveMut.variables === d.id}
                          cancelLoading={cancelMut.isPending && (cancelMut.variables as { id: string } | undefined)?.id === d.id}
                          fmt={fmt}
                          dt={dt}
                        />
                      ))}
                    </ul>
                  )}
                </StatusGroup>
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
  title, description, tone, count, defaultOpen, children,
}: {
  title: string;
  description: string;
  tone: 'danger' | 'warning' | 'info' | 'success' | 'neutral';
  count: number;
  defaultOpen: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const accentBar = {
    danger:  'bg-brand-600', warning: 'bg-amber-500', info: 'bg-sky-500',
    success: 'bg-emerald-500', neutral: 'bg-ink-300',
  }[tone];
  const accentText = {
    danger:  'text-brand-700', warning: 'text-amber-700', info: 'text-sky-700',
    success: 'text-emerald-700', neutral: 'text-ink-700',
  }[tone];

  return (
    <section className="border-b border-ink-100 last:border-b-0">
      <button type="button" onClick={() => setOpen((v) => !v)} className="flex w-full items-center justify-between gap-3 px-5 py-3 text-start hover:bg-ink-50">
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
        <ChevronRight size={14} className={cn('text-ink-400 transition-transform', open && 'rotate-90', !open && 'rtl:rotate-180')} />
      </button>
      {open && children}
    </section>
  );
}

/* ============================================================== Row + inline detail ============================================================== */

function DispenseRow({
  d, expanded, onToggle, onCharge, onMarkGiven, onCancel,
  chargeLoading, giveLoading, cancelLoading,
  fmt, dt,
}: {
  d: Dispense;
  expanded: boolean;
  onToggle: () => void;
  onCharge: () => void;
  onMarkGiven: () => void;
  onCancel: (reason: string) => void;
  chargeLoading: boolean;
  giveLoading: boolean;
  cancelLoading: boolean;
  fmt: Intl.NumberFormat;
  dt: Intl.DateTimeFormat;
}) {
  const [showCancel, setShowCancel] = useState(false);
  const [cancelReason, setCancelReason] = useState('');

  const ageMin = waitMinutes(d);
  const ageTone = waitTone(ageMin, d.status);
  const billable = d.lines.filter((l) => l.billable);
  const nothingToBill = billable.length === 0;

  return (
    <li className={cn(
      ageTone === 'critical' && (d.status === 'PENDING' || d.status === 'AWAITING_PAYMENT') && 'bg-brand-50/40',
      ageTone === 'warn'     && (d.status === 'PENDING' || d.status === 'AWAITING_PAYMENT') && 'bg-amber-50/40',
    )}>
      <button type="button" onClick={onToggle} className="flex w-full items-center gap-3 px-5 py-3 text-start hover:bg-ink-50/60">
        <Pill size={14} className="text-ink-500" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs font-semibold text-ink-900">{d.dispenseDisplayId}</span>
            <span className="text-ink-300">·</span>
            <span className="font-medium text-ink-900 truncate">{d.patientName}</span>
            <span className="font-mono text-[11px] text-ink-500">{d.patientMrn}</span>
          </div>
          <div className="mt-0.5 flex items-center gap-2 text-[11px] text-ink-500">
            <span>Dr. {d.doctorName}</span>
            <span>·</span>
            <span className="font-mono">{d.visitDisplayId}</span>
            <span>·</span>
            <span>{d.lines.length} drug{d.lines.length === 1 ? '' : 's'}{nothingToBill && ' · informational only'}</span>
          </div>
        </div>
        <div className="flex flex-col items-end gap-0.5 shrink-0">
          <div className="font-mono text-sm font-semibold text-ink-900 tabular-nums">
            {d.billableTotal > 0 ? fmt.format(d.billableTotal) : '—'}
          </div>
          <div className={cn(
            'inline-flex items-center gap-1 font-mono text-[11px]',
            ageTone === 'critical' ? 'text-brand-700' :
            ageTone === 'warn'     ? 'text-amber-700' :
                                     'text-ink-500',
          )}>
            {ageTone === 'critical' && <AlertTriangle size={10} />}
            {ageTone === 'warn'     && <Clock size={10} />}
            {humanAge(ageMin)}
          </div>
        </div>
        <ChevronRight size={14} className={cn('text-ink-400 transition-transform shrink-0', expanded && 'rotate-90')} />
      </button>

      {expanded && (
        <div className="border-t border-ink-100 bg-ink-50/40 px-5 py-4">
          {/* Drug lines */}
          <div className="rounded-lg border border-ink-100 bg-white">
            <table className="w-full text-sm">
              <thead className="border-b border-ink-100 text-[10px] font-semibold uppercase tracking-wide text-ink-500">
                <tr>
                  <th className="px-3 py-2 text-start">Drug</th>
                  <th className="px-3 py-2 text-start">Dose / frequency</th>
                  <th className="px-3 py-2 text-start">Duration</th>
                  <th className="px-3 py-2 text-end">Qty</th>
                  <th className="px-3 py-2 text-end">Line total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {d.lines.map((l, i) => (
                  <tr key={i} className={cn(!l.billable && 'bg-ink-50/40')}>
                    <td className="px-3 py-2">
                      <div className="font-medium text-ink-900">{l.drugName}</div>
                      <div className="text-[11px] text-ink-500">
                        {l.strength}{l.route && ` · ${l.route}`}
                        {l.drugCode && (
                          <span className="ms-1 rounded bg-ink-100 px-1 font-mono text-[10px]">{l.drugCode}</span>
                        )}
                        {!l.billable && (
                          <span className="ms-1 inline-flex items-center gap-0.5 rounded bg-amber-100 px-1 text-[10px] text-amber-800">
                            <StickyNote size={9} /> not in catalogue
                          </span>
                        )}
                      </div>
                      {l.notes && <div className="mt-1 text-[11px] italic text-ink-500">{l.notes}</div>}
                    </td>
                    <td className="px-3 py-2 text-ink-700">
                      {l.dose && <div>{l.dose}</div>}
                      {l.frequency && <div className="text-[11px] text-ink-500">{l.frequency}</div>}
                    </td>
                    <td className="px-3 py-2 text-ink-700">{l.duration ?? '—'}</td>
                    <td className="px-3 py-2 text-end font-mono">{l.quantity}</td>
                    <td className="px-3 py-2 text-end font-mono text-ink-900">
                      {l.lineTotal != null ? fmt.format(l.lineTotal) : <span className="text-ink-400">—</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
              {d.billableTotal > 0 && (
                <tfoot className="border-t border-ink-100 bg-ink-50/60 text-sm font-semibold">
                  <tr>
                    <td colSpan={4} className="px-3 py-2 text-end text-ink-700">Total</td>
                    <td className="px-3 py-2 text-end font-mono text-ink-900">{fmt.format(d.billableTotal)} IQD</td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>

          {/* Timeline / metadata */}
          <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] text-ink-600 sm:grid-cols-4">
            <Meta label="Created"   value={dt.format(new Date(d.createdAt))} />
            <Meta label="Charged"   value={d.chargedAt ? dt.format(new Date(d.chargedAt)) : '—'} />
            <Meta label="Paid"      value={d.paidAt ? dt.format(new Date(d.paidAt)) : '—'} />
            <Meta label="Given"     value={d.givenAt ? dt.format(new Date(d.givenAt)) : '—'} />
          </div>
          {d.cancelledReason && (
            <div className="mt-2 rounded-md border border-ink-200 bg-white p-2 text-[11px] text-ink-700">
              <span className="font-semibold">Cancelled:</span> {d.cancelledReason}
            </div>
          )}

          {/* Actions */}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
            <div className="flex flex-wrap items-center gap-2">
              <Link to={`/clinical/exam/${d.visitId}`}>
                <Button variant="secondary" size="sm" type="button">
                  <FileText size={14} className="me-1.5" /> View doctor exam
                </Button>
              </Link>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {d.status === 'PENDING' && !nothingToBill && (
                <Button type="button" size="sm" onClick={onCharge} disabled={chargeLoading}>
                  <Receipt size={14} className="me-1.5" />
                  {chargeLoading ? 'Sending…' : `Send to cashier · ${fmt.format(d.billableTotal)} IQD`}
                </Button>
              )}
              {d.status === 'READY_TO_GIVE' && (
                <Button type="button" size="sm" onClick={onMarkGiven} disabled={giveLoading} className="bg-emerald-600 hover:bg-emerald-700">
                  <Hand size={14} className="me-1.5" />
                  {giveLoading ? 'Marking…' : 'Mark given to patient'}
                </Button>
              )}
              {(d.status === 'PENDING' || d.status === 'AWAITING_PAYMENT' || d.status === 'READY_TO_GIVE') && (
                <Button type="button" variant="secondary" size="sm" onClick={() => setShowCancel((v) => !v)}>
                  Cancel
                </Button>
              )}
              {d.status === 'PENDING' && nothingToBill && (
                <span className="inline-flex items-center gap-1 text-[11px] text-ink-500">
                  <StickyNote size={11} /> No catalogue drugs to bill — cancel to close
                </span>
              )}
            </div>
          </div>

          {showCancel && (
            <div className="mt-3 rounded-lg border border-ink-200 bg-white p-3">
              <Input
                label="Cancellation reason"
                placeholder="e.g. patient declined, prescription error"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
              />
              <div className="mt-3 flex items-center justify-end gap-2">
                <Button type="button" variant="secondary" size="sm" onClick={() => { setShowCancel(false); setCancelReason(''); }}>
                  Back
                </Button>
                <Button
                  type="button" variant="danger" size="sm"
                  disabled={cancelLoading || !cancelReason.trim()}
                  onClick={() => onCancel(cancelReason.trim())}
                >
                  {cancelLoading ? 'Cancelling…' : 'Confirm cancel'}
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </li>
  );
}

/* ============================================================== Helpers / bits ============================================================== */

function waitMinutes(d: Dispense): number {
  // For active states, use createdAt → now. For terminal, use createdAt → terminal-event time.
  let ref: Date;
  if (d.status === 'DISPENSED' && d.givenAt) ref = new Date(d.givenAt);
  else if (d.status === 'CANCELLED' && d.cancelledAt) ref = new Date(d.cancelledAt);
  else ref = new Date();
  return Math.max(0, (ref.getTime() - new Date(d.createdAt).getTime()) / 60000);
}

function waitTone(minutes: number, status: DispenseStatus): 'normal' | 'warn' | 'critical' {
  if (status === 'PENDING') {
    if (minutes >= 60) return 'critical';
    if (minutes >= 20) return 'warn';
  }
  if (status === 'AWAITING_PAYMENT') {
    if (minutes >= 90) return 'critical';
    if (minutes >= 30) return 'warn';
  }
  if (status === 'READY_TO_GIVE') {
    if (minutes >= 60) return 'warn';
  }
  return 'normal';
}

function humanAge(minutes: number): string {
  const m = Math.round(minutes);
  if (m < 1)  return 'just now';
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  if (h < 24) return rem ? `${h}h ${rem}m` : `${h}h`;
  return `${Math.floor(h / 24)}d`;
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wide text-ink-400">{label}</div>
      <div className="text-ink-700">{value}</div>
    </div>
  );
}

function KpiTile({
  icon: Icon, tone, label, value, hint, active, onClick,
}: {
  icon: LucideIcon;
  tone: 'danger' | 'warning' | 'info' | 'success';
  label: string;
  value: number;
  hint?: string;
  active: boolean;
  onClick: () => void;
}) {
  const cls = {
    danger:  'bg-brand-100 text-brand-800',
    warning: 'bg-amber-50  text-amber-700',
    info:    'bg-sky-50    text-sky-700',
    success: 'bg-emerald-50 text-emerald-700',
  }[tone];
  return (
    <button
      type="button" onClick={onClick}
      className={cn(
        'flex items-start justify-between gap-3 rounded-xl border bg-white p-4 text-start shadow-card transition-all',
        active ? 'border-brand-500 ring-2 ring-brand-200' : 'border-ink-200 hover:border-ink-300',
      )}
    >
      <div>
        <div className="text-[11px] font-medium uppercase tracking-wide text-ink-500">{label}</div>
        <div className="mt-1 text-2xl font-semibold tabular-nums text-ink-900">{value}</div>
        {hint && <div className="mt-0.5 text-[11px] text-ink-500">{hint}</div>}
      </div>
      <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', cls)}>
        <Icon size={18} />
      </span>
    </button>
  );
}
