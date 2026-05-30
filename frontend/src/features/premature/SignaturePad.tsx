import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { uploadSignature, fetchSignatureUrl, type SignatureSlot, type SigMeta } from './api';

export function SignaturePad({
  admissionId, slot, label, meta, onSaved, t,
}: {
  admissionId: string; slot: SignatureSlot; label: string; meta: SigMeta;
  onSaved: () => void; t: (k: string) => string;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const [signerName, setSignerName] = useState(meta.signerName ?? '');
  const [savedUrl, setSavedUrl] = useState<string | null>(null);
  const [editing, setEditing] = useState(!meta.present);

  useEffect(() => {
    if (!meta.present) return;
    let url: string | null = null;
    fetchSignatureUrl(admissionId, slot).then((u) => { url = u; setSavedUrl(u); }).catch(() => {});
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [admissionId, slot, meta.present]);

  const pos = (e: React.PointerEvent) => {
    const r = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };
  const start = (e: React.PointerEvent) => {
    drawing.current = true;
    const ctx = canvasRef.current!.getContext('2d')!;
    ctx.beginPath();
    const { x, y } = pos(e);
    ctx.moveTo(x, y);
  };
  const move = (e: React.PointerEvent) => {
    if (!drawing.current) return;
    const ctx = canvasRef.current!.getContext('2d')!;
    ctx.lineWidth = 2; ctx.lineCap = 'round'; ctx.strokeStyle = '#0f172a';
    const { x, y } = pos(e);
    ctx.lineTo(x, y); ctx.stroke();
  };
  const end = () => { drawing.current = false; };
  const clear = () => {
    const c = canvasRef.current!;
    c.getContext('2d')!.clearRect(0, 0, c.width, c.height);
  };

  const saveMut = useMutation({
    mutationFn: () => new Promise<void>((resolve, reject) => {
      canvasRef.current!.toBlob(async (blob) => {
        if (!blob) return reject(new Error('empty'));
        try { await uploadSignature(admissionId, slot, blob, signerName.trim()); resolve(); }
        catch (e) { reject(e); }
      }, 'image/png');
    }),
    onSuccess: async () => {
      toast.success(t('premature.form.signatureSaved'));
      setSavedUrl((prev) => { if (prev) URL.revokeObjectURL(prev); return prev; });
      const url = await fetchSignatureUrl(admissionId, slot).catch(() => null);
      setSavedUrl(url); setEditing(false); onSaved();
    },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  return (
    <div className="rounded-lg border border-ink-200 p-3" data-testid={`signature-${slot}`}>
      <div className="mb-2 flex items-center justify-between">
        <span className="text-xs font-medium text-ink-700">{label}</span>
        {meta.present && !editing && (
          <button type="button" className="text-[11px] text-brand-700 hover:underline" onClick={() => setEditing(true)}>
            {t('premature.form.reSign')}
          </button>
        )}
      </div>
      {!editing && savedUrl ? (
        <div>
          <img src={savedUrl} alt={label} className="h-20 w-full rounded border border-ink-100 object-contain" />
          {meta.signerName && <p className="mt-1 text-[11px] text-ink-500">{meta.signerName}</p>}
        </div>
      ) : (
        <div className="space-y-2">
          <input
            value={signerName} onChange={(e) => setSignerName(e.target.value)}
            placeholder={t('premature.form.signerName')}
            className="w-full rounded-md border border-ink-200 px-2 py-1 text-xs"
            data-testid={`signer-${slot}`}
          />
          <canvas
            ref={canvasRef} width={300} height={90}
            className="w-full touch-none rounded border border-dashed border-ink-300 bg-white"
            onPointerDown={start} onPointerMove={move} onPointerUp={end} onPointerLeave={end}
            data-testid={`canvas-${slot}`}
          />
          <div className="flex gap-2">
            <button type="button" onClick={clear} className="rounded border border-ink-200 px-2 py-1 text-[11px] hover:bg-ink-50">
              {t('premature.form.clear')}
            </button>
            <button type="button" disabled={saveMut.isPending} onClick={() => saveMut.mutate()}
              className="rounded bg-brand-600 px-2 py-1 text-[11px] font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              data-testid={`save-sign-${slot}`}>
              {t('premature.form.saveSignature')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
