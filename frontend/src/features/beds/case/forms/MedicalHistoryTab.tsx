import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { SignaturePad } from '@/shared/ui/SignaturePad';
import {
  getMedicalHistory, saveMedicalHistory, uploadMhSignature, fetchMhSignatureUrl,
  type MedicalHistory, type MhSlot, type StayDepartment,
} from './api';

const TEXT_FIELDS = [
  'doctorName', 'chiefComplaint', 'presentIllnessHx', 'psHx', 'pmHx',
  'familyHx', 'allergicHx', 'socialSmoker', 'socialAlcohol', 'socialSleep',
  'drugHx', 'physicalExamination',
] as const;
const MULTILINE = new Set(['chiefComplaint', 'presentIllnessHx', 'psHx', 'pmHx',
  'familyHx', 'allergicHx', 'drugHx', 'physicalExamination']);
const SLOTS: MhSlot[] = ['SPECIALIST', 'PERMANENT', 'RESIDENT'];

type Draft = Record<string, string>;

function toDraft(form: MedicalHistory | null): Draft {
  const d: Draft = { weightKg: '', heightCm: '' };
  for (const f of TEXT_FIELDS) d[f] = (form?.[f] as string | null) ?? '';
  if (form?.weightKg != null) d.weightKg = String(form.weightKg);
  if (form?.heightCm != null) d.heightCm = String(form.heightCm);
  return d;
}

export function MedicalHistoryTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-mh', department, stayId];
  const { data, isLoading } = useQuery({ queryKey, queryFn: () => getMedicalHistory(department, stayId) });
  const [draft, setDraft] = useState<Draft>(toDraft(null));
  useEffect(() => { if (data) setDraft(toDraft(data.form)); }, [data]);

  const save = useMutation({
    mutationFn: () => saveMedicalHistory(department, stayId, {
      ...Object.fromEntries(TEXT_FIELDS.map((f) => [f, draft[f] || null])),
      weightKg: draft.weightKg ? Number(draft.weightKg) : null,
      heightCm: draft.heightCm ? Number(draft.heightCm) : null,
    }),
    onSuccess: () => { toast.success(t('caseView.forms.saved')); qc.invalidateQueries({ queryKey }); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading || !data) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;
  const set = (k: string) => (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [k]: e.target.value }));
  const sigOf = (slot: MhSlot) =>
    slot === 'SPECIALIST' ? data.form?.specialistSignature
      : slot === 'PERMANENT' ? data.form?.permanentSignature : data.form?.residentSignature;

  return (
    <div className="space-y-4" data-testid="mh-tab">
      <dl className="grid grid-cols-2 gap-2 rounded-md border border-ink-100 bg-ink-50/50 p-3 text-sm md:grid-cols-4">
        <div><dt className="text-ink-500">{t('caseView.forms.ptName')}</dt><dd>{data.prefill.patientName}</dd></div>
        <div><dt className="text-ink-500">{t('caseView.forms.ptCode')}</dt><dd>{data.prefill.patientMrn}</dd></div>
        <div><dt className="text-ink-500">{t('caseView.forms.age')}</dt><dd>{data.prefill.ageText ?? '—'}</dd></div>
        <div><dt className="text-ink-500">{t('caseView.forms.doa')}</dt>
          <dd>{new Date(data.prefill.admittedAt).toLocaleDateString()}</dd></div>
      </dl>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <label className="text-sm">
          <span className="text-ink-600">{t('caseView.forms.mh.weightKg')}</span>
          <input data-testid="mh-weightKg" type="number" step="0.01" value={draft.weightKg} onChange={set('weightKg')}
            disabled={readOnly} className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
        </label>
        <label className="text-sm">
          <span className="text-ink-600">{t('caseView.forms.mh.heightCm')}</span>
          <input data-testid="mh-heightCm" type="number" step="0.1" value={draft.heightCm} onChange={set('heightCm')}
            disabled={readOnly} className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
        </label>
        {TEXT_FIELDS.map((f) => (
          <label key={f} className={MULTILINE.has(f) ? 'text-sm md:col-span-2' : 'text-sm'}>
            <span className="text-ink-600">{t(`caseView.forms.mh.${f}`)}</span>
            {MULTILINE.has(f) ? (
              <textarea data-testid={`mh-${f}`} value={draft[f]} onChange={set(f)} disabled={readOnly}
                rows={2} className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            ) : (
              <input data-testid={`mh-${f}`} value={draft[f]} onChange={set(f)} disabled={readOnly}
                className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            )}
          </label>
        ))}
      </div>

      {!readOnly && (
        <button data-testid="mh-save" onClick={() => save.mutate()} disabled={save.isPending}
          className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
          {t('caseView.forms.save')}
        </button>
      )}

      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        {SLOTS.map((slot) => {
          const sig = sigOf(slot);
          if (readOnly) {
            return (
              <div key={slot} className="rounded-md border border-ink-100 p-3">
                <div className="text-sm font-medium text-ink-700">{t(`caseView.forms.mh.sign.${slot}`)}</div>
                {sig?.present ? (
                  <div className="mt-1 text-xs text-ink-500" data-testid={`mh-signed-${slot}`}>
                    {sig.signerName ?? '—'} · {sig.signedAt ? new Date(sig.signedAt).toLocaleString() : ''}
                  </div>
                ) : (
                  <div className="mt-1 text-xs text-ink-400">—</div>
                )}
              </div>
            );
          }
          return (
            <SignaturePad key={slot} slot={slot} label={t(`caseView.forms.mh.sign.${slot}`)}
              meta={sig ?? { present: false }}
              upload={(blob, name) => uploadMhSignature(department, stayId, slot, blob, name)}
              fetchUrl={() => fetchMhSignatureUrl(department, stayId, slot)}
              onSaved={() => qc.invalidateQueries({ queryKey })} t={t} />
          );
        })}
      </div>
    </div>
  );
}
