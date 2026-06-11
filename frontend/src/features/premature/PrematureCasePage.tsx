import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { cn } from '@/shared/ui/cn';
import { SignaturePad } from '@/shared/ui/SignaturePad';
import { BedStayCasePage, type BedStayActions } from '@/features/beds/case/BedStayCasePage';
import type { BedStayCaseView } from '@/features/beds/case/types';
import { MedicalHistoryTab } from '@/features/beds/case/forms/MedicalHistoryTab';
import { NursingTab } from '@/features/beds/case/forms/NursingTab';
import { TreatmentTab } from '@/features/beds/case/forms/TreatmentTab';
import { DocumentsTab } from '@/features/beds/case/forms/DocumentsTab';
import { listDocuments } from '@/features/beds/case/forms/documentsApi';
import { CaseFileTab } from './CaseFileTab';
import {
  getPrematureCase, listOrders, orderWorkup, setDischargeNote, finishTreatment,
  extendStay, reissueDischargePayment, upsertPrematureForm, recordTour,
  uploadSignature, fetchSignatureUrl,
  type PrematureCase, type RespSupport, type TourType,
} from './api';

const RESP = ['MV', 'CPAP', 'HFNC', 'NC', 'ROOM_AIR'] as const;

export function PrematureCasePage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const qc = useQueryClient();

  const { data: c, isLoading } = useQuery({
    queryKey: ['prem-case', id], queryFn: () => getPrematureCase(id!), enabled: !!id,
  });
  const ordersQuery = useQuery({
    queryKey: ['prem-orders', id], queryFn: () => listOrders(id!), enabled: !!id,
  });
  const docsQuery = useQuery({
    queryKey: ['stay-docs', 'PREMATURE', id],
    queryFn: () => listDocuments('PREMATURE', id!),
    enabled: !!id,
  });

  if (isLoading || !c) return <div className="p-6 text-sm text-ink-500">{t('common.loading')}</div>;

  const invalidate = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['prem-case', id] }),
    qc.invalidateQueries({ queryKey: ['prem-orders', id] }),
    qc.invalidateQueries({ queryKey: ['prem-beds'] }),
    qc.invalidateQueries({ queryKey: ['prem-admissions'] }),
    qc.invalidateQueries({ queryKey: ['payments'] }),
    qc.invalidateQueries({ queryKey: ['visits'] }),
  ]);

  const a = c.admission;
  const view: BedStayCaseView = {
    patientId: a.patientId, patientName: a.patientName, patientMrn: a.patientMrn,
    visitDisplayId: a.visitDisplayId, bedCode: a.bedCode, status: a.status,
    stayValue: a.stayValue, stayUnit: a.stayUnit, admittedAt: a.admittedAt, stayExpiresAt: a.stayExpiresAt,
    treatmentFinishedAt: a.treatmentFinishedAt, closedAt: a.closedAt, dischargeNote: a.dischargeNote,
    initialPaymentId: a.initialPaymentId, finalPaymentId: a.finalPaymentId,
  };

  const actions: BedStayActions = {
    onOrder: async (target, note) => { await orderWorkup(id!, target, note || undefined); await invalidate(); },
    onSetDischargeNote: async (note) => { await setDischargeNote(id!, note); await invalidate(); },
    onFinish: async (override, reason) => { await finishTreatment(id!, override, reason); await invalidate(); },
    onExtend: async (value, unit) => { await extendStay(id!, value, unit); await invalidate(); },
    onReissue: async () => { await reissueDischargePayment(id!); await invalidate(); },
  };

  const readOnly = a.status === 'CLOSED' || a.status === 'CANCELLED';
  const extraTabs = [
    { key: 'history', label: t('caseView.tabs.history'),
      content: <MedicalHistoryTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
    { key: 'nursing', label: t('caseView.tabs.nursing'),
      content: <NursingTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
    { key: 'treatment', label: t('caseView.tabs.treatment'),
      content: <TreatmentTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
    { key: 'documents', label: t('caseView.tabs.documents'),
      count: docsQuery.data?.filter((d) => !d.archived).length,
      content: <DocumentsTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
    { key: 'caseFile', label: t('caseView.tabs.caseFile'),
      content: <CaseFileTab c={c} admissionId={id!} readOnly={readOnly}
        onSaved={() => qc.invalidateQueries({ queryKey: ['prem-case', id] })} /> },
  ];

  return (
    <BedStayCasePage
      backTo="/departments/premature"
      backLabel={t('premature.detail.title')}
      view={view}
      orders={ordersQuery.data ?? []}
      ordersLoading={ordersQuery.isLoading}
      statusLabel={(code) => t(`premature.admissionStatus.${code}`)}
      canExtend
      actions={actions}
      extraTabs={extraTabs}
      clinical={
        <PrematureClinical
          c={c} admissionId={id!}
          onSaved={() => qc.invalidateQueries({ queryKey: ['prem-case', id] })}
          t={t}
        />
      }
    />
  );
}

/** The premature-specific Clinical tab: the Form and the daily Tours, behind an inner toggle. */
function PrematureClinical({ c, admissionId, onSaved, t }: {
  c: PrematureCase; admissionId: string; onSaved: () => void; t: (k: string) => string;
}) {
  const [view, setView] = useState<'form' | 'tours'>('form');
  return (
    <div className="space-y-4">
      <div className="inline-flex rounded-lg border border-ink-200 p-0.5">
        {(['form', 'tours'] as const).map((v) => (
          <button key={v} type="button" onClick={() => setView(v)}
            className={cn('rounded-md px-3 py-1.5 text-xs font-medium transition-colors',
              view === v ? 'bg-brand-600 text-white' : 'text-ink-600 hover:bg-ink-50')}
            data-testid={`clinical-${v}`}>
            {t(`premature.case.tab.${v}`)}
          </button>
        ))}
      </div>
      {view === 'form'
        ? <FormTab c={c} admissionId={admissionId} onSaved={onSaved} t={t} />
        : <ToursTab c={c} admissionId={admissionId} onSaved={onSaved} t={t} />}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="mb-1 block text-[11px] font-medium text-ink-600">{label}</span>{children}</label>;
}
const input = 'w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm';

function FormTab({ c, admissionId, onSaved, t }: { c: PrematureCase; admissionId: string; onSaved: () => void; t: (k: string) => string }) {
  const f = c.form;
  const p = c.prefill;
  const { register, handleSubmit } = useForm({
    defaultValues: {
      ageText: f?.ageText ?? p.ageText ?? '',
      birthWeightKg: f?.birthWeightKg ?? p.birthWeightKg ?? '',
      currentWeightKg: f?.currentWeightKg ?? '',
      gestationalAgeWeeks: f?.gestationalAgeWeeks ?? p.gestationalAgeWeeks ?? '',
      gestationalAgeDays: f?.gestationalAgeDays ?? p.gestationalAgeDays ?? '',
      correctedGaWeeks: f?.correctedGaWeeks ?? '', correctedGaDays: f?.correctedGaDays ?? '',
      lengthCm: f?.lengthCm ?? p.lengthCm ?? '', ofcCm: f?.ofcCm ?? p.ofcCm ?? '',
      feedingType: f?.feedingType ?? '', kcalPerOz: f?.kcalPerOz ?? '', enteralPerKg: f?.enteralPerKg ?? '',
      kcalPerKg: f?.kcalPerKg ?? '', gir: f?.gir ?? '', pharmacyOthers: f?.pharmacyOthers ?? '',
      lastCultureDate: f?.lastCultureDate ?? '', sampleType: f?.sampleType ?? '', cultureResult: f?.cultureResult ?? '',
      prescriptionNotes: f?.prescriptionNotes ?? '', specialistDoctorNotes: f?.specialistDoctorNotes ?? '',
    },
  });
  const mut = useMutation({
    mutationFn: (v: Record<string, unknown>) => upsertPrematureForm(admissionId, clean(v)),
    onSuccess: () => { toast.success(t('premature.form.saved')); onSaved(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });
  // Convert blank strings → null/number for the API.
  const clean = (v: Record<string, unknown>) => {
    const numKeys = ['birthWeightKg','currentWeightKg','gestationalAgeWeeks','gestationalAgeDays','correctedGaWeeks','correctedGaDays','lengthCm','ofcCm','kcalPerOz','enteralPerKg','kcalPerKg','gir'];
    const out: Record<string, unknown> = {};
    for (const [k, val] of Object.entries(v)) {
      if (val === '' || val == null) { out[k] = null; continue; }
      out[k] = numKeys.includes(k) ? Number(val) : val;
    }
    return out;
  };

  return (
    <form onSubmit={handleSubmit((v) => mut.mutate(v))} className="space-y-5" data-testid="prem-form">
      <Section title={t('premature.form.measurements')}>
        <Field label={t('premature.form.age') + ' *'}><input className={input} {...register('ageText')} data-testid="f-ageText" /></Field>
        <Field label={t('premature.form.birthWeight') + ' (kg) *'}><input className={input} type="number" step="0.001" {...register('birthWeightKg')} data-testid="f-birthWeightKg" /></Field>
        <Field label={t('premature.form.currentWeight') + ' (kg) *'}><input className={input} type="number" step="0.001" {...register('currentWeightKg')} data-testid="f-currentWeightKg" /></Field>
        <Field label={t('premature.form.gaWeeks') + ' *'}><input className={input} type="number" {...register('gestationalAgeWeeks')} data-testid="f-gaWeeks" /></Field>
        <Field label={t('premature.form.gaDays') + ' *'}><input className={input} type="number" {...register('gestationalAgeDays')} data-testid="f-gaDays" /></Field>
        <Field label={t('premature.form.correctedGaWeeks') + ' *'}><input className={input} type="number" {...register('correctedGaWeeks')} data-testid="f-correctedGaWeeks" /></Field>
        <Field label={t('premature.form.correctedGaDays') + ' *'}><input className={input} type="number" {...register('correctedGaDays')} data-testid="f-correctedGaDays" /></Field>
        <Field label={t('premature.form.length') + ' (cm) *'}><input className={input} type="number" step="0.1" {...register('lengthCm')} data-testid="f-lengthCm" /></Field>
        <Field label={t('premature.form.ofc') + ' (cm) *'}><input className={input} type="number" step="0.1" {...register('ofcCm')} data-testid="f-ofcCm" /></Field>
      </Section>
      <Section title={t('premature.form.clinicalPharmacy')}>
        <Field label={t('premature.form.feedingType') + ' *'}><input className={input} {...register('feedingType')} data-testid="f-feedingType" /></Field>
        <Field label="kCal/oz"><input className={input} type="number" step="0.01" {...register('kcalPerOz')} /></Field>
        <Field label="Enteral/Kg"><input className={input} type="number" step="0.01" {...register('enteralPerKg')} /></Field>
        <Field label="kCal/kg"><input className={input} type="number" step="0.01" {...register('kcalPerKg')} /></Field>
        <Field label="GIR"><input className={input} type="number" step="0.01" {...register('gir')} /></Field>
        <Field label={t('premature.form.others')}><input className={input} {...register('pharmacyOthers')} /></Field>
      </Section>
      <Section title={t('premature.form.culture')}>
        <Field label={t('premature.form.lastCultureDate')}><input className={input} type="date" {...register('lastCultureDate')} /></Field>
        <Field label={t('premature.form.sampleType')}><input className={input} {...register('sampleType')} /></Field>
        <Field label={t('premature.form.cultureResult')}><input className={input} {...register('cultureResult')} /></Field>
      </Section>
      <Section title={t('premature.form.notes')}>
        <Field label={t('premature.form.rx')}><textarea className={input} rows={2} {...register('prescriptionNotes')} /></Field>
        <Field label={t('premature.form.specialistNotes')}><textarea className={input} rows={2} {...register('specialistDoctorNotes')} /></Field>
      </Section>
      <button type="submit" disabled={mut.isPending}
        className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
        data-testid="save-form">
        {mut.isPending ? t('common.loading') : t('premature.form.save')}
      </button>

      {f && (
        <Section title={t('premature.form.signatures')}>
          <SignaturePad slot="CLINICAL_PHARMACY" label={t('premature.form.pharmacySign')} meta={f.clinicalPharmacySignature}
            upload={(blob, name) => uploadSignature(admissionId, 'CLINICAL_PHARMACY', blob, name)}
            fetchUrl={() => fetchSignatureUrl(admissionId, 'CLINICAL_PHARMACY')} onSaved={onSaved} t={t} />
          <SignaturePad slot="RESIDENT" label={t('premature.form.residentSign')} meta={f.residentSignature}
            upload={(blob, name) => uploadSignature(admissionId, 'RESIDENT', blob, name)}
            fetchUrl={() => fetchSignatureUrl(admissionId, 'RESIDENT')} onSaved={onSaved} t={t} />
          <SignaturePad slot="SENIOR_RESIDENT" label={t('premature.form.seniorSign')} meta={f.seniorResidentSignature}
            upload={(blob, name) => uploadSignature(admissionId, 'SENIOR_RESIDENT', blob, name)}
            fetchUrl={() => fetchSignatureUrl(admissionId, 'SENIOR_RESIDENT')} onSaved={onSaved} t={t} />
        </Section>
      )}
    </form>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-ink-100 bg-white p-4">
      <h3 className="mb-3 text-sm font-semibold text-ink-900">{title}</h3>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">{children}</div>
    </section>
  );
}

function ToursTab({ c, admissionId, onSaved, t }: { c: PrematureCase; admissionId: string; onSaved: () => void; t: (k: string) => string }) {
  const { register, handleSubmit, reset } = useForm<{ tourType: TourType; respSupport: RespSupport[] } & Record<string, unknown>>({
    defaultValues: { tourType: 'MORNING', respSupport: [] },
  });
  const mut = useMutation({
    mutationFn: (v: Record<string, unknown>) => recordTour(admissionId, cleanTour(v)),
    onSuccess: () => { toast.success(t('premature.form.tourAdded')); reset({ tourType: 'MORNING', respSupport: [] }); onSaved(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });
  const cleanTour = (v: Record<string, unknown>) => {
    const numKeys = ['respRate','spo2','pulseRate','babyTempC','incubatorTempC','humidity','rbs'];
    const out: Record<string, unknown> = { tourType: v.tourType, respSupport: v.respSupport ?? [] };
    for (const k of ['respRate','spo2','pulseRate','bowelMotion','uop','feeding','vomiting','jaundice','ivAccess','ivFluid','babyTempC','incubatorTempC','humidity','nasalSeptum','rbs','others']) {
      const val = (v as Record<string, unknown>)[k];
      out[k] = (val === '' || val == null) ? null : (numKeys.includes(k) ? Number(val) : val);
    }
    return out;
  };

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit((v) => mut.mutate(v))} className="rounded-xl border border-ink-100 bg-white p-4" data-testid="tour-form">
        <h3 className="mb-3 text-sm font-semibold text-ink-900">{t('premature.case.addTour')}</h3>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Field label={t('premature.case.tourType')}>
            <select className={input} {...register('tourType')} data-testid="tour-type">
              <option value="MORNING">{t('premature.case.morning')}</option>
              <option value="NIGHT">{t('premature.case.night')}</option>
            </select>
          </Field>
          <Field label={t('premature.case.respRate') + ' *'}><input className={input} type="number" {...register('respRate')} data-testid="tour-respRate" /></Field>
          <Field label="SpO₂ (%) *"><input className={input} type="number" {...register('spo2')} data-testid="tour-spo2" /></Field>
          <Field label={t('premature.case.pulse') + ' *'}><input className={input} type="number" {...register('pulseRate')} data-testid="tour-pulse" /></Field>
          <Field label={t('premature.case.uop') + ' *'}><input className={input} {...register('uop')} data-testid="tour-uop" /></Field>
          <Field label={t('premature.case.babyTemp') + ' (°C) *'}><input className={input} type="number" step="0.1" {...register('babyTempC')} data-testid="tour-temp" /></Field>
          <Field label={t('premature.case.incuTemp') + ' (°C)'}><input className={input} type="number" step="0.1" {...register('incubatorTempC')} /></Field>
          <Field label={t('premature.case.humidity') + ' (%)'}><input className={input} type="number" {...register('humidity')} /></Field>
          <Field label="RBS"><input className={input} type="number" {...register('rbs')} /></Field>
          <Field label={t('premature.case.feeding')}><input className={input} {...register('feeding')} /></Field>
          <Field label={t('premature.case.bowel')}><input className={input} {...register('bowelMotion')} /></Field>
          <Field label={t('premature.case.vomiting')}><input className={input} {...register('vomiting')} /></Field>
          <Field label={t('premature.case.jaundice')}><input className={input} {...register('jaundice')} /></Field>
          <Field label={t('premature.case.ivAccess')}><input className={input} {...register('ivAccess')} /></Field>
          <Field label={t('premature.case.ivFluid')}><input className={input} {...register('ivFluid')} /></Field>
          <Field label={t('premature.case.nasalSeptum')}><input className={input} {...register('nasalSeptum')} /></Field>
        </div>
        <div className="mt-3">
          <span className="mb-1 block text-[11px] font-medium text-ink-600">{t('premature.case.respSupport')} *</span>
          <div className="flex flex-wrap gap-3">
            {RESP.map((r) => (
              <label key={r} className="inline-flex items-center gap-1 text-xs">
                <input type="checkbox" value={r} {...register('respSupport')} className="accent-brand-600" /> {r}
              </label>
            ))}
          </div>
        </div>
        <Field label={t('premature.form.others')}><textarea className={input + ' mt-3'} rows={2} {...register('others')} /></Field>
        <button type="submit" disabled={mut.isPending}
          className="mt-3 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          data-testid="save-tour">
          {mut.isPending ? t('common.loading') : t('premature.case.addTour')}
        </button>
      </form>

      <div className="rounded-xl border border-ink-100 bg-white" data-testid="tour-list">
        <div className="border-b border-ink-100 px-4 py-3 text-sm font-semibold text-ink-900">{t('premature.case.tourLog')}</div>
        {c.tours.length === 0 ? (
          <p className="p-4 text-xs text-ink-500">{t('premature.case.noTours')}</p>
        ) : (
          <ul className="divide-y divide-ink-100 text-xs">
            {c.tours.map((tr) => (
              <li key={tr.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2">
                <span className="font-medium text-ink-900">{t('premature.case.' + (tr.tourType === 'MORNING' ? 'morning' : 'night'))}</span>
                <span className="text-ink-500">{new Date(tr.recordedAt).toLocaleString()}</span>
                <span>RR {tr.respRate}</span><span>SpO₂ {tr.spo2}%</span><span>HR {tr.pulseRate}</span>
                <span>T {String(tr.babyTempC)}°C</span><span>UOP {tr.uop}</span>
                <span className="text-ink-500">{tr.respSupport.join(', ')}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
