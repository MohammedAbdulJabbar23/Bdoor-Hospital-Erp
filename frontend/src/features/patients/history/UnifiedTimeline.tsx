import { useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Inbox, ChevronRight, ChevronDown, Eye } from 'lucide-react';
import { DocumentPreview } from '@/shared/ui/DocumentPreview';
import { cn } from '@/shared/ui/cn';
import type { TimelineEntry } from '@/features/clinical/api';

type FilterKey = 'all' | 'visits' | 'admissions' | 'forms' | 'documents';

const FILTER_TYPE: Record<Exclude<FilterKey, 'all'>, TimelineEntry['type']> = {
  visits: 'VISIT', admissions: 'ADMISSION', forms: 'FORM', documents: 'DOCUMENT',
};

function dotColor(department: string): string {
  if (department === 'PREMATURE') return 'bg-brand-500';
  if (department === 'EMERGENCY') return 'bg-amber-500';
  return 'bg-ink-400';
}

function admissionLink(e: TimelineEntry): string | null {
  if (!e.refs.stayId) return null;
  if (e.department === 'PREMATURE') return `/premature/admissions/${e.refs.stayId}`;
  if (e.department === 'EMERGENCY') return `/emergency/cases/${e.refs.stayId}`;
  return null;
}

export function UnifiedTimeline({ timeline, renderExamFor }: {
  timeline: TimelineEntry[];
  renderExamFor?: (visitId: string) => ReactNode;
}) {
  const { t, i18n } = useTranslation();
  const [filter, setFilter] = useState<FilterKey>('all');
  const [expanded, setExpanded] = useState<number | null>(null);
  const [preview, setPreview] = useState<{ fileUrl: string; fileName: string; contentType: string } | null>(null);

  const dtt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
    [i18n.language],
  );

  const counts = useMemo(() => {
    const c: Record<FilterKey, number> = { all: timeline.length, visits: 0, admissions: 0, forms: 0, documents: 0 };
    for (const e of timeline) {
      if (e.type === 'VISIT') c.visits++;
      else if (e.type === 'ADMISSION') c.admissions++;
      else if (e.type === 'FORM') c.forms++;
      else if (e.type === 'DOCUMENT') c.documents++;
    }
    return c;
  }, [timeline]);

  const visible = useMemo(
    () => (filter === 'all' ? timeline : timeline.filter((e) => e.type === FILTER_TYPE[filter])),
    [timeline, filter],
  );

  return (
    <div className="rounded-lg border border-ink-100 bg-white">
      <div className="flex flex-wrap items-center gap-2 border-b border-ink-100 bg-ink-50/40 px-4 py-3 text-xs print-hide">
        {(['all', 'visits', 'admissions', 'forms', 'documents'] as FilterKey[]).map((key) => (
          <button
            key={key} type="button" data-testid={`timeline-filter-${key}`}
            onClick={() => { setFilter(key); setExpanded(null); }}
            className={cn(
              'inline-flex h-7 items-center gap-1 rounded-full px-3 font-medium transition-colors',
              filter === key ? 'bg-brand-600 text-white' : 'border border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
            )}
          >
            {t(`patientProfile.timeline.filters.${key}`)}
            <span className={cn('font-mono text-[10px]', filter === key ? 'text-white/80' : 'text-ink-400')}>{counts[key]}</span>
          </button>
        ))}
      </div>

      {visible.length === 0 ? (
        <div className="px-5 py-10 text-center">
          <Inbox size={28} className="mx-auto text-ink-300" />
          <p className="mt-2 text-sm text-ink-600">{t('patientProfile.timeline.empty')}</p>
        </div>
      ) : (
        <ol className="divide-y divide-ink-100">
          {visible.map((e, i) => {
            const link = e.type === 'ADMISSION' ? admissionLink(e) : null;
            const examNode = e.type === 'VISIT' && renderExamFor && e.refs.visitId ? renderExamFor(e.refs.visitId) : null;
            const isOpen = expanded === i;
            return (
              <li key={`${e.at}-${e.type}-${i}`} data-testid={`timeline-entry-${e.type}`} className="px-4 py-3">
                <div className="flex items-start gap-3">
                  <span className={cn('mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full', dotColor(e.department))} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm">
                      <span className="font-medium text-ink-900">{e.title}</span>
                      <span className="text-[11px] text-ink-500">{dtt.format(new Date(e.at))}</span>
                    </div>
                    {e.detail && <div className="mt-0.5 whitespace-pre-line text-xs text-ink-600">{e.detail}</div>}
                    {isOpen && examNode && (
                      <div className="mt-2 rounded-lg border border-ink-200 bg-white p-3">{examNode}</div>
                    )}
                  </div>
                  {link && (
                    <Link to={link} className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-brand-700 hover:underline">
                      {t('patientProfile.timeline.view')} <ChevronRight size={12} className="rtl:rotate-180" />
                    </Link>
                  )}
                  {e.type === 'DOCUMENT' && e.refs.fileUrl && (
                    <button
                      type="button"
                      onClick={() => {
                        const filename = e.title.split(' — ')[0].trim();
                        return setPreview({
                          fileUrl: e.refs.fileUrl!,
                          fileName: filename,
                          // Content type guessed from filename extension (pdf vs image).
                          contentType: filename.toLowerCase().endsWith('.pdf') ? 'application/pdf' : 'image/png',
                        });
                      }}
                      className="inline-flex shrink-0 items-center gap-1 rounded-md border border-ink-200 px-2 py-1 text-xs font-medium text-ink-700 hover:bg-ink-50"
                    >
                      <Eye size={12} /> {t('patientProfile.timeline.view')}
                    </button>
                  )}
                  {examNode && (
                    <button
                      type="button"
                      onClick={() => setExpanded((v) => (v === i ? null : i))}
                      className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-brand-700 hover:underline"
                    >
                      {t('patientProfile.timeline.view')}
                      <ChevronDown size={12} className={cn('transition-transform', isOpen && 'rotate-180')} />
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}

      {preview && <DocumentPreview {...preview} onClose={() => setPreview(null)} />}
    </div>
  );
}
