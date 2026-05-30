import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Baby, BedDouble, Clock, ChevronRight } from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import { BedDetailPanel } from './BedDetailPanel';
import {
  listBeds, listAdmissions, listIncomingPremature, admitPatient, extendStay, finishTreatment,
  reissueDischargePayment,
  type Bed, type PrematureVisit, type StayUnit,
} from './api';

function isExpiringSoon(iso: string): boolean {
  const expires = new Date(iso).getTime();
  return expires - Date.now() < 2 * 60 * 60 * 1000; // within 2h
}

export function PrematureWorkspacePage() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [admitFor, setAdmitFor] = useState<PrematureVisit | null>(null);
  const [selectedAdmissionId, setSelectedAdmissionId] = useState<string | null>(null);

  const { data: beds } = useQuery({ queryKey: ['prem-beds'], queryFn: listBeds, refetchInterval: 15000 });
  const { data: admissions } = useQuery({
    queryKey: ['prem-admissions'], queryFn: () => listAdmissions(), refetchInterval: 15000,
  });
  const { data: incoming } = useQuery({
    queryKey: ['prem-incoming'], queryFn: listIncomingPremature, refetchInterval: 20000,
  });

  const admittedVisitIds = useMemo(
    () => new Set((admissions ?? []).map((a) => a.visitId)),
    [admissions],
  );
  const queue = useMemo(
    () => (incoming ?? []).filter((v) => !admittedVisitIds.has(v.id)),
    [incoming, admittedVisitIds],
  );

  // Drawer: full admission + bed derived from already-loaded data (no extra fetch).
  const selectedAdmission = useMemo(
    () => (admissions ?? []).find((a) => a.id === selectedAdmissionId) ?? null,
    [admissions, selectedAdmissionId],
  );
  const selectedBed = useMemo(
    () => (beds ?? []).find((b) => b.occupant?.admissionId === selectedAdmissionId) ?? null,
    [beds, selectedAdmissionId],
  );
  // Auto-close when the selected admission leaves the active set (e.g. discharged).
  useEffect(() => {
    if (selectedAdmissionId && admissions && !admissions.some((a) => a.id === selectedAdmissionId)) {
      setSelectedAdmissionId(null);
    }
  }, [admissions, selectedAdmissionId]);

  const invalidate = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['prem-beds'] }),
      qc.invalidateQueries({ queryKey: ['prem-admissions'] }),
      qc.invalidateQueries({ queryKey: ['prem-incoming'] }),
      qc.invalidateQueries({ queryKey: ['payments'] }),
      qc.invalidateQueries({ queryKey: ['visits'] }),
    ]);
  };

  const finishMut = useMutation({
    mutationFn: (id: string) => finishTreatment(id),
    onSuccess: async () => { toast.success(t('premature.toast.finished')); await invalidate(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  const extendMut = useMutation({
    mutationFn: ({ id, value, unit }: { id: string; value: number; unit: StayUnit }) =>
      extendStay(id, value, unit),
    onSuccess: async () => { toast.success(t('premature.toast.extended')); await invalidate(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  const reissueMut = useMutation({
    mutationFn: (id: string) => reissueDischargePayment(id),
    onSuccess: async () => { toast.success(t('premature.toast.reissued')); await invalidate(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  return (
    <div className="space-y-6 p-1">
      <header className="flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
          <Baby size={20} />
        </span>
        <div>
          <h1 className="text-lg font-semibold text-ink-900">{t('premature.title')}</h1>
          <p className="text-sm text-ink-500">{t('premature.subtitle')}</p>
        </div>
      </header>

      {/* Incoming queue */}
      <section className="rounded-xl border border-ink-100 bg-white" data-testid="prem-incoming">
        <div className="border-b border-ink-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-ink-900">{t('premature.incoming')}</h2>
        </div>
        {queue.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">{t('premature.noIncoming')}</p>
        ) : (
          <ul className="divide-y divide-ink-100">
            {queue.map((v) => (
              <li key={v.id} className="flex items-center justify-between px-5 py-3">
                <div>
                  <div className="font-medium text-ink-900">{v.patientName}</div>
                  <div className="font-mono text-xs text-ink-500">{v.patientMrn} · {v.visitDisplayId}</div>
                </div>
                <button
                  type="button"
                  onClick={() => setAdmitFor(v)}
                  className="rounded-md bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
                  data-testid={`admit-${v.id}`}
                >
                  {t('premature.assignBed')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Bed dashboard */}
      <section className="rounded-xl border border-ink-100 bg-white" data-testid="prem-beds">
        <div className="border-b border-ink-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-ink-900">{t('premature.bedDashboard')}</h2>
        </div>
        <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
          {(beds ?? []).map((bed) => (
            <BedCard
              key={bed.id}
              bed={bed}
              onOpen={() => bed.occupant && setSelectedAdmissionId(bed.occupant.admissionId)}
              t={t}
            />
          ))}
        </div>
      </section>

      {admitFor && (
        <AdmitDialog
          visit={admitFor}
          beds={(beds ?? []).filter((b) => b.status === 'AVAILABLE' && b.active)}
          onClose={() => setAdmitFor(null)}
          onAdmitted={async () => { setAdmitFor(null); await invalidate(); }}
          t={t}
        />
      )}

      {selectedBed && (
        <BedDetailPanel
          bed={selectedBed}
          admission={selectedAdmission}
          onClose={() => setSelectedAdmissionId(null)}
          onExtend={extendMut.mutate}
          onFinish={finishMut.mutate}
          onReissue={reissueMut.mutate}
          pending={extendMut.isPending || finishMut.isPending || reissueMut.isPending}
          t={t}
          dir={i18n.dir() === 'rtl' ? 'rtl' : 'ltr'}
        />
      )}
    </div>
  );
}

function BedCard({
  bed, onOpen, t,
}: {
  bed: Bed;
  onOpen: () => void;
  t: (k: string) => string;
}) {
  const occ = bed.occupant;
  const expiring = occ && isExpiringSoon(occ.stayExpiresAt);
  const statusClass =
    bed.status === 'AVAILABLE'
      ? 'bg-emerald-50 text-emerald-700'
      : bed.status === 'OCCUPIED'
      ? 'bg-brand-50 text-brand-700'
      : 'bg-amber-50 text-amber-700';

  const header = (
    <div className="flex items-center justify-between">
      <span className="flex items-center gap-1.5 font-mono text-sm font-semibold text-ink-900">
        <BedDouble size={14} /> {bed.code}
      </span>
      <span
        className={'rounded-full px-2 py-0.5 text-[11px] font-medium ' + statusClass}
        data-testid={`bed-status-${bed.code}`}
      >
        {t(`premature.bedStatus.${bed.status}`)}
      </span>
    </div>
  );

  // Empty bed — quiet, non-interactive tile.
  if (!occ) {
    return (
      <div
        className="rounded-lg border border-dashed border-ink-200 p-3 opacity-80"
        data-testid={`bed-${bed.code}`}
      >
        {header}
        <p className="mt-2 text-[11px] text-ink-400">{t('premature.detail.empty')}</p>
      </div>
    );
  }

  // Occupied / pending — clickable, opens the detail drawer.
  return (
    <button
      type="button"
      onClick={onOpen}
      className="group w-full rounded-lg border border-ink-100 p-3 text-start transition hover:border-brand-200 hover:bg-brand-50/40 focus:outline-none focus:ring-2 focus:ring-brand-200"
      data-testid={`bed-${bed.code}`}
      aria-label={`${bed.code} — ${occ.patientName}`}
    >
      {header}
      <div className="mt-2 flex items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-xs font-medium text-ink-900">{occ.patientName}</div>
          <div className="truncate font-mono text-[11px] text-ink-500">{occ.patientMrn}</div>
        </div>
        <ChevronRight size={16} className="shrink-0 text-ink-300 group-hover:text-brand-600 rtl:rotate-180" />
      </div>
      {expiring && (
        <div className="mt-2 inline-flex items-center gap-1 rounded bg-red-50 px-1.5 py-0.5 text-[11px] font-medium text-red-700">
          <Clock size={11} /> {t('premature.expiringSoon')}
        </div>
      )}
    </button>
  );
}

function AdmitDialog({
  visit, beds, onClose, onAdmitted, t,
}: {
  visit: PrematureVisit;
  beds: Bed[];
  onClose: () => void;
  onAdmitted: () => void;
  t: (k: string) => string;
}) {
  const [bedId, setBedId] = useState(beds[0]?.id ?? '');
  const [stayValue, setStayValue] = useState(3);
  const [stayUnit, setStayUnit] = useState<StayUnit>('DAYS');

  const mut = useMutation({
    mutationFn: () => admitPatient({ visitId: visit.id, bedId, stayValue, stayUnit }),
    onSuccess: async () => { toast.success(t('premature.toast.admitted')); onAdmitted(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <div className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl" data-testid="admit-dialog">
        <h3 className="text-sm font-semibold text-ink-900">{t('premature.assignBed')} — {visit.patientName}</h3>
        <label className="mt-4 block text-xs font-medium text-ink-700">{t('premature.bed')}</label>
        <select
          value={bedId} onChange={(e) => setBedId(e.target.value)}
          className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
          data-testid="admit-bed-select"
        >
          {beds.map((b) => <option key={b.id} value={b.id}>{b.code}{b.room ? ` · ${b.room}` : ''}</option>)}
        </select>
        <div className="mt-3 flex gap-2">
          <div className="flex-1">
            <label className="block text-xs font-medium text-ink-700">{t('premature.stayValue')}</label>
            <input
              type="number" min={1} value={stayValue}
              onChange={(e) => setStayValue(Number(e.target.value))}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
              data-testid="admit-stay-value"
            />
          </div>
          <div className="flex-1">
            <label className="block text-xs font-medium text-ink-700">{t('premature.stayUnit')}</label>
            <select
              value={stayUnit} onChange={(e) => setStayUnit(e.target.value as StayUnit)}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
              data-testid="admit-stay-unit"
            >
              <option value="DAYS">{t('premature.days')}</option>
              <option value="HOURS">{t('premature.hours')}</option>
            </select>
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-md px-3 py-1.5 text-sm text-ink-600 hover:bg-ink-100">
            {t('common.cancel')}
          </button>
          <button
            type="button" disabled={!bedId || mut.isPending} onClick={() => mut.mutate()}
            className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            data-testid="admit-confirm"
          >
            {mut.isPending ? t('common.loading') : t('premature.confirmAdmit')}
          </button>
        </div>
      </div>
    </div>
  );
}
