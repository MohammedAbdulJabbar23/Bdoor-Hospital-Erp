import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  Wallet, Crown, CheckCircle2, XCircle, Receipt,
  ChevronLeft, ChevronRight, X, Banknote, CreditCard, Building2,
  Search, Clock, AlertTriangle, TrendingUp, Hourglass,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Input } from '@/shared/ui/Input';
import { extractApiError } from '@/shared/api/client';
import {
  searchPayments, approvePayment, rejectPayment,
  Payment, PaymentStatus, PaymentStage,
} from './api';
import { cn } from '@/shared/ui/cn';
import { printRoutingSlip } from '@/shared/print/RoutingSlip';

const STAGE_KEYS: PaymentStage[] = ['INITIAL', 'REFERRAL', 'FINAL', 'STAY_EXTENSION', 'PHARMACY'];

export function CashierQueuePage() {
  const { t, i18n } = useTranslation();
  const [tab, setTab] = useState<PaymentStatus>('PENDING');
  const [stage, setStage] = useState<PaymentStage | null>(null);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [openPayment, setOpenPayment] = useState<Payment | null>(null);
  const [openMode, setOpenMode] = useState<'approve' | 'reject' | null>(null);
  const queryClient = useQueryClient();

  // Page being viewed (filtered by tab/stage server-side, paginated)
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['payments', tab, stage, page],
    queryFn: () => searchPayments(tab, stage, page, 20),
    placeholderData: (prev) => prev,
    refetchInterval: 10000,
  });

  // Pending overview — fetch a flat snapshot of all pending so we can count, sum, slice by stage. Bounded enough to do client-side.
  const pendingOverview = useQuery({
    queryKey: ['payments-pending-overview'],
    queryFn: () => searchPayments('PENDING', null, 0, 200),
    refetchInterval: 10000,
  });
  const pendingAll = pendingOverview.data?.content ?? [];

  // Approved-today snapshot — drives the "received today" KPI.
  const approvedToday = useQuery({
    queryKey: ['payments-approved-today'],
    queryFn: () => searchPayments('APPROVED', null, 0, 200),
    refetchInterval: 30000,
  });
  const approvedTodayList = useMemo(() => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    return (approvedToday.data?.content ?? []).filter((p) => p.decidedAt && new Date(p.decidedAt) >= today);
  }, [approvedToday.data]);

  const fmt = useMemo(
    () => new Intl.NumberFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-US', { maximumFractionDigits: 0 }),
    [i18n.language],
  );
  const dt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: 'short' }),
    [i18n.language],
  );

  // KPI calculations on the pending snapshot
  const pendingCount  = pendingAll.length;
  const cashExposure  = pendingAll.reduce((s, p) => s + p.totalDue, 0);
  const oldestPendingMin = pendingAll.length === 0 ? 0
    : Math.max(...pendingAll.map((p) => paymentAgeMinutes(p)));
  const receivedToday = approvedTodayList.reduce((s, p) => s + p.totalDue, 0);
  const stageCounts: Record<PaymentStage, number> = { INITIAL: 0, REFERRAL: 0, FINAL: 0, STAY_EXTENSION: 0, PHARMACY: 0 };
  for (const p of pendingAll) stageCounts[p.stage]++;

  // Search filter on the page in view (cheap — small page).
  const visibleRows = useMemo(() => {
    const rows = data?.content ?? [];
    if (!query.trim()) return rows;
    const n = query.trim().toLowerCase();
    return rows.filter((p) =>
      p.paymentDisplayId.toLowerCase().includes(n)
      || p.patientName.toLowerCase().includes(n)
      || p.patientMrn.toLowerCase().includes(n)
      || p.visitDisplayId.toLowerCase().includes(n));
  }, [data, query]);

  // Pending: oldest first (most-attention-worthy on top). Approved/rejected: most-recent decision first.
  const sortedRows = useMemo(() => {
    const rows = [...visibleRows];
    if (tab === 'PENDING') {
      rows.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
    } else {
      rows.sort((a, b) => {
        const at = a.decidedAt ? new Date(a.decidedAt).getTime() : 0;
        const bt = b.decidedAt ? new Date(b.decidedAt).getTime() : 0;
        return bt - at;
      });
    }
    return rows;
  }, [visibleRows, tab]);

  const approveMut = useMutation({
    mutationFn: ({ id, method }: { id: string; method: 'CASH' | 'CARD' | 'BANK_TRANSFER' }) => approvePayment(id, method),
    onSuccess: async (p) => {
      toast.success(t('cashier.approvedToast', { id: p.paymentDisplayId }));
      await queryClient.invalidateQueries({ queryKey: ['payments'] });
      await queryClient.invalidateQueries({ queryKey: ['payments-pending-overview'] });
      await queryClient.invalidateQueries({ queryKey: ['payments-approved-today'] });
      // Auto-print routing slip — patient takes this to the next handoff (department/pharmacy).
      printRoutingSlip({
        patientName: p.patientName,
        patientMrn: p.patientMrn,
        visitDisplayId: p.visitDisplayId,
        paymentDisplayId: p.paymentDisplayId,
        fromLabel: t('cashier.fromLabel'),
        toLabel: t(`cashier.stageLabel.${p.stage}`),
        serviceLines: p.lines.map((l) => ({ code: l.code, name: l.name, qty: l.quantity })),
      });
      setOpenPayment(null); setOpenMode(null);
    },
    onError: (err) => {
      const apiErr = extractApiError(err);
      toast.error(apiErr?.message ?? t('cashier.approvalFailed'));
    },
  });

  const rejectMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => rejectPayment(id, reason),
    onSuccess: async (p) => {
      toast.success(t('cashier.rejectedToast', { id: p.paymentDisplayId }));
      await queryClient.invalidateQueries({ queryKey: ['payments'] });
      await queryClient.invalidateQueries({ queryKey: ['payments-pending-overview'] });
      setOpenPayment(null); setOpenMode(null);
    },
    onError: (err) => {
      const apiErr = extractApiError(err);
      toast.error(apiErr?.message ?? t('cashier.rejectionFailed'));
    },
  });

  return (
    <>
      <PageHeader
        title={t('cashier.title')}
        description={t('cashier.description')}
      />

      {/* ======================== KPI strip ======================== */}
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <KpiTile
          icon={Hourglass} tone="warning"
          label={t('cashier.kpiPending')}
          value={String(pendingCount)}
          hint={pendingCount === 0 ? t('cashier.kpiPendingClear') : t('cashier.kpiPendingAwaiting', { count: pendingCount })}
        />
        <KpiTile
          icon={Wallet} tone="info"
          label={t('cashier.kpiCashExposure')}
          value={fmt.format(cashExposure)}
          hint={t('cashier.kpiCashExposureHint')}
        />
        <KpiTile
          icon={Clock}
          tone={oldestPendingMin >= 60 ? 'danger' : oldestPendingMin >= 20 ? 'warning' : 'neutral'}
          label={t('cashier.kpiOldestPending')}
          value={pendingCount === 0 ? '—' : humanAge(oldestPendingMin, t)}
          hint={pendingCount === 0 ? t('cashier.kpiOldestPendingNoBacklog') : t('cashier.kpiOldestPendingHint')}
        />
        <KpiTile
          icon={TrendingUp} tone="success"
          label={t('cashier.kpiReceivedToday')}
          value={fmt.format(receivedToday)}
          hint={t('cashier.kpiReceivedTodayHint', { count: approvedTodayList.length })}
        />
      </div>

      {/* ======================== Tabs (status) ======================== */}
      <div className="mb-4 flex flex-wrap items-center gap-2 rounded-xl border border-ink-200 bg-white p-2 shadow-card">
        {(['PENDING', 'APPROVED', 'REJECTED'] as PaymentStatus[]).map((s) => (
          <button
            key={s} type="button"
            onClick={() => { setTab(s); setPage(0); setStage(null); setQuery(''); }}
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              tab === s ? 'bg-brand-50 text-brand-700 ring-1 ring-inset ring-brand-200' : 'text-ink-600 hover:bg-ink-50',
            )}
          >
            {t(`cashier.statusLabel.${s}`)}
            {s === 'PENDING' && pendingCount > 0 && (
              <span className={cn(
                'ms-1 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full px-1.5 text-[10px] font-semibold',
                tab === s ? 'bg-brand-600 text-white' : 'bg-amber-100 text-amber-800',
              )}>
                {pendingCount}
              </span>
            )}
          </button>
        ))}
      </div>

      <Card>
        {/* ======================== Toolbar: search + stage filter ======================== */}
        <div className="space-y-3 border-b border-ink-100 p-3">
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="search" value={query} onChange={(e) => setQuery(e.target.value)}
              placeholder={t('cashier.searchPlaceholder')}
              className="h-9 w-full rounded-lg border border-ink-200 bg-white ps-9 pe-8 text-sm placeholder:text-ink-400 focus:border-brand-500"
            />
            {query && (
              <button type="button" onClick={() => setQuery('')} className="absolute end-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-ink-400 hover:bg-ink-100 hover:text-ink-700">
                <XIcon /> {/* see below */}
              </button>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="font-medium text-ink-600">{t('cashier.stage')}</span>
            <FilterPill active={stage === null} onClick={() => { setStage(null); setPage(0); }}>
              {t('common.all')}
            </FilterPill>
            {STAGE_KEYS.map((s) => (
              <FilterPill key={s} active={stage === s} onClick={() => { setStage(stage === s ? null : s); setPage(0); }}>
                {t(`cashier.stageLabel.${s}`)}
                {tab === 'PENDING' && stageCounts[s] > 0 && (
                  <span className="ms-1 opacity-70">({stageCounts[s]})</span>
                )}
              </FilterPill>
            ))}
          </div>
        </div>

        {/* ======================== Table ======================== */}
        {isLoading ? (
          <TableSkeleton />
        ) : sortedRows.length === 0 ? (
          <EmptyState
            icon={Wallet}
            title={query ? t('cashier.noMatches') : t('cashier.nothingInQueue')}
            description={
              query
                ? t('cashier.noMatchDesc', { query })
                : tab === 'PENDING'
                  ? t('cashier.noPendingDesc')
                  : t('cashier.noStatusDesc')
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-ink-100 bg-ink-50/60 text-[11px] font-semibold uppercase tracking-wide text-ink-500">
                <tr>
                  <Th>{t('cashier.colPayment')}</Th>
                  <Th>{t('cashier.colPatient')}</Th>
                  <Th>{t('cashier.colVisit')}</Th>
                  <Th>{t('cashier.colStage')}</Th>
                  <Th className="text-end">{t('cashier.colAmount')}</Th>
                  <Th>{tab === 'PENDING' ? t('cashier.colWaiting') : t('cashier.colDecided')}</Th>
                  <Th className="text-end">{t('common.actions')}</Th>
                </tr>
              </thead>
              <tbody className={cn('divide-y divide-ink-100', isFetching && 'opacity-70')}>
                {sortedRows.map((p) => (
                  <PaymentRow
                    key={p.id} p={p} tab={tab}
                    fmt={fmt} dt={dt}
                    onApprove={() => { setOpenPayment(p); setOpenMode('approve'); }}
                    onReject={() => { setOpenPayment(p); setOpenMode('reject'); }}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-ink-100 px-4 py-3 text-sm text-ink-600">
            <span className="text-xs">
              {t('cashier.pageRange', {
                from: data.number * data.size + 1,
                to: data.number * data.size + data.content.length,
                total: data.totalElements,
              })}
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

      {openPayment && openMode && (
        <DecisionDialog
          payment={openPayment} mode={openMode}
          onClose={() => { setOpenPayment(null); setOpenMode(null); }}
          onApprove={(method) => approveMut.mutate({ id: openPayment.id, method })}
          onReject={(reason) => rejectMut.mutate({ id: openPayment.id, reason })}
          loading={approveMut.isPending || rejectMut.isPending}
          formatCurrency={fmt}
        />
      )}
    </>
  );
}

/* ============================================================== Row ============================================================== */

function PaymentRow({
  p, tab, fmt, dt, onApprove, onReject,
}: {
  p: Payment;
  tab: PaymentStatus;
  fmt: Intl.NumberFormat;
  dt: Intl.DateTimeFormat;
  onApprove: () => void;
  onReject: () => void;
}) {
  const { t } = useTranslation();
  const ageMin = paymentAgeMinutes(p);
  const ageTone = paymentAgeTone(ageMin, p.status);

  return (
    <tr className={cn(
      'group transition-colors hover:bg-ink-50/60',
      ageTone === 'critical' && tab === 'PENDING' && 'bg-brand-50/40',
      ageTone === 'warn'     && tab === 'PENDING' && 'bg-amber-50/40',
    )}>
      <Td>
        <span className="font-mono text-xs font-semibold text-ink-900">{p.paymentDisplayId}</span>
        {p.vipBypass && (
          <div className="mt-0.5 inline-flex items-center gap-1 text-[10px] text-brand-700">
            <Crown size={10} /> {t('cashier.vipBypass')}
          </div>
        )}
      </Td>
      <Td>
        <div className="font-medium text-ink-900">{p.patientName}</div>
        <div className="font-mono text-[11px] text-ink-500">{p.patientMrn}</div>
      </Td>
      <Td>
        <span className="font-mono text-xs text-ink-700">{p.visitDisplayId}</span>
        <div className="text-[11px] text-ink-500">{p.visitType.replace(/_/g, ' ').toLowerCase()}</div>
      </Td>
      <Td>
        <Badge tone="info">{t(`cashier.stageLabel.${p.stage}`)}</Badge>
      </Td>
      <Td className="text-end">
        <span className="font-mono font-semibold text-ink-900">{fmt.format(p.totalDue)}</span>{' '}
        <span className="text-[11px] text-ink-500">{p.currency}</span>
        <div className="text-[11px] text-ink-400">{t('cashier.itemCount', { count: p.lines.length })}</div>
      </Td>
      <Td>
        {tab === 'PENDING' ? (
          <div className={cn(
            'inline-flex items-center gap-1 font-mono text-xs font-medium',
            ageTone === 'critical' ? 'text-brand-700' :
            ageTone === 'warn'     ? 'text-amber-700' :
                                     'text-ink-600',
          )}>
            {ageTone === 'critical' && <AlertTriangle size={11} />}
            {ageTone === 'warn'     && <Clock size={11} />}
            {humanAge(ageMin, t)}
          </div>
        ) : (
          <span className="text-xs text-ink-700">{p.decidedAt ? dt.format(new Date(p.decidedAt)) : '—'}</span>
        )}
      </Td>
      <Td className="text-end">
        {p.status === 'PENDING' ? (
          <div className="inline-flex items-center gap-1">
            <button
              type="button" onClick={onApprove}
              className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-700"
            >
              <CheckCircle2 size={12} /> {t('cashier.approve')}
            </button>
            <button
              type="button" onClick={onReject}
              className="inline-flex items-center gap-1 rounded-md border border-brand-200 bg-white px-2 py-1 text-xs font-medium text-brand-700 hover:bg-brand-50"
            >
              <XCircle size={12} /> {t('cashier.reject')}
            </button>
          </div>
        ) : p.status === 'APPROVED' ? (
          <span className="inline-flex items-center gap-1 text-xs text-emerald-700">
            <CheckCircle2 size={12} />
            {p.paymentMethod ? t(`cashier.methodLabel.${p.paymentMethod}`) : t('cashier.approvedLower')}
          </span>
        ) : (
          <span title={p.rejectionReason ?? ''} className="inline-flex items-center gap-1 text-xs text-brand-700">
            <XCircle size={12} /> {t('cashier.rejectedLower')}
          </span>
        )}
      </Td>
    </tr>
  );
}

/* ============================================================== Helpers ============================================================== */

function paymentAgeMinutes(p: Payment): number {
  const ref = p.decidedAt ? new Date(p.decidedAt) : new Date();
  return Math.max(0, (ref.getTime() - new Date(p.createdAt).getTime()) / 60000);
}

function paymentAgeTone(minutes: number, status: PaymentStatus): 'normal' | 'warn' | 'critical' {
  if (status !== 'PENDING') return 'normal';
  if (minutes >= 60) return 'critical';
  if (minutes >= 20) return 'warn';
  return 'normal';
}

function humanAge(minutes: number, t: TFunction): string {
  const m = Math.round(minutes);
  if (m < 1)  return t('cashier.justNow');
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  if (h < 24) return rem ? `${h}h ${rem}m` : `${h}h`;
  return `${Math.floor(h / 24)}d`;
}

/* ============================================================== Decision dialog ============================================================== */

function DecisionDialog({
  payment, mode, onClose, onApprove, onReject, loading, formatCurrency,
}: {
  payment: Payment;
  mode: 'approve' | 'reject';
  onClose: () => void;
  onApprove: (method: 'CASH' | 'CARD' | 'BANK_TRANSFER') => void;
  onReject: (reason: string) => void;
  loading: boolean;
  formatCurrency: Intl.NumberFormat;
}) {
  const { t } = useTranslation();
  const [method, setMethod] = useState<'CASH' | 'CARD' | 'BANK_TRANSFER'>('CASH');
  const [reason, setReason] = useState('');

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-lg overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">
              {mode === 'approve' ? t('cashier.approveTitle') : t('cashier.rejectTitle')}
            </h2>
            <p className="mt-0.5 font-mono text-xs text-ink-500">{payment.paymentDisplayId}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
            <X size={18} />
          </button>
        </div>

        <div className="space-y-3 border-b border-ink-100 bg-ink-50/40 p-5">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm font-semibold text-ink-900">{payment.patientName}</div>
              <div className="font-mono text-[11px] text-ink-500">{payment.patientMrn}</div>
            </div>
            <div className="text-end">
              <div className="font-mono text-2xl font-semibold text-ink-900">
                {formatCurrency.format(payment.totalDue)}
              </div>
              <div className="text-xs text-ink-500">{payment.currency} · {t(`cashier.stageLabel.${payment.stage}`)}</div>
            </div>
          </div>
          <ul className="divide-y divide-ink-100 rounded-lg border border-ink-100 bg-white">
            {payment.lines.map((l) => (
              <li key={l.serviceItemId} className="flex items-center justify-between px-3 py-2">
                <div>
                  <div className="font-mono text-[10px] text-ink-500">{l.code}</div>
                  <div className="text-sm text-ink-900">{l.name}</div>
                </div>
                <div className="text-end">
                  <div className="font-mono text-sm">{formatCurrency.format(l.lineTotal)}</div>
                  <div className="text-[10px] text-ink-500">×{l.quantity}</div>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="space-y-4 p-5">
          {mode === 'approve' ? (
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">{t('cashier.paymentMethod')}</label>
              <div className="grid grid-cols-3 gap-2">
                <MethodTile icon={Banknote}  label={t('cashier.methodLabel.CASH')}          active={method === 'CASH'}          onClick={() => setMethod('CASH')} />
                <MethodTile icon={CreditCard} label={t('cashier.methodLabel.CARD')}          active={method === 'CARD'}          onClick={() => setMethod('CARD')} />
                <MethodTile icon={Building2}  label={t('cashier.methodLabel.BANK_TRANSFER')} active={method === 'BANK_TRANSFER'} onClick={() => setMethod('BANK_TRANSFER')} />
              </div>
            </div>
          ) : (
            <Input
              label={t('cashier.rejectionReason')}
              placeholder={t('cashier.rejectionReasonPlaceholder')}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          {mode === 'approve' ? (
            <Button
              type="button" onClick={() => onApprove(method)} disabled={loading}
              className="bg-emerald-600 hover:bg-emerald-700"
            >
              <Receipt size={14} className="me-1.5" />
              {loading ? t('cashier.approving') : t('cashier.approveReceive')}
            </Button>
          ) : (
            <Button type="button" variant="danger" onClick={() => onReject(reason)} disabled={loading || !reason.trim()}>
              {loading ? t('cashier.rejecting') : t('cashier.reject')}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

/* ============================================================== UI bits ============================================================== */

function KpiTile({
  icon: Icon, tone, label, value, hint,
}: {
  icon: LucideIcon;
  tone: 'warning' | 'info' | 'success' | 'danger' | 'neutral';
  label: string;
  value: string;
  hint?: string;
}) {
  const cls = {
    warning: 'bg-amber-50 text-amber-700',
    info:    'bg-sky-50 text-sky-700',
    success: 'bg-emerald-50 text-emerald-700',
    danger:  'bg-brand-100 text-brand-800',
    neutral: 'bg-ink-100 text-ink-700',
  }[tone];
  return (
    <div className="flex items-start justify-between gap-3 rounded-xl border border-ink-200 bg-white p-4 shadow-card">
      <div className="min-w-0">
        <div className="text-[11px] font-medium uppercase tracking-wide text-ink-500">{label}</div>
        <div className="mt-1 truncate text-2xl font-semibold tabular-nums text-ink-900">{value}</div>
        {hint && <div className="mt-0.5 text-[11px] text-ink-500">{hint}</div>}
      </div>
      <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', cls)}>
        <Icon size={18} />
      </span>
    </div>
  );
}

function MethodTile({ icon: Icon, label, active, onClick }: { icon: LucideIcon; label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button" onClick={onClick}
      className={cn(
        'flex flex-col items-center justify-center gap-1.5 rounded-lg border p-3 transition-colors',
        active ? 'border-brand-300 bg-brand-50 text-brand-700' : 'border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
      )}
    >
      <Icon size={20} />
      <span className="text-xs font-medium">{label}</span>
    </button>
  );
}

function FilterPill({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button" onClick={onClick}
      className={cn(
        'inline-flex h-7 items-center rounded-full px-3 text-xs font-medium transition-colors',
        active ? 'bg-brand-600 text-white' : 'border border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
      )}
    >
      {children}
    </button>
  );
}

function XIcon() { return <X size={12} />; }

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
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-5 w-24" />
        </div>
      ))}
    </div>
  );
}
