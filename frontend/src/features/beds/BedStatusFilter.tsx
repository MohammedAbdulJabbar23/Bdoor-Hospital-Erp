export type BedFilter = 'ALL' | 'AVAILABLE' | 'PENDING_PAYMENT' | 'OCCUPIED';

const OPTIONS: { value: BedFilter; key: string; testid: string }[] = [
  { value: 'ALL', key: 'all', testid: 'bed-filter-all' },
  { value: 'AVAILABLE', key: 'available', testid: 'bed-filter-available' },
  { value: 'PENDING_PAYMENT', key: 'pending', testid: 'bed-filter-pending' },
  { value: 'OCCUPIED', key: 'occupied', testid: 'bed-filter-occupied' },
];

/**
 * Segmented status filter for a bed dashboard. Purely presentational — filtering happens
 * client-side in the parent over the already-fetched beds. Default selection should be ALL
 * so every bed stays visible (the workspace e2e suite relies on this).
 */
export function BedStatusFilter({
  ns, value, counts, onChange, t,
}: {
  ns: 'premature' | 'emergency';
  value: BedFilter;
  counts: Record<BedFilter, number>;
  onChange: (next: BedFilter) => void;
  t: (k: string) => string;
}) {
  return (
    <div className="inline-flex flex-wrap items-center gap-1 rounded-lg bg-ink-50 p-0.5">
      {OPTIONS.map((opt) => {
        const active = value === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => onChange(opt.value)}
            aria-pressed={active}
            data-testid={opt.testid}
            className={
              'rounded-md px-2.5 py-1 text-xs font-medium transition ' +
              (active ? 'bg-white text-ink-900 shadow-sm' : 'text-ink-500 hover:text-ink-900')
            }
          >
            {t(`${ns}.filter.${opt.key}`)} ({counts[opt.value]})
          </button>
        );
      })}
    </div>
  );
}
