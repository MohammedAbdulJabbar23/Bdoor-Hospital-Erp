/** Shared model for the bed-stay case page, used by both Premature and Emergency. */

export type OrderTargetType = 'LABORATORY' | 'RADIOLOGY' | 'ECO';

export const ORDER_TARGETS: OrderTargetType[] = ['LABORATORY', 'RADIOLOGY', 'ECO'];

/** One forwarded department order (a child visit) + its referral note and results. */
export type OrderView = {
  visitId: string;
  visitDisplayId: string;
  visitType: string; // LABORATORY | RADIOLOGY | ECO
  status: string;
  resultsSummary?: string | null;
  startedAt: string;
  note?: string | null;
  resultsAt?: string | null;
};

/** Normalized header/banner/billing view, mapped from a premature Admission or an emergency Case. */
export type BedStayCaseView = {
  patientId: string;
  patientName: string;
  patientMrn: string;
  visitDisplayId: string;
  bedCode: string;
  serviceName?: string | null; // emergency only
  status: string; // raw status code (e.g. UNDER_CARE / UNDER_TREATMENT / AWAITING_DISCHARGE_PAYMENT)
  stayValue: number;
  stayUnit: 'HOURS' | 'DAYS';
  admittedAt: string;
  stayExpiresAt: string;
  treatmentFinishedAt?: string | null;
  closedAt?: string | null;
  dischargeNote?: string | null;
  initialPaymentId?: string | null;
  finalPaymentId?: string | null;
};

/** The "active treatment" statuses where ordering / notes / finish are allowed. */
export function isUnderTreatment(status: string): boolean {
  return status === 'UNDER_CARE' || status === 'UNDER_TREATMENT';
}

/** Tailwind classes for a status pill, covering both departments' status codes. */
export function statusToneClass(status: string): string {
  switch (status) {
    case 'UNDER_CARE':
    case 'UNDER_TREATMENT':
      return 'bg-brand-50 text-brand-700';
    case 'TREATMENT_FINISHED':
      return 'bg-indigo-50 text-indigo-700';
    case 'AWAITING_ADMISSION_PAYMENT':
    case 'AWAITING_INITIAL_PAYMENT':
    case 'AWAITING_DISCHARGE_PAYMENT':
      return 'bg-amber-50 text-amber-700';
    case 'CLOSED':
      return 'bg-emerald-50 text-emerald-700';
    case 'CANCELLED':
      return 'bg-red-50 text-red-700';
    default:
      return 'bg-ink-100 text-ink-600';
  }
}
