import { useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import {
  ArrowLeft, ChevronDown, ChevronRight, User as UserIcon, FlaskConical, Scan, Activity,
  CheckCircle2, Clock, CalendarPlus, Receipt, ClipboardList, X as XIcon,
  FileText, Image as ImageIcon, Eye,
} from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import { cn } from '@/shared/ui/cn';
import { DocumentPreview } from '@/shared/ui/DocumentPreview';
import { getOrderResults, type StayDoc } from './forms/documentsApi';
import type { StayDepartment } from './forms/api';
import {
  type BedStayCaseView, type OrderView, type OrderTargetType,
  isUnderTreatment, statusToneClass,
} from './types';

export type BedStayActions = {
  /** Each action performs the API call AND invalidates the relevant queries, then resolves.
   *  It must THROW on error (so the shell can surface RESULTS_PENDING). */
  onOrder: (target: OrderTargetType, note: string) => Promise<unknown>;
  onSetDischargeNote: (note: string) => Promise<unknown>;
  onFinish: (override: boolean, reason?: string) => Promise<unknown>;
  onExtend?: (value: number, unit: 'HOURS' | 'DAYS') => Promise<unknown>;
  onReissue?: () => Promise<unknown>;
};

type BuiltinTabKey = 'overview' | 'LABORATORY' | 'RADIOLOGY' | 'ECO' | 'clinical' | 'billing' | 'timeline';
type TabKey = BuiltinTabKey | (string & {});

export type ExtraTab = { key: string; label: string; content: ReactNode; count?: number };

const TARGET_ICON: Record<OrderTargetType, typeof FlaskConical> = {
  LABORATORY: FlaskConical,
  RADIOLOGY: Scan,
  ECO: Activity,
};

function fmt(iso?: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function BedStayCasePage({
  backTo, backLabel, department, stayId, view, orders, ordersLoading, statusLabel, canExtend, clinical, actions, extraTabs,
}: {
  backTo: string;
  backLabel: string;
  department: StayDepartment;
  stayId: string;
  view: BedStayCaseView;
  orders: OrderView[];
  ordersLoading?: boolean;
  statusLabel: (code: string) => string;
  canExtend: boolean;
  clinical: ReactNode;
  actions: BedStayActions;
  extraTabs?: ExtraTab[];
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') as TabKey) || 'overview';
  const setTab = (k: TabKey) => setParams({ tab: k }, { replace: true });

  const [busy, setBusy] = useState(false);
  const [pendingMsg, setPendingMsg] = useState<string | null>(null);
  const [overrideReason, setOverrideReason] = useState('');

  const active = isUnderTreatment(view.status);
  const ordersByTarget = useMemo(() => {
    const m: Record<string, OrderView[]> = { LABORATORY: [], RADIOLOGY: [], ECO: [] };
    for (const o of orders) (m[o.visitType] ??= []).push(o);
    return m;
  }, [orders]);

  const run = async (fn: () => Promise<unknown>, okMsg?: string) => {
    setBusy(true);
    try {
      await fn();
      if (okMsg) toast.success(okMsg);
    } catch (e) {
      toast.error(extractApiError(e)?.message ?? t('caseView.error'));
    } finally {
      setBusy(false);
    }
  };

  const finish = async (override: boolean, reason?: string) => {
    setBusy(true);
    try {
      await actions.onFinish(override, reason);
      setPendingMsg(null);
      setOverrideReason('');
      toast.success(t('caseView.finished'));
    } catch (e) {
      const err = extractApiError(e);
      if (err?.code === 'RESULTS_PENDING') {
        setPendingMsg(err.message);
      } else {
        toast.error(err?.message ?? t('caseView.error'));
      }
    } finally {
      setBusy(false);
    }
  };

  const tabs: { key: TabKey; label: string; count?: number }[] = [
    { key: 'overview', label: t('caseView.tabs.overview') },
    { key: 'LABORATORY', label: t('caseView.tabs.laboratory'), count: ordersByTarget.LABORATORY.length },
    { key: 'RADIOLOGY', label: t('caseView.tabs.radiology'), count: ordersByTarget.RADIOLOGY.length },
    { key: 'ECO', label: t('caseView.tabs.eco'), count: ordersByTarget.ECO.length },
    { key: 'clinical', label: t('caseView.tabs.clinical') },
    ...(extraTabs ?? []).map((e) => ({ key: e.key as TabKey, label: e.label, count: e.count })),
    { key: 'billing', label: t('caseView.tabs.billing') },
    { key: 'timeline', label: t('caseView.tabs.timeline') },
  ];

  const activeExtra = (extraTabs ?? []).find((e) => e.key === tab);

  return (
    <div className="space-y-4 p-1">
      <button type="button" onClick={() => navigate(backTo)}
        className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-900">
        <ArrowLeft size={14} className="rtl:rotate-180" /> {backLabel}
      </button>

      {/* Header: patient + status */}
      <header className="flex flex-wrap items-center justify-between gap-3">
        <button type="button" onClick={() => navigate(`/patients/${view.patientId}`)}
          className="group flex items-center gap-3 text-start" data-testid="case-patient">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-50 text-brand-700"><UserIcon size={18} /></span>
          <span>
            <span className="block font-semibold text-ink-900">{view.patientName}</span>
            <span className="block font-mono text-[11px] text-ink-500">{view.patientMrn} · {view.visitDisplayId}</span>
          </span>
          <ChevronRight size={16} className="text-ink-300 group-hover:text-brand-600 rtl:rotate-180" />
        </button>
        <span className={cn('rounded-full px-2.5 py-1 text-xs font-medium', statusToneClass(view.status))} data-testid="case-status">
          {statusLabel(view.status)}
        </span>
      </header>

      {/* Banner: key facts */}
      <div className="flex flex-wrap gap-x-6 gap-y-1.5 rounded-xl border border-ink-100 bg-white px-4 py-3 text-xs">
        <Fact label={t('caseView.bed')}>{view.bedCode}</Fact>
        {view.serviceName && <Fact label={t('caseView.service')}>{view.serviceName}</Fact>}
        <Fact label={t('caseView.stay')}>{view.stayValue} {t(view.stayUnit === 'DAYS' ? 'caseView.days' : 'caseView.hours')}</Fact>
        <Fact label={t('caseView.admitted')}>{fmt(view.admittedAt)}</Fact>
        <Fact label={t('caseView.expires')}>{fmt(view.stayExpiresAt)}</Fact>
        {view.treatmentFinishedAt && <Fact label={t('caseView.treatmentFinished')}>{fmt(view.treatmentFinishedAt)}</Fact>}
      </div>

      {/* Quick actions */}
      {(active || view.status === 'AWAITING_DISCHARGE_PAYMENT') && (
        <div className="flex flex-wrap gap-2">
          {active && (
            <button type="button" disabled={busy} onClick={() => finish(false)}
              className="inline-flex items-center gap-1.5 rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
              data-testid="detail-finish">
              <CheckCircle2 size={14} /> {t('caseView.finishTreatment')}
            </button>
          )}
          {active && canExtend && actions.onExtend && (
            <ExtendControl busy={busy} onExtend={(v, u) => run(() => actions.onExtend!(v, u), t('caseView.extended'))} t={t} />
          )}
          {view.status === 'AWAITING_DISCHARGE_PAYMENT' && actions.onReissue && (
            <button type="button" disabled={busy} onClick={() => run(() => actions.onReissue!(), t('caseView.reissued'))}
              className="inline-flex items-center gap-1.5 rounded-md border border-ink-200 px-3 py-2 text-sm font-medium hover:bg-ink-50 disabled:opacity-50"
              data-testid="detail-reissue">
              <Receipt size={14} /> {t('caseView.reissueDischarge')}
            </button>
          )}
        </div>
      )}

      {/* Tabs */}
      <div className="flex flex-wrap gap-1 border-b border-ink-100">
        {tabs.map((tb) => (
          <button key={tb.key} type="button" onClick={() => setTab(tb.key)}
            className={cn('inline-flex items-center gap-1.5 border-b-2 px-3.5 py-2.5 text-sm font-medium transition-colors',
              tab === tb.key ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-600 hover:text-ink-900')}
            data-testid={`case-tab-${tb.key}`}>
            {tb.label}
            {tb.count != null && tb.count > 0 && (
              <span className="rounded-full bg-ink-100 px-1.5 text-[10px] font-semibold text-ink-600">{tb.count}</span>
            )}
          </button>
        ))}
      </div>

      {activeExtra ? activeExtra.content : (
        <>
          {tab === 'overview' && (
            <OverviewTab view={view} statusLabel={statusLabel} busy={busy}
              onSaveNote={(n) => run(() => actions.onSetDischargeNote(n), t('caseView.noteSaved'))} t={t} />
          )}
          {(tab === 'LABORATORY' || tab === 'RADIOLOGY' || tab === 'ECO') && (
            <OrdersTab target={tab as OrderTargetType} department={department} stayId={stayId}
              orders={ordersByTarget[tab]} loading={ordersLoading} canOrder={active} busy={busy}
              onOrder={(note) => run(() => actions.onOrder(tab as OrderTargetType, note), t('caseView.ordered'))} t={t} />
          )}
          {tab === 'clinical' && <div>{clinical}</div>}
          {tab === 'billing' && <BillingTab view={view} t={t} />}
          {tab === 'timeline' && <TimelineTab view={view} orders={orders} statusLabel={statusLabel} t={t} />}
        </>
      )}

      {pendingMsg !== null && (
        <ResultsPendingDialog
          message={pendingMsg} reason={overrideReason} setReason={setOverrideReason} busy={busy}
          onCancel={() => { setPendingMsg(null); setOverrideReason(''); }}
          onConfirm={() => finish(true, overrideReason)} t={t}
        />
      )}
    </div>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-ink-500">{label}</dt>
      <dd className="font-medium text-ink-900">{children}</dd>
    </div>
  );
}

function ExtendControl({ busy, onExtend, t }: { busy: boolean; onExtend: (v: number, u: 'HOURS' | 'DAYS') => void; t: (k: string) => string }) {
  const [value, setValue] = useState(1);
  const [unit, setUnit] = useState<'HOURS' | 'DAYS'>('DAYS');
  return (
    <div className="inline-flex items-center gap-1.5 rounded-md border border-ink-200 px-2 py-1">
      <CalendarPlus size={14} className="text-ink-400" />
      <input type="number" min={1} value={value} onChange={(e) => setValue(Math.max(1, Number(e.target.value) || 1))}
        className="w-14 rounded border border-ink-200 px-1.5 py-1 text-sm" data-testid="detail-extend-value" />
      <select value={unit} onChange={(e) => setUnit(e.target.value as 'HOURS' | 'DAYS')}
        className="rounded border border-ink-200 px-1.5 py-1 text-sm" data-testid="detail-extend-unit">
        <option value="DAYS">{t('caseView.days')}</option>
        <option value="HOURS">{t('caseView.hours')}</option>
      </select>
      <button type="button" disabled={busy} onClick={() => onExtend(value, unit)}
        className="rounded-md px-2 py-1 text-sm font-medium text-brand-700 hover:bg-brand-50 disabled:opacity-50"
        data-testid="detail-extend">
        {t('caseView.extend')}
      </button>
    </div>
  );
}

function OverviewTab({ view, statusLabel, busy, onSaveNote, t }: {
  view: BedStayCaseView; statusLabel: (c: string) => string; busy: boolean;
  onSaveNote: (note: string) => void; t: (k: string) => string;
}) {
  const [note, setNote] = useState(view.dischargeNote ?? '');
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-ink-100 bg-white p-4 text-sm">
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <Fact label={t('caseView.status')}>{statusLabel(view.status)}</Fact>
          <Fact label={t('caseView.bed')}>{view.bedCode}</Fact>
          <Fact label={t('caseView.stay')}>{view.stayValue} {t(view.stayUnit === 'DAYS' ? 'caseView.days' : 'caseView.hours')}</Fact>
          <Fact label={t('caseView.admitted')}>{fmt(view.admittedAt)}</Fact>
          <Fact label={t('caseView.expires')}>{fmt(view.stayExpiresAt)}</Fact>
          {view.serviceName && <Fact label={t('caseView.service')}>{view.serviceName}</Fact>}
        </dl>
      </div>
      <div className="rounded-xl border border-ink-100 bg-white p-4">
        <label className="mb-1 block text-sm font-medium text-ink-700">{t('caseView.dischargeNote')}</label>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3}
          placeholder={t('caseView.dischargeNotePlaceholder')}
          className="w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm" data-testid="discharge-note-input" />
        <button type="button" disabled={busy} onClick={() => onSaveNote(note)}
          className="mt-2 rounded-md border border-ink-200 px-3 py-1.5 text-sm font-medium hover:bg-ink-50 disabled:opacity-50"
          data-testid="discharge-note-save">
          {t('caseView.saveNote')}
        </button>
      </div>
    </div>
  );
}

function OrdersTab({ target, department, stayId, orders, loading, canOrder, busy, onOrder, t }: {
  target: OrderTargetType; department: StayDepartment; stayId: string;
  orders: OrderView[]; loading?: boolean; canOrder: boolean; busy: boolean;
  onOrder: (note: string) => void; t: (k: string) => string;
}) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const Icon = TARGET_ICON[target];
  return (
    <div className="space-y-3">
      {canOrder && (
        <button type="button" onClick={() => { setNote(''); setOpen(true); }}
          className="inline-flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"
          data-testid={`order-${target}`}>
          <Icon size={14} /> {t('caseView.sendTo')}
        </button>
      )}

      <div className="rounded-xl border border-ink-100 bg-white" data-testid="order-list">
        {loading ? (
          <p className="p-4 text-xs text-ink-400">{t('common.loading')}</p>
        ) : orders.length === 0 ? (
          <p className="p-6 text-center text-sm text-ink-400">{t('caseView.noOrders')}</p>
        ) : (
          <ul className="divide-y divide-ink-100">
            {orders.map((o) => (
              <li key={o.visitId} className="p-4" data-testid={`order-row-${o.visitType}`}>
                <div className="flex flex-wrap items-center gap-2">
                  <Icon size={14} className="text-ink-400" />
                  <span className="font-mono text-xs text-ink-700">{o.visitDisplayId}</span>
                  <span title={o.status}
                    className={cn('rounded-full px-2 py-0.5 text-[11px] font-medium',
                      o.resultsAt ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>
                    {o.resultsAt ? t('caseView.orderStatus.resultsReady') : t('caseView.orderStatus.awaitingResults')}
                  </span>
                  <span className="ms-auto text-[11px] text-ink-400">{fmt(o.startedAt)}</span>
                  <button type="button" onClick={() => setExpanded((m) => ({ ...m, [o.visitId]: !m[o.visitId] }))}
                    className="rounded p-1 text-ink-400 hover:bg-ink-50 hover:text-ink-700"
                    data-testid={`order-expand-${o.visitId}`}
                    aria-expanded={!!expanded[o.visitId]}>
                    <ChevronDown size={15} className={cn('transition-transform', expanded[o.visitId] && 'rotate-180')} />
                  </button>
                </div>
                {o.note && (
                  <p className="mt-1.5 text-xs text-ink-600"><span className="font-medium text-ink-500">{t('caseView.note')}:</span> {o.note}</p>
                )}
                {o.resultsSummary && (
                  <p className="mt-1.5 rounded-md bg-emerald-50 px-2 py-1.5 text-xs text-emerald-800">
                    <span className="font-medium">{t('caseView.result')}:</span> {o.resultsSummary}
                    {o.resultsAt && <span className="ms-2 text-[11px] text-emerald-600">{fmt(o.resultsAt)}</span>}
                  </p>
                )}
                <OrderResultsPanel department={department} stayId={stayId} visitId={o.visitId}
                  expanded={!!expanded[o.visitId]} t={t} />
              </li>
            ))}
          </ul>
        )}
      </div>

      {open && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4">
          <div className="w-full max-w-md rounded-xl bg-white shadow-elevated" data-testid="order-dialog">
            <div className="flex items-center justify-between border-b border-ink-100 px-5 py-3">
              <h3 className="text-sm font-semibold text-ink-900">{t('caseView.sendTo')} · {t(`caseView.tabs.${target.toLowerCase()}`)}</h3>
              <button type="button" onClick={() => setOpen(false)} className="rounded p-1 text-ink-500 hover:bg-ink-100"><XIcon size={16} /></button>
            </div>
            <div className="p-5">
              <label className="mb-1 block text-sm font-medium text-ink-700">{t('caseView.orderNote')}</label>
              <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3} autoFocus
                placeholder={t('caseView.orderNotePlaceholder')}
                className="w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm" data-testid="order-note" />
            </div>
            <div className="flex justify-end gap-2 border-t border-ink-100 px-5 py-3">
              <button type="button" onClick={() => setOpen(false)}
                className="rounded-md border border-ink-200 px-3 py-1.5 text-sm font-medium hover:bg-ink-50">{t('common.cancel')}</button>
              <button type="button" disabled={busy} onClick={() => { onOrder(note.trim()); setOpen(false); }}
                className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
                data-testid="order-send">
                {t('caseView.send')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function OrderResultsPanel({ department, stayId, visitId, expanded, t }: {
  department: StayDepartment; stayId: string; visitId: string; expanded: boolean; t: (k: string) => string;
}) {
  const [preview, setPreview] = useState<StayDoc | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['order-results', department, stayId, visitId],
    queryFn: () => getOrderResults(department, stayId, visitId),
    enabled: expanded,
  });

  if (!expanded) return null;

  const services = data?.services ?? [];
  const documents = data?.documents ?? [];

  return (
    <div className="mt-2.5 space-y-3 rounded-md border border-ink-100 bg-ink-50/40 p-3" data-testid={`order-results-${visitId}`}>
      {isLoading ? (
        <p className="text-xs text-ink-400">{t('common.loading')}</p>
      ) : services.length === 0 && documents.length === 0 ? (
        <p className="text-xs text-ink-400">{t('caseView.orderStatus.nothingYet')}</p>
      ) : (
        <>
          {services.length > 0 && (
            <div>
              <h4 className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-ink-500">{t('caseView.orderResults.findings')}</h4>
              <ul className="space-y-1.5">
                {services.map((s, i) => (
                  <li key={i} className="text-xs text-ink-700">
                    <span className="font-semibold text-ink-900">{s.serviceName}</span>
                    <span className="ms-2">{s.findings?.trim() ? s.findings : '—'}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {documents.length > 0 && (
            <div>
              <h4 className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-ink-500">{t('caseView.orderResults.documents')}</h4>
              <ul className="divide-y divide-ink-100">
                {documents.map((d) => (
                  <li key={d.id} className="flex items-center gap-2.5 py-1.5">
                    {d.contentType.startsWith('image/')
                      ? <ImageIcon size={15} className="shrink-0 text-ink-400" />
                      : <FileText size={15} className="shrink-0 text-ink-400" />}
                    <span className="min-w-0 flex-1 truncate text-xs font-medium text-ink-900">{d.fileName}</span>
                    <span className="text-[11px] text-ink-400">{fmtSize(d.sizeBytes)}</span>
                    <button type="button" onClick={() => setPreview(d)} data-testid={`order-doc-view-${d.fileName}`}
                      className="inline-flex items-center gap-1 rounded-md border border-ink-200 bg-white px-2 py-1 text-xs hover:bg-ink-50">
                      <Eye size={12} /> {t('caseView.documents.view')}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
      {preview && (
        <DocumentPreview fileUrl={preview.fileUrl} fileName={preview.fileName}
          contentType={preview.contentType} onClose={() => setPreview(null)} />
      )}
    </div>
  );
}

function BillingTab({ view, t }: { view: BedStayCaseView; t: (k: string) => string }) {
  return (
    <div className="rounded-xl border border-ink-100 bg-white p-4 text-sm">
      <ul className="space-y-3">
        <li className="flex items-center justify-between gap-3">
          <span className="flex items-center gap-2 text-ink-700"><Receipt size={14} className="text-ink-400" /> {t('caseView.initialPayment')}</span>
          <span className={cn('rounded-full px-2 py-0.5 text-[11px] font-medium', view.initialPaymentId ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-500')}>
            {view.initialPaymentId ? t('caseView.paid') : t('caseView.notYet')}
          </span>
        </li>
        <li className="flex items-center justify-between gap-3">
          <span className="flex items-center gap-2 text-ink-700"><Receipt size={14} className="text-ink-400" /> {t('caseView.dischargePayment')}</span>
          <span className={cn('rounded-full px-2 py-0.5 text-[11px] font-medium', view.finalPaymentId ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-500')}>
            {view.finalPaymentId ? t('caseView.issued') : t('caseView.notYet')}
          </span>
        </li>
      </ul>
    </div>
  );
}

type TimelineEvent = { at: string; label: string; detail?: string | null; tone: 'brand' | 'emerald' | 'ink' };

function TimelineTab({ view, orders, statusLabel, t }: {
  view: BedStayCaseView; orders: OrderView[]; statusLabel: (c: string) => string; t: (k: string) => string;
}) {
  const events: TimelineEvent[] = [];
  events.push({ at: view.admittedAt, label: t('caseView.timeline.admitted'), tone: 'brand' });
  for (const o of orders) {
    events.push({ at: o.startedAt, label: `${t('caseView.timeline.orderSent')} → ${o.visitType}`, detail: o.note, tone: 'ink' });
    if (o.resultsAt) {
      events.push({ at: o.resultsAt, label: `${t('caseView.timeline.resultsBack')} ← ${o.visitType}`, detail: o.resultsSummary, tone: 'emerald' });
    }
  }
  if (view.treatmentFinishedAt) events.push({ at: view.treatmentFinishedAt, label: t('caseView.timeline.treatmentFinished'), tone: 'brand' });
  if (view.closedAt) events.push({ at: view.closedAt, label: t('caseView.timeline.discharged'), tone: 'emerald' });
  events.sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());

  const dot = { brand: 'bg-brand-500', emerald: 'bg-emerald-500', ink: 'bg-ink-300' };

  return (
    <div className="rounded-xl border border-ink-100 bg-white p-4">
      <ol className="relative ms-2 space-y-4 border-s border-ink-100 ps-5" data-testid="case-timeline">
        {events.map((e, i) => (
          <li key={i} className="relative">
            <span className={cn('absolute -start-[1.42rem] top-1 h-2.5 w-2.5 rounded-full ring-2 ring-white', dot[e.tone])} />
            <div className="flex flex-wrap items-baseline gap-x-2">
              <span className="text-sm font-medium text-ink-900">{e.label}</span>
              <span className="text-[11px] text-ink-400">{fmt(e.at)}</span>
            </div>
            {e.detail && <p className="mt-0.5 text-xs text-ink-600">{e.detail}</p>}
          </li>
        ))}
        <li className="relative">
          <span className="absolute -start-[1.42rem] top-1 h-2.5 w-2.5 rounded-full bg-white ring-2 ring-brand-400" />
          <span className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-700">
            <ClipboardList size={13} className="text-ink-400" /> {t('caseView.timeline.now')}: {statusLabel(view.status)}
          </span>
        </li>
      </ol>
    </div>
  );
}

function ResultsPendingDialog({ message, reason, setReason, busy, onCancel, onConfirm, t }: {
  message: string; reason: string; setReason: (s: string) => void; busy: boolean;
  onCancel: () => void; onConfirm: () => void; t: (k: string) => string;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl" data-testid="results-pending-dialog" role="alertdialog" aria-modal="true">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold text-ink-900"><Clock size={15} className="text-amber-600" /> {t('caseView.resultsPending.title')}</h3>
        <p className="mt-2 text-xs text-ink-600">{t('caseView.resultsPending.body')}</p>
        <p className="mt-1 text-xs text-amber-700">{message}</p>
        <label className="mt-3 block text-[11px] font-medium text-ink-600">{t('caseView.resultsPending.reason')}</label>
        <input type="text" value={reason} onChange={(e) => setReason(e.target.value)}
          className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm" data-testid="override-reason" />
        <div className="mt-3 flex justify-end gap-2">
          <button type="button" onClick={onCancel}
            className="rounded-md border border-ink-200 px-3 py-1.5 text-xs font-medium hover:bg-ink-50" data-testid="results-pending-cancel">
            {t('common.cancel')}
          </button>
          <button type="button" disabled={busy || reason.trim().length === 0} onClick={onConfirm}
            className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            data-testid="finish-override">
            {t('caseView.resultsPending.finishAnyway')}
          </button>
        </div>
      </div>
    </div>
  );
}
