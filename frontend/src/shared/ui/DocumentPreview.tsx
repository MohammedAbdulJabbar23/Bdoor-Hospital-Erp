import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X as XIcon, Download } from 'lucide-react';
import { fetchDocumentBlobUrl } from '@/features/beds/case/forms/documentsApi';

export function DocumentPreview({ fileUrl, fileName, contentType, onClose }: {
  fileUrl: string; fileName: string; contentType: string; onClose: () => void;
}) {
  const { t } = useTranslation();
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let url: string | null = null;
    fetchDocumentBlobUrl(fileUrl)
      .then((u) => { url = u; setBlobUrl(u); })
      .catch(() => setFailed(true));
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [fileUrl]);

  const isImage = contentType.startsWith('image/');
  const isPdf = contentType === 'application/pdf';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink-900/60 p-4" data-testid="doc-preview">
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-lg bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-ink-100 px-4 py-2.5">
          <span className="truncate text-sm font-medium text-ink-900">{fileName}</span>
          <div className="flex items-center gap-2">
            {blobUrl && (
              <a href={blobUrl} download={fileName} data-testid="doc-download"
                className="inline-flex items-center gap-1 rounded-md border border-ink-200 px-2 py-1 text-xs hover:bg-ink-50">
                <Download size={13} /> {t('caseView.documents.download')}
              </a>
            )}
            <button onClick={onClose} data-testid="doc-preview-close"
              className="rounded-md p-1 text-ink-500 hover:bg-ink-50"><XIcon size={16} /></button>
          </div>
        </div>
        <div className="min-h-48 flex-1 overflow-auto p-3">
          {failed && <p className="p-6 text-center text-sm text-rose-600">{t('caseView.documents.loadFailed')}</p>}
          {!failed && !blobUrl && <p className="p-6 text-center text-sm text-ink-500">{t('common.loading')}</p>}
          {blobUrl && isImage && <img src={blobUrl} alt={fileName} className="mx-auto max-h-[70vh] object-contain" />}
          {blobUrl && isPdf && <iframe src={blobUrl} title={fileName} className="h-[70vh] w-full rounded border border-ink-100" />}
          {blobUrl && !isImage && !isPdf && (
            <p className="p-6 text-center text-sm text-ink-500">{t('caseView.documents.noPreview')}</p>
          )}
        </div>
      </div>
    </div>
  );
}
