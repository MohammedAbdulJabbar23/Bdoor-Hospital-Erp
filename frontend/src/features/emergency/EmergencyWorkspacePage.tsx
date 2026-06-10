import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Siren } from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import { BedStatusFilter, type BedFilter } from '@/features/beds/BedStatusFilter';
import { BedCard } from '@/features/beds/BedCard';
import {
  listBeds, listCases, listIncomingEmergency, admitPatient, listEmergencyServices,
  type Bed, type EmergencyVisit, type StayUnit, type EmergencyService,
} from './api';

export function EmergencyWorkspacePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [admitFor, setAdmitFor] = useState<EmergencyVisit | null>(null);
  const [bedFilter, setBedFilter] = useState<BedFilter>('ALL');

  const { data: beds } = useQuery({ queryKey: ['emerg-beds'], queryFn: listBeds, refetchInterval: 15000 });
  const { data: cases } = useQuery({
    queryKey: ['emerg-cases'], queryFn: () => listCases(), refetchInterval: 15000,
  });
  const { data: incoming } = useQuery({
    queryKey: ['emerg-incoming'], queryFn: listIncomingEmergency, refetchInterval: 20000,
  });

  const admittedVisitIds = useMemo(
    () => new Set((cases ?? []).map((c) => c.visitId)),
    [cases],
  );
  const queue = useMemo(
    () => (incoming ?? []).filter((v) => !admittedVisitIds.has(v.id)),
    [incoming, admittedVisitIds],
  );

  // Client-side status filter over the already-fetched beds. Default ALL shows every bed.
  const bedCounts = useMemo(() => {
    const all = beds ?? [];
    return {
      ALL: all.length,
      AVAILABLE: all.filter((b) => b.status === 'AVAILABLE').length,
      PENDING_PAYMENT: all.filter((b) => b.status === 'PENDING_PAYMENT').length,
      OCCUPIED: all.filter((b) => b.status === 'OCCUPIED').length,
    };
  }, [beds]);
  const visibleBeds = useMemo(
    () => (beds ?? []).filter((b) => bedFilter === 'ALL' || b.status === bedFilter),
    [beds, bedFilter],
  );

  const invalidate = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['emerg-beds'] }),
      qc.invalidateQueries({ queryKey: ['emerg-cases'] }),
      qc.invalidateQueries({ queryKey: ['emerg-incoming'] }),
      qc.invalidateQueries({ queryKey: ['payments'] }),
      qc.invalidateQueries({ queryKey: ['visits'] }),
    ]);
  };

  return (
    <div className="space-y-6 p-1">
      <header className="flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
          <Siren size={20} />
        </span>
        <div>
          <h1 className="text-lg font-semibold text-ink-900">{t('emergency.title')}</h1>
          <p className="text-sm text-ink-500">{t('emergency.subtitle')}</p>
        </div>
      </header>

      {/* Incoming queue */}
      <section className="rounded-xl border border-ink-100 bg-white" data-testid="emerg-incoming">
        <div className="border-b border-ink-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-ink-900">{t('emergency.incoming')}</h2>
        </div>
        {queue.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">{t('emergency.noIncoming')}</p>
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
                  {t('emergency.assignBed')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Bed dashboard */}
      <section className="rounded-xl border border-ink-100 bg-white" data-testid="emerg-beds">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-ink-900">{t('emergency.bedDashboard')}</h2>
          <BedStatusFilter ns="emergency" value={bedFilter} counts={bedCounts} onChange={setBedFilter} t={t} />
        </div>
        <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
          {visibleBeds.map((bed) => (
            <BedCard
              key={bed.id}
              bed={bed}
              ns="emergency"
              onOpen={() => bed.occupant && navigate(`/emergency/cases/${bed.occupant.caseId}`)}
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

    </div>
  );
}

function AdmitDialog({
  visit, beds, onClose, onAdmitted, t,
}: {
  visit: EmergencyVisit;
  beds: Bed[];
  onClose: () => void;
  onAdmitted: () => void;
  t: (k: string) => string;
}) {
  const [bedId, setBedId] = useState(beds[0]?.id ?? '');
  const [serviceItemId, setServiceItemId] = useState('');
  const [stayValue, setStayValue] = useState(1);
  const [stayUnit, setStayUnit] = useState<StayUnit>('DAYS');
  // The beds query can resolve after this dialog mounts (parallel fetch with the incoming
  // queue). Fall back to the first available bed so the effective selection always matches
  // what the <select> displays as its default.
  const effectiveBedId = bedId || (beds[0]?.id ?? '');

  const { data: services } = useQuery<EmergencyService[]>({
    queryKey: ['emerg-services'],
    queryFn: listEmergencyServices,
  });

  const mut = useMutation({
    mutationFn: () => admitPatient({ visitId: visit.id, bedId: effectiveBedId, serviceItemId, stayValue, stayUnit }),
    onSuccess: async () => { toast.success(t('emergency.toast.admitted')); onAdmitted(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('emergency.toast.error')),
  });

  const canConfirm = !!effectiveBedId && !!serviceItemId && !mut.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <div className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl" data-testid="admit-dialog">
        <h3 className="text-sm font-semibold text-ink-900">{t('emergency.assignBed')} — {visit.patientName}</h3>

        <label className="mt-4 block text-xs font-medium text-ink-700">{t('emergency.service')}</label>
        <select
          value={serviceItemId} onChange={(e) => setServiceItemId(e.target.value)}
          className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
          data-testid="admit-service-select"
        >
          <option value="">{t('emergency.selectService')}</option>
          {(services ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.code} — {s.nameEn} ({s.fee} {s.currency})
            </option>
          ))}
        </select>

        <label className="mt-3 block text-xs font-medium text-ink-700">{t('emergency.bed')}</label>
        <select
          value={effectiveBedId} onChange={(e) => setBedId(e.target.value)}
          className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
          data-testid="admit-bed-select"
        >
          {beds.map((b) => <option key={b.id} value={b.id}>{b.code}{b.room ? ` · ${b.room}` : ''}</option>)}
        </select>

        <div className="mt-3 flex gap-2">
          <div className="flex-1">
            <label className="block text-xs font-medium text-ink-700">{t('emergency.stayValue')}</label>
            <input
              type="number" min={1} value={stayValue}
              onChange={(e) => setStayValue(Number(e.target.value))}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
              data-testid="admit-stay-value"
            />
          </div>
          <div className="flex-1">
            <label className="block text-xs font-medium text-ink-700">{t('emergency.stayUnit')}</label>
            <select
              value={stayUnit} onChange={(e) => setStayUnit(e.target.value as StayUnit)}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
              data-testid="admit-stay-unit"
            >
              <option value="DAYS">{t('emergency.days')}</option>
              <option value="HOURS">{t('emergency.hours')}</option>
            </select>
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-md px-3 py-1.5 text-sm text-ink-600 hover:bg-ink-100">
            {t('common.cancel')}
          </button>
          <button
            type="button" disabled={!canConfirm} onClick={() => mut.mutate()}
            className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            data-testid="admit-confirm"
          >
            {mut.isPending ? t('common.loading') : t('emergency.confirmAdmit')}
          </button>
        </div>
      </div>
    </div>
  );
}
