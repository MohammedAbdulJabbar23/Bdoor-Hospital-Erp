import { Bed as BedIcon, Clock, ChevronRight } from 'lucide-react';

/**
 * Shared bed tile for the premature & emergency bed dashboards, styled to look like a
 * hospital bed (headboard → pillow → mattress → blanket) using pure Tailwind.
 *
 * Contract preserved from the previous inline cards (the e2e suites depend on it):
 * - root testid `bed-<code>`; status pill testid `bed-status-<code>`
 * - empty bed  → non-interactive <div>; occupied/pending → whole card is a <button>
 *   with aria-label "<code> — <patientName>" and the onOpen click handler
 * - visible text unchanged: t(`${ns}.bedStatus.<STATUS>`), t(`${ns}.detail.empty`),
 *   occupant name / MRN, t(`${ns}.expiringSoon`)
 */

export type BedCardOccupant = {
  patientName: string;
  patientMrn: string;
  stayExpiresAt: string;
};

export type BedCardBed = {
  code: string;
  status: 'AVAILABLE' | 'PENDING_PAYMENT' | 'OCCUPIED';
  active: boolean;
  occupant: BedCardOccupant | null;
};

function isExpiringSoon(iso: string): boolean {
  const expires = new Date(iso).getTime();
  return expires - Date.now() < 2 * 60 * 60 * 1000; // within 2h
}

/** Status → palette. Pill classes match the previous dashboard exactly. */
const PALETTES = {
  AVAILABLE: {
    headboard: 'bg-emerald-300',
    blanket: 'bg-emerald-100/80 border-emerald-200/70',
    icon: 'text-emerald-600',
    pill: 'bg-emerald-50 text-emerald-700',
  },
  PENDING_PAYMENT: {
    headboard: 'bg-amber-300',
    blanket: 'bg-amber-100/80 border-amber-200/70',
    icon: 'text-amber-600',
    pill: 'bg-amber-50 text-amber-700',
  },
  OCCUPIED: {
    headboard: 'bg-brand-300',
    blanket: 'bg-brand-100/70 border-brand-200/70',
    icon: 'text-brand-600',
    pill: 'bg-brand-50 text-brand-700',
  },
  INACTIVE: {
    headboard: 'bg-ink-300',
    blanket: 'bg-ink-100 border-ink-200',
    icon: 'text-ink-400',
    pill: 'bg-ink-100 text-ink-500',
  },
} as const;

export function BedCard({
  bed, ns, onOpen, t,
}: {
  bed: BedCardBed;
  ns: 'premature' | 'emergency';
  onOpen: () => void;
  t: (k: string) => string;
}) {
  const occ = bed.occupant;
  const expiring = occ && isExpiringSoon(occ.stayExpiresAt);
  const palette = bed.active ? PALETTES[bed.status] : PALETTES.INACTIVE;

  const bedShape = (
    <>
      {/* Headboard */}
      <div className={'h-2.5 w-full rounded-t-[10px] ' + palette.headboard} />

      {/* Mattress */}
      <div className="bg-white px-3 pb-2.5 pt-2 transition group-hover:bg-brand-50/40">
        {/* Pillow */}
        <div className="h-2 w-10 rounded-full bg-ink-100 ring-1 ring-ink-200/70" />

        <div className="mt-2 flex items-center justify-between">
          <span className="flex items-center gap-1.5 font-mono text-sm font-semibold text-ink-900">
            <BedIcon size={14} className={palette.icon} /> {bed.code}
          </span>
          <span
            className={'rounded-full px-2 py-0.5 text-[11px] font-medium ' + palette.pill}
            data-testid={`bed-status-${bed.code}`}
          >
            {t(`${ns}.bedStatus.${bed.status}`)}
          </span>
        </div>

        {occ ? (
          <div className="mt-2 flex items-center justify-between gap-2">
            <div className="min-w-0">
              <div className="truncate text-xs font-medium text-ink-900">{occ.patientName}</div>
              <div className="truncate font-mono text-[11px] text-ink-500">{occ.patientMrn}</div>
            </div>
            <ChevronRight size={16} className="shrink-0 text-ink-300 group-hover:text-brand-600 rtl:rotate-180" />
          </div>
        ) : (
          <p className="mt-2 text-[11px] text-ink-400">{t(`${ns}.detail.empty`)}</p>
        )}
      </div>

      {/* Blanket */}
      <div className={'flex min-h-[14px] items-center rounded-b-[10px] border-t px-3 ' + palette.blanket}>
        {expiring && (
          <span className="my-1 inline-flex items-center gap-1 rounded bg-red-50 px-1.5 py-0.5 text-[11px] font-medium text-red-700">
            <Clock size={11} /> {t(`${ns}.expiringSoon`)}
          </span>
        )}
      </div>
    </>
  );

  // Empty bed — quiet, non-interactive tile.
  if (!occ) {
    return (
      <div
        className={
          'overflow-hidden rounded-xl border border-dashed border-ink-200 ' +
          (bed.active ? 'opacity-80' : 'opacity-50')
        }
        data-testid={`bed-${bed.code}`}
      >
        {bedShape}
      </div>
    );
  }

  // Occupied / pending — the whole bed is the clickable target, opens the detail view.
  return (
    <button
      type="button"
      onClick={onOpen}
      className={
        'group w-full overflow-hidden rounded-xl border border-ink-100 text-start shadow-card transition ' +
        'hover:border-brand-200 hover:bg-brand-50/40 focus:outline-none focus:ring-2 focus:ring-brand-200' +
        (bed.active ? '' : ' opacity-60')
      }
      data-testid={`bed-${bed.code}`}
      aria-label={`${bed.code} — ${occ.patientName}`}
    >
      {bedShape}
    </button>
  );
}
