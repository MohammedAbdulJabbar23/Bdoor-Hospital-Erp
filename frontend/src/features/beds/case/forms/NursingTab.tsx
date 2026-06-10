import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { addNursingProcedure, listNursingProcedures, type StayDepartment } from './api';

export function NursingTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-nursing', department, stayId];
  const { data: rows, isLoading } = useQuery({ queryKey, queryFn: () => listNursingProcedures(department, stayId) });

  const [procedureName, setProcedureName] = useState('');
  const [performedAt, setPerformedAt] = useState(() => new Date().toISOString().slice(0, 16));
  const [note, setNote] = useState('');

  const add = useMutation({
    mutationFn: () => addNursingProcedure(department, stayId, {
      procedureName,
      performedAt: new Date(performedAt).toISOString(),
      note: note || undefined,
    }),
    onSuccess: () => {
      toast.success(t('caseView.forms.saved'));
      setProcedureName(''); setNote('');
      qc.invalidateQueries({ queryKey });
    },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;

  return (
    <div className="space-y-4" data-testid="nursing-tab">
      {!readOnly && (
        <div className="flex flex-wrap items-end gap-2 rounded-md border border-ink-100 p-3">
          <label className="text-sm">
            <span className="text-ink-600">{t('caseView.forms.nursing.procedureName')}</span>
            <input data-testid="nursing-procedureName" value={procedureName}
              onChange={(e) => setProcedureName(e.target.value)}
              className="mt-1 w-56 rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <label className="text-sm">
            <span className="text-ink-600">{t('caseView.forms.nursing.performedAt')}</span>
            <input data-testid="nursing-performedAt" type="datetime-local" value={performedAt}
              onChange={(e) => setPerformedAt(e.target.value)}
              className="mt-1 rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <label className="flex-1 text-sm">
            <span className="text-ink-600">{t('caseView.forms.nursing.note')}</span>
            <input data-testid="nursing-note" value={note} onChange={(e) => setNote(e.target.value)}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <button data-testid="nursing-add" onClick={() => add.mutate()}
            disabled={add.isPending || !procedureName.trim()}
            className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
            {t('caseView.forms.nursing.add')}
          </button>
        </div>
      )}

      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-ink-100 text-start text-xs uppercase text-ink-500">
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.procedureName')}</th>
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.nurse')}</th>
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.performedAt')}</th>
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.note')}</th>
          </tr>
        </thead>
        <tbody data-testid="nursing-rows">
          {(rows ?? []).map((r) => (
            <tr key={r.id} className="border-b border-ink-50">
              <td className="px-2 py-1.5">{r.procedureName}</td>
              <td className="px-2 py-1.5">{r.nurseName ?? '—'}</td>
              <td className="px-2 py-1.5">{new Date(r.performedAt).toLocaleString()}</td>
              <td className="px-2 py-1.5">{r.note ?? '—'}</td>
            </tr>
          ))}
          {(rows ?? []).length === 0 && (
            <tr><td colSpan={4} className="px-2 py-4 text-center text-ink-400">{t('caseView.forms.nursing.empty')}</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
