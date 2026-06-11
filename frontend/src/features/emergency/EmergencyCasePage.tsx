import { useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Siren } from 'lucide-react';
import { BedStayCasePage, type BedStayActions } from '@/features/beds/case/BedStayCasePage';
import type { BedStayCaseView } from '@/features/beds/case/types';
import { MedicalHistoryTab } from '@/features/beds/case/forms/MedicalHistoryTab';
import { NursingTab } from '@/features/beds/case/forms/NursingTab';
import { TreatmentTab } from '@/features/beds/case/forms/TreatmentTab';
import { DocumentsTab } from '@/features/beds/case/forms/DocumentsTab';
import {
  getCase, listOrders, orderWorkup, setDischargeNote, finishTreatment, reissueDischargePayment,
} from './api';

export function EmergencyCasePage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const qc = useQueryClient();

  const { data: c, isLoading } = useQuery({
    queryKey: ['emerg-case', id], queryFn: () => getCase(id!), enabled: !!id,
  });
  const ordersQuery = useQuery({
    queryKey: ['emerg-orders', id], queryFn: () => listOrders(id!), enabled: !!id,
  });

  if (isLoading || !c) return <div className="p-6 text-sm text-ink-500">{t('common.loading')}</div>;

  const invalidate = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['emerg-case', id] }),
    qc.invalidateQueries({ queryKey: ['emerg-orders', id] }),
    qc.invalidateQueries({ queryKey: ['emerg-beds'] }),
    qc.invalidateQueries({ queryKey: ['emerg-cases'] }),
    qc.invalidateQueries({ queryKey: ['payments'] }),
    qc.invalidateQueries({ queryKey: ['visits'] }),
  ]);

  const view: BedStayCaseView = {
    patientId: c.patientId, patientName: c.patientName, patientMrn: c.patientMrn,
    visitDisplayId: c.visitDisplayId, bedCode: c.bedCode, serviceName: c.serviceName, status: c.status,
    stayValue: c.stayValue, stayUnit: c.stayUnit, admittedAt: c.admittedAt, stayExpiresAt: c.stayExpiresAt,
    treatmentFinishedAt: c.treatmentFinishedAt, closedAt: c.closedAt, dischargeNote: c.dischargeNote,
    initialPaymentId: c.initialPaymentId, finalPaymentId: c.finalPaymentId,
  };

  // Emergency has no extend-stay.
  const actions: BedStayActions = {
    onOrder: async (target, note) => { await orderWorkup(id!, target, note || undefined); await invalidate(); },
    onSetDischargeNote: async (note) => { await setDischargeNote(id!, note); await invalidate(); },
    onFinish: async (override, reason) => { await finishTreatment(id!, override, reason); await invalidate(); },
    onReissue: async () => { await reissueDischargePayment(id!); await invalidate(); },
  };

  const readOnly = c.status === 'CLOSED' || c.status === 'CANCELLED';
  const extraTabs = [
    { key: 'history', label: t('caseView.tabs.history'),
      content: <MedicalHistoryTab department="EMERGENCY" stayId={id!} readOnly={readOnly} /> },
    { key: 'nursing', label: t('caseView.tabs.nursing'),
      content: <NursingTab department="EMERGENCY" stayId={id!} readOnly={readOnly} /> },
    { key: 'treatment', label: t('caseView.tabs.treatment'),
      content: <TreatmentTab department="EMERGENCY" stayId={id!} readOnly={readOnly} /> },
    { key: 'documents', label: t('caseView.tabs.documents'),
      content: <DocumentsTab department="EMERGENCY" stayId={id!} readOnly={readOnly} /> },
  ];

  return (
    <BedStayCasePage
      backTo="/departments/emergency"
      backLabel={t('emergency.detail.title')}
      view={view}
      orders={ordersQuery.data ?? []}
      ordersLoading={ordersQuery.isLoading}
      statusLabel={(code) => t(`emergency.caseStatus.${code}`)}
      canExtend={false}
      actions={actions}
      extraTabs={extraTabs}
      clinical={
        <div className="rounded-xl border border-ink-100 bg-white p-4 text-sm">
          <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-ink-900">
            <Siren size={15} className="text-brand-600" /> {t('emergency.case.serviceDetails')}
          </h3>
          <dl className="grid grid-cols-2 gap-3">
            <div><dt className="text-ink-500">{t('caseView.service')}</dt><dd className="font-medium text-ink-900">{c.serviceName}</dd></div>
            <div><dt className="text-ink-500">{t('emergency.serviceCode')}</dt><dd className="font-mono text-ink-700">{c.serviceCode}</dd></div>
          </dl>
          <p className="mt-3 text-xs text-ink-400">{t('emergency.case.clinicalHint')}</p>
        </div>
      }
    />
  );
}
