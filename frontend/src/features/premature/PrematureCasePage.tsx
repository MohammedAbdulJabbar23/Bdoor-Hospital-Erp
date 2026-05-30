import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { ArrowLeft, Baby, ChevronRight } from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import { cn } from '@/shared/ui/cn';
import { SignaturePad } from './SignaturePad';
import {
  getPrematureCase, upsertPrematureForm, recordTour,
  type PrematureCase, type RespSupport, type TourType,
} from './api';

const RESP = ['MV', 'CPAP', 'HFNC', 'NC', 'ROOM_AIR'] as const;

export function PrematureCasePage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'overview' | 'form' | 'tours'>('form');

  const { data: c, isLoading } = useQuery({
    queryKey: ['prem-case', id], queryFn: () => getPrematureCase(id!), enabled: !!id,
  });

  if (isLoading || !c) return <div className="p-6 text-sm text-ink-500">{t('common.loading')}</div>;

  return (
    <div className="space-y-4 p-1">
      <button type="button" onClick={() => navigate('/departments/premature')}
        className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-900">
        <ArrowLeft size={14} className="rtl:rotate-180" /> {t('premature.detail.title')}
      </button>

      <header className="flex items-center justify-between">
        <button type="button" onClick={() => navigate(`/patients/${c.admission.patientId}`)}
          className="group flex items-center gap-3 text-start" data-testid="case-patient">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-50 text-brand-700"><Baby size={18} /></span>
          <span>
            <span className="block font-semibold text-ink-900">{c.admission.patientName}</span>
            <span className="block font-mono text-[11px] text-ink-500">
              {c.admission.patientMrn} · {c.admission.bedCode} · {t(`premature.admissionStatus.${c.admission.status}`)}
            </span>
          </span>
          <ChevronRight size={16} className="text-ink-300 group-hover:text-brand-600 rtl:rotate-180" />
        </button>
      </header>

      <div className="flex gap-1 border-b border-ink-100">
        {(['form', 'tours', 'overview'] as const).map((tb) => (
          <button key={tb} type="button" onClick={() => setTab(tb)}
            className={cn('border-b-2 px-4 py-2.5 text-sm font-medium transition-colors',
              tab === tb ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-600 hover:text-ink-900')}
            data-testid={`case-tab-${tb}`}>
            {t(`premature.case.tab.${tb}`)}
          </button>
        ))}
      </div>

      {tab === 'form' && <FormTab c={c} onSaved={() => qc.invalidateQueries({ queryKey: ['prem-case', id] })} t={t} admissionId={id!} />}
      {tab === 'tours' && <ToursTab c={c} onSaved={() => qc.invalidateQueries({ queryKey: ['prem-case', id] })} t={t} admissionId={id!} />}
      {tab === 'overview' && <OverviewTab c={c} t={t} />}
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
          <SignaturePad admissionId={admissionId} slot="CLINICAL_PHARMACY" label={t('premature.form.pharmacySign')} meta={f.clinicalPharmacySignature} onSaved={onSaved} t={t} />
          <SignaturePad admissionId={admissionId} slot="RESIDENT" label={t('premature.form.residentSign')} meta={f.residentSignature} onSaved={onSaved} t={t} />
          <SignaturePad admissionId={admissionId} slot="SENIOR_RESIDENT" label={t('premature.form.seniorSign')} meta={f.seniorResidentSignature} onSaved={onSaved} t={t} />
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

function OverviewTab({ c, t }: { c: PrematureCase; t: (k: string) => string }) {
  return (
    <div className="rounded-xl border border-ink-100 bg-white p-4 text-sm">
      <dl className="grid grid-cols-2 gap-3">
        <div><dt className="text-ink-500">{t('premature.detail.status')}</dt><dd className="font-medium">{t(`premature.admissionStatus.${c.admission.status}`)}</dd></div>
        <div><dt className="text-ink-500">{t('premature.bed')}</dt><dd className="font-medium">{c.admission.bedCode}</dd></div>
        <div><dt className="text-ink-500">{t('premature.stayValue')}</dt><dd className="font-medium">{c.admission.stayValue} {t(`premature.${c.admission.stayUnit === 'DAYS' ? 'days' : 'hours'}`)}</dd></div>
        <div><dt className="text-ink-500">{t('premature.detail.admittedAt')}</dt><dd className="font-medium">{new Date(c.admission.admittedAt).toLocaleString()}</dd></div>
      </dl>
    </div>
  );
}
