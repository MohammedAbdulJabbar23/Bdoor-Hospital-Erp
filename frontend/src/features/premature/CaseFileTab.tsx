import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { upsertCaseForm, type PrematureCase } from './api';

const FIELDS = ['wardNumber', 'nextOfKinAddress', 'nextOfKinPhone',
  'treatingSpecialist', 'initialDiagnosis', 'finalDiagnosis'] as const;
const MULTILINE = new Set(['initialDiagnosis', 'finalDiagnosis']);

export function CaseFileTab({ c, admissionId, readOnly, onSaved }: {
  c: PrematureCase; admissionId: string; readOnly: boolean; onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState<Record<string, string>>({});
  useEffect(() => {
    setDraft(Object.fromEntries(FIELDS.map((f) => [f, c.caseForm?.[f] ?? ''])));
  }, [c.caseForm]);

  const save = useMutation({
    mutationFn: () => upsertCaseForm(admissionId,
      Object.fromEntries(FIELDS.map((f) => [f, draft[f] || null]))),
    onSuccess: () => { toast.success(t('caseView.forms.saved')); onSaved(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  const a = c.admission;
  const ro: [string, string | null][] = [
    [t('caseView.forms.ptName'), a.patientName],
    [t('caseView.forms.ptCode'), a.patientMrn],
    [t('caseView.forms.caseFile.motherName'), c.caseFilePrefill.motherName],
    [t('caseView.forms.caseFile.gender'), c.caseFilePrefill.gender],
    [t('caseView.forms.age'), c.prefill.ageText],
    [t('caseView.forms.caseFile.occupation'), null],   // not held for infants in the registry
    [t('caseView.forms.caseFile.address'), null],
  ];

  return (
    <div className="space-y-4" data-testid="casefile-tab">
      <dl className="grid grid-cols-2 gap-2 rounded-md border border-ink-100 bg-ink-50/50 p-3 text-sm md:grid-cols-4">
        {ro.map(([label, value]) => (
          <div key={label}><dt className="text-ink-500">{label}</dt><dd>{value ?? '—'}</dd></div>
        ))}
      </dl>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        {FIELDS.map((f) => (
          <label key={f} className={MULTILINE.has(f) ? 'text-sm md:col-span-2' : 'text-sm'}>
            <span className="text-ink-600">{t(`caseView.forms.caseFile.${f}`)}</span>
            {MULTILINE.has(f) ? (
              <textarea data-testid={`cf-${f}`} value={draft[f] ?? ''} disabled={readOnly} rows={2}
                onChange={(e) => setDraft((d) => ({ ...d, [f]: e.target.value }))}
                className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            ) : (
              <input data-testid={`cf-${f}`} value={draft[f] ?? ''} disabled={readOnly}
                onChange={(e) => setDraft((d) => ({ ...d, [f]: e.target.value }))}
                className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            )}
          </label>
        ))}
      </div>

      {!readOnly && (
        <button data-testid="cf-save" onClick={() => save.mutate()} disabled={save.isPending}
          className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
          {t('caseView.forms.save')}
        </button>
      )}
    </div>
  );
}
