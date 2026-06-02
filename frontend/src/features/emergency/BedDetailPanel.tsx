import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { BedDouble, CheckCircle2, ChevronRight, Clock, FlaskConical, Siren, X as XIcon } from 'lucide-react';
import type { Case, Bed, StayUnit } from './api';
import { finishTreatment, listOrders, orderWorkup, setDischargeNote } from './api';
import { extractApiError } from '@/shared/api/client';

function fmt(iso?: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function isExpiringSoon(iso?: string | null): boolean {
  if (!iso) return false;
  return new Date(iso).getTime() - Date.now() < 2 * 60 * 60 * 1000; // within 2h
}

function statusBadgeClass(status?: string): string {
  switch (status) {
    case 'UNDER_TREATMENT':
      return 'bg-brand-50 text-brand-700';
    case 'TREATMENT_FINISHED':
      return 'bg-indigo-50 text-indigo-700';
    case 'AWAITING_INITIAL_PAYMENT':
    case 'AWAITING_DISCHARGE_PAYMENT':
      return 'bg-amber-50 text-amber-700';
    case 'CANCELLED':
      return 'bg-red-50 text-red-700';
    default:
      return 'bg-ink-100 text-ink-600';
  }
}

/**
 * Right-side slide-in drawer showing a bed's occupant + case details, with the patient
 * linking to their full history and the contextual stay actions. Reads already-loaded data;
 * RTL-aware; Esc / scrim-click to close. No "Open case" button (no case page in Emergency).
 */
export function BedDetailPanel({
  bed, emergencyCase, onClose, onExtend, onReissue, pending, t, dir,
}: {
  bed: Bed;
  emergencyCase: Case | null;
  onClose: () => void;
  onExtend: (a: { id: string; value: number; unit: StayUnit }) => void;
  onFinish: (id: string) => void;
  onReissue: (id: string) => void;
  pending: boolean;
  t: (k: string) => string;
  dir: 'ltr' | 'rtl';
}) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [shown, setShown] = useState(false);
  const [extendValue, setExtendValue] = useState(1);
  const [extendUnit, setExtendUnit] = useState<StayUnit>('DAYS');
  const [note, setNote] = useState('');
  const [pendingMsg, setPendingMsg] = useState<string | null>(null);
  const [overrideReason, setOverrideReason] = useState('');

  const caseId = emergencyCase?.id ?? null;
  const isUnderTreatment = emergencyCase?.status === 'UNDER_TREATMENT';

  const invalidate = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['emerg-beds'] }),
      qc.invalidateQueries({ queryKey: ['emerg-cases'] }),
      qc.invalidateQueries({ queryKey: ['emerg-incoming'] }),
      qc.invalidateQueries({ queryKey: ['payments'] }),
      qc.invalidateQueries({ queryKey: ['visits'] }),
    ]);
  };

  const ordersQuery = useQuery({
    queryKey: ['emerg-orders', caseId],
    queryFn: () => listOrders(caseId as string),
    enabled: !!caseId && isUnderTreatment,
  });

  const orderMut = useMutation({
    mutationFn: (target: 'LABORATORY' | 'RADIOLOGY' | 'ECO') => orderWorkup(caseId as string, target),
    onSuccess: async () => {
      toast.success(t('emergency.orders.ordered'));
      await qc.invalidateQueries({ queryKey: ['emerg-orders', caseId] });
    },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('emergency.toast.error')),
  });

  const noteMut = useMutation({
    mutationFn: () => setDischargeNote(caseId as string, note),
    onSuccess: () => toast.success(t('emergency.dischargeNote.saved')),
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('emergency.toast.error')),
  });

  const finishMut = useMutation({
    mutationFn: ({ override, reason }: { override: boolean; reason?: string }) =>
      finishTreatment(caseId as string, override, reason),
    onSuccess: async () => {
      setPendingMsg(null);
      setOverrideReason('');
      toast.success(t('emergency.toast.finished'));
      await invalidate();
    },
    onError: (e) => {
      const apiErr = extractApiError(e);
      if (apiErr?.code === 'RESULTS_PENDING') {
        setPendingMsg(apiErr.message);
        return;
      }
      toast.error(apiErr?.message ?? t('emergency.toast.error'));
    },
  });

  const busy = pending || orderMut.isPending || noteMut.isPending || finishMut.isPending;

  useEffect(() => { setNote(emergencyCase?.dischargeNote ?? ''); }, [emergencyCase?.id, emergencyCase?.dischargeNote]);
  useEffect(() => { setShown(true); }, []);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const onLeft = dir === 'rtl';
  const sideClass = onLeft ? 'left-0' : 'right-0';
  const hiddenClass = onLeft ? '-translate-x-full' : 'translate-x-full';
  const status = emergencyCase?.status;
  const expiring = emergencyCase ? isExpiringSoon(emergencyCase.stayExpiresAt) : false;

  const goToHistory = () => { if (emergencyCase) navigate(`/patients/${emergencyCase.patientId}`); };

  return (
    <div className="fixed inset-0 z-50" role="dialog" aria-modal="true" aria-label={t('emergency.detail.title')}>
      <div
        className={'absolute inset-0 bg-black/30 transition-opacity duration-200 ' + (shown ? 'opacity-100' : 'opacity-0')}
        onClick={onClose}
        data-testid="bed-detail-scrim"
      />
      <div
        className={
          `absolute top-0 ${sideClass} flex h-full w-full max-w-sm flex-col bg-white shadow-xl ` +
          `transition-transform duration-200 ` + (shown ? 'translate-x-0' : hiddenClass)
        }
        data-testid="bed-detail-panel"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-3">
          <span className="flex items-center gap-1.5 font-mono text-sm font-semibold text-ink-900">
            <BedDouble size={15} /> {bed.code}
          </span>
          <div className="flex items-center gap-2">
            <span className={'rounded-full px-2 py-0.5 text-[11px] font-medium ' + statusBadgeClass(status)}>
              {status ? t(`emergency.caseStatus.${status}`) : t(`emergency.bedStatus.${bed.status}`)}
            </span>
            <button
              type="button" onClick={onClose}
              className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100"
              aria-label={t('common.close')} data-testid="bed-detail-close"
            >
              <XIcon size={16} />
            </button>
          </div>
        </div>

        {!emergencyCase ? (
          <div className="p-5 text-sm text-ink-500">{t('emergency.detail.noOccupant')}</div>
        ) : (
          <>
            <div className="flex-1 overflow-y-auto px-5 py-4">
              {/* Patient — links to full history */}
              <button
                type="button" onClick={goToHistory}
                className="group flex w-full items-center gap-3 rounded-lg border border-ink-100 p-3 text-start transition hover:border-brand-200 hover:bg-brand-50/40 focus:outline-none focus:ring-2 focus:ring-brand-200"
                data-testid="bed-detail-patient"
              >
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
                  <Siren size={18} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold text-ink-900">{emergencyCase.patientName}</span>
                  <span className="block truncate font-mono text-[11px] text-ink-500">
                    {emergencyCase.patientMrn} · {emergencyCase.visitDisplayId}
                  </span>
                </span>
                <ChevronRight size={16} className="shrink-0 text-ink-400 group-hover:text-brand-600 rtl:rotate-180" />
              </button>
              <button
                type="button" onClick={goToHistory}
                className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:underline"
                data-testid="bed-detail-history"
              >
                {t('emergency.detail.viewHistory')}
                <ChevronRight size={13} className="rtl:rotate-180" />
              </button>

              {/* Case details */}
              <dl className="mt-5 space-y-2.5 text-xs">
                <Row label={t('emergency.detail.status')}>{t(`emergency.caseStatus.${emergencyCase.status}`)}</Row>
                <Row label={t('emergency.service')}>{emergencyCase.serviceName}</Row>
                <Row label={t('emergency.bed')}>{emergencyCase.bedCode}{bed.room ? ` · ${bed.room}` : ''}</Row>
                <Row label={t('emergency.stayValue')}>
                  {emergencyCase.stayValue} {t(`emergency.${emergencyCase.stayUnit === 'DAYS' ? 'days' : 'hours'}`)}
                </Row>
                <Row label={t('emergency.detail.admittedAt')}>{fmt(emergencyCase.admittedAt)}</Row>
                <Row label={t('emergency.detail.expiresAt')}>
                  <span className={expiring ? 'inline-flex items-center gap-1 font-medium text-red-700' : ''}>
                    {expiring && <Clock size={11} />}{fmt(emergencyCase.stayExpiresAt)}
                  </span>
                </Row>
                {emergencyCase.treatmentFinishedAt && (
                  <Row label={t('emergency.detail.treatmentFinishedAt')}>{fmt(emergencyCase.treatmentFinishedAt)}</Row>
                )}
              </dl>
            </div>

            {/* Contextual actions */}
            <div className="border-t border-ink-100 px-5 py-3">
              {status === 'UNDER_TREATMENT' && (
                <div className="space-y-2.5">
                  {/* Orders — Lab / Radiology / ECO */}
                  <div>
                    <label className="block text-[11px] font-medium text-ink-600">{t('emergency.orders.title')}</label>
                    <div className="mt-1 flex gap-1.5">
                      <button
                        type="button" disabled={busy}
                        onClick={() => orderMut.mutate('LABORATORY')}
                        className="flex-1 rounded-md border border-ink-200 px-2 py-1.5 text-[11px] font-medium hover:bg-ink-50 disabled:opacity-50"
                        data-testid="order-LABORATORY"
                      >
                        {t('emergency.orders.sendToLab')}
                      </button>
                      <button
                        type="button" disabled={busy}
                        onClick={() => orderMut.mutate('RADIOLOGY')}
                        className="flex-1 rounded-md border border-ink-200 px-2 py-1.5 text-[11px] font-medium hover:bg-ink-50 disabled:opacity-50"
                        data-testid="order-RADIOLOGY"
                      >
                        {t('emergency.orders.sendToRadiology')}
                      </button>
                      <button
                        type="button" disabled={busy}
                        onClick={() => orderMut.mutate('ECO')}
                        className="flex-1 rounded-md border border-ink-200 px-2 py-1.5 text-[11px] font-medium hover:bg-ink-50 disabled:opacity-50"
                        data-testid="order-ECO"
                      >
                        {t('emergency.orders.sendToEco')}
                      </button>
                    </div>
                    <ul className="mt-1.5 space-y-1" data-testid="order-list">
                      {(ordersQuery.data ?? []).length === 0 ? (
                        <li className="text-[11px] text-ink-400">{t('emergency.orders.none')}</li>
                      ) : (
                        (ordersQuery.data ?? []).map((o) => (
                          <li
                            key={o.visitId}
                            className="flex items-center gap-1.5 rounded-md bg-ink-50 px-2 py-1 text-[11px] text-ink-700"
                            data-testid={`order-row-${o.visitType}`}
                          >
                            <FlaskConical size={11} className="shrink-0 text-ink-400" />
                            <span className="font-mono">{o.visitDisplayId}</span>
                            <span className="text-ink-400">·</span>
                            <span>{o.visitType}</span>
                            <span className="text-ink-400">·</span>
                            <span className="font-medium">{o.status}</span>
                          </li>
                        ))
                      )}
                    </ul>
                  </div>

                  {/* Discharge note */}
                  <div>
                    <label className="block text-[11px] font-medium text-ink-600">{t('emergency.dischargeNote.label')}</label>
                    <textarea
                      value={note}
                      onChange={(e) => setNote(e.target.value)}
                      placeholder={t('emergency.dischargeNote.placeholder')}
                      rows={2}
                      className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-xs"
                      data-testid="discharge-note-input"
                    />
                    <button
                      type="button" disabled={busy}
                      onClick={() => noteMut.mutate()}
                      className="mt-1 w-full rounded-md border border-ink-200 px-3 py-1.5 text-xs font-medium hover:bg-ink-50 disabled:opacity-50"
                      data-testid="discharge-note-save"
                    >
                      {t('emergency.dischargeNote.save')}
                    </button>
                  </div>

                  <div>
                    <label className="block text-[11px] font-medium text-ink-600">{t('emergency.detail.extendBy')}</label>
                    <div className="mt-1 flex gap-1.5">
                      <input
                        type="number" min={1} value={extendValue}
                        onChange={(e) => setExtendValue(Math.max(1, Number(e.target.value) || 1))}
                        className="w-16 rounded-md border border-ink-200 px-2 py-1.5 text-sm"
                        data-testid="detail-extend-value"
                      />
                      <select
                        value={extendUnit} onChange={(e) => setExtendUnit(e.target.value as StayUnit)}
                        className="rounded-md border border-ink-200 px-2 py-1.5 text-sm"
                        data-testid="detail-extend-unit"
                      >
                        <option value="DAYS">{t('emergency.days')}</option>
                        <option value="HOURS">{t('emergency.hours')}</option>
                      </select>
                      <button
                        type="button" disabled={busy}
                        onClick={() => onExtend({ id: emergencyCase.id, value: extendValue, unit: extendUnit })}
                        className="flex-1 rounded-md border border-ink-200 px-3 py-1.5 text-xs font-medium hover:bg-ink-50 disabled:opacity-50"
                        data-testid="detail-extend"
                      >
                        {t('emergency.detail.extend')}
                      </button>
                    </div>
                  </div>
                  <button
                    type="button" disabled={busy} onClick={() => finishMut.mutate({ override: false })}
                    className="inline-flex w-full items-center justify-center gap-1.5 rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                    data-testid="detail-finish"
                  >
                    <CheckCircle2 size={14} /> {t('emergency.finishTreatment')}
                  </button>
                </div>
              )}
              {status === 'AWAITING_DISCHARGE_PAYMENT' && (
                <div className="space-y-2">
                  <p className="text-[11px] text-amber-700">{t('emergency.awaitingDischarge')}</p>
                  <button
                    type="button" disabled={pending} onClick={() => onReissue(emergencyCase.id)}
                    className="w-full rounded-md border border-ink-200 px-3 py-2 text-sm font-medium hover:bg-ink-50 disabled:opacity-50"
                    data-testid="detail-reissue"
                  >
                    {t('emergency.reissueDischarge')}
                  </button>
                </div>
              )}
              {status === 'AWAITING_INITIAL_PAYMENT' && (
                <p className="text-[11px] text-amber-700">{t('emergency.detail.awaitingAdmission')}</p>
              )}
              {status === 'TREATMENT_FINISHED' && (
                <p className="text-[11px] text-ink-500">{t('emergency.detail.treatmentFinishedNote')}</p>
              )}
            </div>
          </>
        )}
      </div>

      {pendingMsg !== null && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-black/30 p-4">
          <div
            className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl"
            data-testid="results-pending-dialog"
            role="alertdialog"
            aria-modal="true"
          >
            <h3 className="text-sm font-semibold text-ink-900">{t('emergency.resultsPending.title')}</h3>
            <p className="mt-2 text-xs text-ink-600">{t('emergency.resultsPending.body')}</p>
            <p className="mt-1 text-xs text-amber-700">{pendingMsg}</p>
            <label className="mt-3 block text-[11px] font-medium text-ink-600">{t('emergency.resultsPending.reason')}</label>
            <input
              type="text"
              value={overrideReason}
              onChange={(e) => setOverrideReason(e.target.value)}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
              data-testid="override-reason"
            />
            <div className="mt-3 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => { setPendingMsg(null); setOverrideReason(''); }}
                className="rounded-md border border-ink-200 px-3 py-1.5 text-xs font-medium hover:bg-ink-50"
                data-testid="results-pending-cancel"
              >
                {t('common.close')}
              </button>
              <button
                type="button"
                disabled={busy || overrideReason.trim().length === 0}
                onClick={() => finishMut.mutate({ override: true, reason: overrideReason })}
                className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                data-testid="finish-override"
              >
                {t('emergency.resultsPending.finishAnyway')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="text-ink-500">{label}</dt>
      <dd className="text-end font-medium text-ink-900">{children}</dd>
    </div>
  );
}
