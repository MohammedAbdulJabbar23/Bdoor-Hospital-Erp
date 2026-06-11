import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { FileText, Image as ImageIcon, Upload, Archive, Eye } from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import { DocumentPreview } from '@/shared/ui/DocumentPreview';
import { archiveDocument, listDocuments, uploadDocument, type StayDoc } from './documentsApi';
import type { StayDepartment } from './api';

const SOURCE_TONE: Record<string, string> = {
  UPLOAD: 'bg-ink-100 text-ink-700',
  LABORATORY: 'bg-emerald-50 text-emerald-700',
  RADIOLOGY: 'bg-sky-50 text-sky-700',
  ECO: 'bg-violet-50 text-violet-700',
};

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function DocumentsTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-docs', department, stayId];
  const { data: docs, isLoading } = useQuery({ queryKey, queryFn: () => listDocuments(department, stayId) });

  const fileRef = useRef<HTMLInputElement>(null);
  const [label, setLabel] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [preview, setPreview] = useState<StayDoc | null>(null);

  const upload = useMutation({
    mutationFn: (file: File) => uploadDocument(department, stayId, file, label.trim() || undefined),
    onSuccess: () => {
      toast.success(t('caseView.documents.uploaded'));
      setLabel('');
      if (fileRef.current) fileRef.current.value = '';
      qc.invalidateQueries({ queryKey });
    },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  const archive = useMutation({
    mutationFn: (id: string) => archiveDocument(department, stayId, id),
    onSuccess: () => { toast.success(t('caseView.documents.archived')); qc.invalidateQueries({ queryKey }); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;
  const visible = (docs ?? []).filter((d) => showArchived || !d.archived);
  const archivedCount = (docs ?? []).filter((d) => d.archived).length;

  return (
    <div className="space-y-4" data-testid="documents-tab">
      {!readOnly && (
        <div className="flex flex-wrap items-end gap-2 rounded-md border border-ink-100 p-3">
          <label className="text-sm">
            <span className="text-ink-600">{t('caseView.documents.labelField')}</span>
            <input data-testid="doc-label" value={label} onChange={(e) => setLabel(e.target.value)}
              className="mt-1 w-56 rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <input ref={fileRef} data-testid="doc-file" type="file" accept="image/*,application/pdf"
            className="text-sm" onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); }} />
          <span className="inline-flex items-center gap-1 text-xs text-ink-400">
            <Upload size={12} /> {t('caseView.documents.policy')}
          </span>
        </div>
      )}

      <ul className="divide-y divide-ink-50" data-testid="doc-rows">
        {visible.map((d) => (
          <li key={d.id} className={`flex items-center gap-3 px-2 py-2.5 ${d.archived ? 'opacity-50' : ''}`}>
            {d.contentType.startsWith('image/')
              ? <ImageIcon size={18} className="shrink-0 text-ink-400" />
              : <FileText size={18} className="shrink-0 text-ink-400" />}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="truncate text-sm font-medium text-ink-900">{d.fileName}</span>
                <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${SOURCE_TONE[d.source]}`}>
                  {t(`caseView.documents.source.${d.source}`)}
                </span>
                {d.label && <span className="truncate text-xs text-ink-500">{d.label}</span>}
              </div>
              <div className="text-xs text-ink-400">
                {new Date(d.uploadedAt).toLocaleString()} · {fmtSize(d.sizeBytes)}
              </div>
            </div>
            <button onClick={() => setPreview(d)} data-testid={`doc-view-${d.fileName}`}
              className="inline-flex items-center gap-1 rounded-md border border-ink-200 px-2 py-1 text-xs hover:bg-ink-50">
              <Eye size={13} /> {t('caseView.documents.view')}
            </button>
            {!readOnly && d.source === 'UPLOAD' && !d.archived && (
              <button data-testid={`doc-archive-${d.fileName}`}
                onClick={() => { if (window.confirm(t('caseView.documents.archiveConfirm'))) archive.mutate(d.id); }}
                className="inline-flex items-center gap-1 rounded-md border border-ink-200 px-2 py-1 text-xs text-ink-500 hover:bg-ink-50">
                <Archive size={13} /> {t('caseView.documents.archive')}
              </button>
            )}
          </li>
        ))}
        {visible.length === 0 && (
          <li className="px-2 py-6 text-center text-sm text-ink-400">{t('caseView.documents.empty')}</li>
        )}
      </ul>

      {archivedCount > 0 && (
        <button data-testid="doc-toggle-archived" onClick={() => setShowArchived((v) => !v)}
          className="text-xs text-ink-500 hover:text-ink-900 hover:underline">
          {showArchived ? t('caseView.documents.hideArchived') : t('caseView.documents.showArchived', { count: archivedCount })}
        </button>
      )}

      {preview && (
        <DocumentPreview fileUrl={preview.fileUrl} fileName={preview.fileName}
          contentType={preview.contentType} onClose={() => setPreview(null)} />
      )}
    </div>
  );
}
