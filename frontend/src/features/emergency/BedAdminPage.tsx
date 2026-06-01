import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { listBeds, createBed, updateBed } from './api';

export function BedAdminPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [code, setCode] = useState('');
  const [room, setRoom] = useState('');

  const { data: beds } = useQuery({ queryKey: ['emerg-beds-admin'], queryFn: listBeds });

  const refresh = () => qc.invalidateQueries({ queryKey: ['emerg-beds-admin'] });

  const createMut = useMutation({
    mutationFn: () => createBed({ code: code.trim(), room: room.trim() || undefined }),
    onSuccess: async () => { toast.success(t('emergency.toast.bedCreated')); setCode(''); setRoom(''); await refresh(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('emergency.toast.error')),
  });

  const toggleMut = useMutation({
    mutationFn: ({ id, room, active }: { id: string; room: string | null; active: boolean }) =>
      updateBed(id, { room: room ?? undefined, active }),
    onSuccess: async () => { await refresh(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('emergency.toast.error')),
  });

  return (
    <div className="space-y-6 p-1">
      <h1 className="text-lg font-semibold text-ink-900">{t('emergency.bedAdmin')}</h1>

      <div className="flex flex-wrap items-end gap-2 rounded-xl border border-ink-100 bg-white p-4">
        <div>
          <label className="block text-xs font-medium text-ink-700">{t('emergency.bedCode')}</label>
          <input value={code} onChange={(e) => setCode(e.target.value)}
            className="mt-1 rounded-md border border-ink-200 px-2 py-1.5 text-sm" data-testid="bed-code-input" />
        </div>
        <div>
          <label className="block text-xs font-medium text-ink-700">{t('emergency.room')}</label>
          <input value={room} onChange={(e) => setRoom(e.target.value)}
            className="mt-1 rounded-md border border-ink-200 px-2 py-1.5 text-sm" data-testid="bed-room-input" />
        </div>
        <button type="button" disabled={!code.trim() || createMut.isPending} onClick={() => createMut.mutate()}
          className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          data-testid="bed-create">
          {t('emergency.addBed')}
        </button>
      </div>

      <table className="w-full rounded-xl border border-ink-100 bg-white text-sm">
        <thead className="border-b border-ink-100 text-[11px] uppercase text-ink-500">
          <tr><th className="px-4 py-2 text-start">{t('emergency.bedCode')}</th>
            <th className="px-4 py-2 text-start">{t('emergency.room')}</th>
            <th className="px-4 py-2 text-start">{t('emergency.status')}</th>
            <th className="px-4 py-2 text-start">{t('common.actions')}</th></tr>
        </thead>
        <tbody className="divide-y divide-ink-100">
          {(beds ?? []).map((b) => (
            <tr key={b.id} data-testid={`admin-bed-${b.code}`}>
              <td className="px-4 py-2 font-mono">{b.code}</td>
              <td className="px-4 py-2">{b.room ?? '—'}</td>
              <td className="px-4 py-2">{t(`emergency.bedStatus.${b.status}`)} {b.active ? '' : `(${t('emergency.inactive')})`}</td>
              <td className="px-4 py-2">
                <button type="button"
                  onClick={() => toggleMut.mutate({ id: b.id, room: b.room, active: !b.active })}
                  className="rounded border border-ink-200 px-2 py-1 text-xs hover:bg-ink-50">
                  {b.active ? t('emergency.deactivate') : t('emergency.activate')}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
