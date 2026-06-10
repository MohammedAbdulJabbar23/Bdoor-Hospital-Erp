import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { SignaturePad } from '@/shared/ui/SignaturePad';
import {
  listTreatmentCharts, saveTreatmentChart, uploadChartSignature, fetchChartSignatureUrl,
  type StayDepartment, type TreatmentChart,
} from './api';

type DraftRow = { medicineName: string; dose: string; frequency: string; timing: string[] };

const emptyRow = (): DraftRow => ({ medicineName: '', dose: '', frequency: '', timing: ['', '', '', '', '', ''] });
const TIMING_HEAD = ['AM', 'AM', 'PM', 'PM', 'PM', 'AM'];

function toDraft(chart: TreatmentChart | undefined): DraftRow[] {
  if (!chart || chart.rows.length === 0) return [emptyRow()];
  return chart.rows.map((r) => ({
    medicineName: r.medicineName,
    dose: r.dose ?? '',
    frequency: r.frequency ?? '',
    timing: Array.from({ length: 6 }, (_, i) => r.timing[i] ?? ''),
  }));
}

export function TreatmentTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-charts', department, stayId];
  const { data: charts, isLoading } = useQuery({ queryKey, queryFn: () => listTreatmentCharts(department, stayId) });

  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const chart = (charts ?? []).find((c) => c.chartDate === date);
  const [rows, setRows] = useState<DraftRow[]>(toDraft(undefined));
  useEffect(() => { setRows(toDraft(chart)); }, [date, charts]);   // eslint-disable-line react-hooks/exhaustive-deps

  const save = useMutation({
    mutationFn: () => saveTreatmentChart(department, stayId, date,
      rows.filter((r) => r.medicineName.trim()).map((r) => ({
        medicineName: r.medicineName.trim(),
        dose: r.dose || undefined,
        frequency: r.frequency || undefined,
        timing: r.timing.map((v) => v || null),
      }))),
    onSuccess: () => { toast.success(t('caseView.forms.saved')); qc.invalidateQueries({ queryKey }); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;
  const setRow = (i: number, patch: Partial<DraftRow>) =>
    setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const setTiming = (i: number, k: number, v: string) =>
    setRows((rs) => rs.map((r, j) => (j === i ? { ...r, timing: r.timing.map((x, m) => (m === k ? v : x)) } : r)));

  return (
    <div className="space-y-3" data-testid="treatment-tab">
      <div className="flex items-center gap-3">
        <label className="text-sm">
          <span className="text-ink-600">{t('caseView.forms.treatment.date')}</span>
          <input data-testid="tc-date" type="date" value={date} onChange={(e) => setDate(e.target.value)}
            className="ms-2 rounded-md border border-ink-200 px-2 py-1.5" />
        </label>
        {chart?.doctorSignature.present && (
          <span className="text-xs text-ink-500">
            {t('caseView.forms.treatment.signedBy')} {chart.doctorSignature.signerName ?? '—'}
          </span>
        )}
      </div>

      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-ink-100 text-xs uppercase text-ink-500">
            <th className="px-1 py-1.5 text-start">#</th>
            <th className="px-1 py-1.5 text-start">{t('caseView.forms.treatment.medicine')}</th>
            <th className="px-1 py-1.5 text-start">{t('caseView.forms.treatment.dose')}</th>
            <th className="px-1 py-1.5 text-start">{t('caseView.forms.treatment.freq')}</th>
            {TIMING_HEAD.map((h, i) => <th key={i} className="px-1 py-1.5">{h}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-b border-ink-50">
              <td className="px-1 py-1 text-ink-400">{i + 1}</td>
              <td className="px-1 py-1">
                <input data-testid={`tc-row-${i}-medicine`} value={r.medicineName} disabled={readOnly}
                  onChange={(e) => setRow(i, { medicineName: e.target.value })}
                  className="w-full rounded-md border border-ink-200 px-2 py-1" />
              </td>
              <td className="px-1 py-1">
                <input data-testid={`tc-row-${i}-dose`} value={r.dose} disabled={readOnly}
                  onChange={(e) => setRow(i, { dose: e.target.value })}
                  className="w-24 rounded-md border border-ink-200 px-2 py-1" />
              </td>
              <td className="px-1 py-1">
                <input data-testid={`tc-row-${i}-freq`} value={r.frequency} disabled={readOnly}
                  onChange={(e) => setRow(i, { frequency: e.target.value })}
                  className="w-20 rounded-md border border-ink-200 px-2 py-1" />
              </td>
              {r.timing.map((v, k) => (
                <td key={k} className="px-0.5 py-1">
                  <input value={v} disabled={readOnly} onChange={(e) => setTiming(i, k, e.target.value)}
                    className="w-12 rounded-md border border-ink-200 px-1 py-1 text-center" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {!readOnly && (
        <div className="flex gap-2">
          <button data-testid="tc-add-row" onClick={() => setRows((rs) => [...rs, emptyRow()])}
            className="rounded-md border border-ink-200 px-3 py-1.5 text-sm hover:bg-ink-50">
            {t('caseView.forms.treatment.addRow')}
          </button>
          <button data-testid="tc-save" onClick={() => save.mutate()} disabled={save.isPending}
            className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
            {t('caseView.forms.save')}
          </button>
        </div>
      )}

      {!readOnly && chart && (
        <div className="max-w-sm">
          <SignaturePad slot="DOCTOR" label={t('caseView.forms.treatment.doctorSign')}
            meta={chart.doctorSignature}
            upload={(blob, name) => uploadChartSignature(department, stayId, date, blob, name)}
            fetchUrl={() => fetchChartSignatureUrl(department, stayId, date)}
            onSaved={() => qc.invalidateQueries({ queryKey })} t={t} />
        </div>
      )}
    </div>
  );
}
