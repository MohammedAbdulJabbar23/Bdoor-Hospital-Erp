import { useEffect, useState } from 'react';
import { Trash2, Plus } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';
import { ScheduleBlock } from './api';
import { DayOfWeek, WeeklyHour } from '@/features/reception/appointments/api';

const DAYS: DayOfWeek[] = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
const DAY_LABEL: Record<DayOfWeek, string> = {
  SUNDAY: 'Sun', MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat',
};

function trimSeconds(t: string) {
  return t.length > 5 ? t.slice(0, 5) : t;
}

export function ScheduleEditor({
  initial,
  onSave,
  saving,
}: {
  initial: WeeklyHour[];
  onSave: (blocks: ScheduleBlock[]) => void;
  saving: boolean;
}) {
  const [blocks, setBlocks] = useState<ScheduleBlock[]>([]);

  useEffect(() => {
    setBlocks(
      initial.map((h) => ({
        dayOfWeek: h.dayOfWeek,
        startTime: trimSeconds(h.startTime),
        endTime: trimSeconds(h.endTime),
        slotMinutes: h.slotMinutes,
      })),
    );
  }, [initial]);

  const update = (i: number, patch: Partial<ScheduleBlock>) =>
    setBlocks((bs) => bs.map((b, idx) => (idx === i ? { ...b, ...patch } : b)));

  const remove = (i: number) => setBlocks((bs) => bs.filter((_, idx) => idx !== i));

  const add = () =>
    setBlocks((bs) => [
      ...bs,
      { dayOfWeek: 'SUNDAY', startTime: '09:00', endTime: '13:00', slotMinutes: 15 },
    ]);

  return (
    <div className="space-y-3">
      {blocks.length === 0 ? (
        <p className="rounded-lg border border-dashed border-ink-200 p-4 text-center text-sm text-ink-500">
          No working hours defined. Add at least one block.
        </p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-ink-200">
          <table className="w-full text-sm">
            <thead className="bg-ink-50/60 text-[11px] font-semibold uppercase tracking-wide text-ink-500">
              <tr>
                <th className="px-3 py-2 text-start">Day</th>
                <th className="px-3 py-2 text-start">Start</th>
                <th className="px-3 py-2 text-start">End</th>
                <th className="px-3 py-2 text-start">Slot (min)</th>
                <th className="px-3 py-2 text-end" />
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-100">
              {blocks.map((b, i) => (
                <tr key={i}>
                  <td className="px-3 py-2">
                    <Select value={b.dayOfWeek} onChange={(e) => update(i, { dayOfWeek: e.target.value as DayOfWeek })}>
                      {DAYS.map((d) => <option key={d} value={d}>{DAY_LABEL[d]}</option>)}
                    </Select>
                  </td>
                  <td className="px-3 py-2"><Input type="time" value={b.startTime} onChange={(e) => update(i, { startTime: e.target.value })} /></td>
                  <td className="px-3 py-2"><Input type="time" value={b.endTime} onChange={(e) => update(i, { endTime: e.target.value })} /></td>
                  <td className="px-3 py-2"><Input type="number" min={5} max={240} step={5} value={b.slotMinutes} onChange={(e) => update(i, { slotMinutes: Number(e.target.value) })} /></td>
                  <td className="px-3 py-2 text-end">
                    <button type="button" onClick={() => remove(i)} className="rounded-md p-1.5 text-ink-500 hover:bg-brand-50 hover:text-brand-700" aria-label="Remove">
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex items-center justify-between">
        <Button type="button" variant="secondary" size="sm" onClick={add}>
          <Plus size={14} className="me-1.5" /> Add block
        </Button>
        <Button type="button" size="sm" onClick={() => onSave(blocks)} disabled={saving || blocks.length === 0}>
          {saving ? 'Saving…' : 'Save schedule'}
        </Button>
      </div>
    </div>
  );
}
