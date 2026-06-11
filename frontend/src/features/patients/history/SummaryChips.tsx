import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Activity, BedDouble, FileText, Clock, type LucideIcon } from 'lucide-react';
import type { PatientHistory } from '@/features/clinical/api';

function Chip({ testid, icon: Icon, label, value }: {
  testid: string; icon: LucideIcon; label: string; value: string | number;
}) {
  return (
    <div data-testid={testid} className="flex items-center gap-2.5 rounded-lg border border-ink-100 bg-white px-4 py-2.5">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
        <Icon size={15} />
      </span>
      <div>
        <div className="text-[10px] font-semibold uppercase tracking-wide text-ink-500">{label}</div>
        <div className="text-sm font-semibold text-ink-900">{value}</div>
      </div>
    </div>
  );
}

export function SummaryChips({ history }: { history: PatientHistory | undefined }) {
  const { t, i18n } = useTranslation();
  const dt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { day: '2-digit', month: 'short', year: 'numeric' }),
    [i18n.language],
  );

  const timeline = history?.timeline ?? [];
  const admissions = new Set(
    timeline.filter((e) => e.type === 'ADMISSION' && e.refs.stayId).map((e) => e.refs.stayId),
  ).size;
  const documents = timeline.filter((e) => e.type === 'DOCUMENT').length;
  const lastAt = timeline.reduce<string | null>((max, e) => (max == null || e.at > max ? e.at : max), null);

  return (
    <div className="flex flex-wrap gap-2 print-hide">
      <Chip testid="chip-visits" icon={Activity} label={t('patientProfile.timeline.chips.visits')} value={history?.totalVisits ?? 0} />
      <Chip testid="chip-admissions" icon={BedDouble} label={t('patientProfile.timeline.chips.admissions')} value={admissions} />
      <Chip testid="chip-documents" icon={FileText} label={t('patientProfile.timeline.chips.documents')} value={documents} />
      <Chip testid="chip-lastseen" icon={Clock} label={t('patientProfile.timeline.chips.lastSeen')} value={lastAt ? dt.format(new Date(lastAt)) : '—'} />
    </div>
  );
}
