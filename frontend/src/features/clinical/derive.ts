import type { PatientHistory, Prescription, Vitals } from './api';

/**
 * Derives active medications (latest non-cancelled prescription per drug, deduplicated by
 * drug id or drug name fallback). Returned in reverse chronological order.
 */
export function deriveActiveMedications(history: PatientHistory | undefined): Array<Prescription & {
  prescribedAt: string;
  prescribedByVisit: string;
  prescribedByDoctor: string;
}> {
  if (!history) return [];
  const seen = new Set<string>();
  const out: Array<Prescription & { prescribedAt: string; prescribedByVisit: string; prescribedByDoctor: string }> = [];
  // history.entries is newest first; we want freshest-per-drug
  for (const entry of history.entries) {
    if (!entry.exam) continue;
    if (entry.exam.status !== 'FINALIZED') continue;
    if (entry.status === 'CANCELLED') continue;
    for (const rx of entry.exam.prescriptions) {
      const key = rx.drugServiceItemId ?? rx.drugName.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      out.push({
        ...rx,
        prescribedAt: entry.exam.finalizedAt ?? entry.exam.createdAt,
        prescribedByVisit: entry.visitDisplayId,
        prescribedByDoctor: entry.exam.doctorName,
      });
    }
  }
  return out;
}

/**
 * Derives the problem list — all unique diagnoses across the patient's history,
 * with first-seen and last-seen dates and an occurrence count. Diagnoses with
 * 2+ occurrences are flagged as "chronic" candidates.
 */
export function deriveProblemList(history: PatientHistory | undefined): Array<{
  code: string | null;
  description: string;
  firstSeen: string;
  lastSeen: string;
  occurrences: number;
  primaryCount: number;
  chronic: boolean;
}> {
  if (!history) return [];
  type Acc = {
    code: string | null;
    description: string;
    firstSeen: string;
    lastSeen: string;
    occurrences: number;
    primaryCount: number;
  };
  const map = new Map<string, Acc>();

  // history.entries is newest first
  for (const entry of history.entries) {
    if (!entry.exam) continue;
    const when = entry.exam.finalizedAt ?? entry.exam.createdAt;
    for (const dx of entry.exam.diagnoses) {
      const key = (dx.code ?? '') + '|' + dx.description.toLowerCase().trim();
      const existing = map.get(key);
      if (existing) {
        existing.occurrences += 1;
        if (dx.primary) existing.primaryCount += 1;
        if (when < existing.firstSeen) existing.firstSeen = when;
        if (when > existing.lastSeen) existing.lastSeen = when;
      } else {
        map.set(key, {
          code: dx.code,
          description: dx.description,
          firstSeen: when,
          lastSeen: when,
          occurrences: 1,
          primaryCount: dx.primary ? 1 : 0,
        });
      }
    }
  }
  return Array.from(map.values())
    .sort((a, b) => b.lastSeen.localeCompare(a.lastSeen))
    .map((p) => ({ ...p, chronic: p.occurrences >= 2 }));
}

/**
 * Returns the last N visits that recorded any vital, newest first, with the visit
 * display id and date stamped in for chart axes.
 */
export function deriveVitalsTrend(history: PatientHistory | undefined, limit = 5): Array<{
  visitDisplayId: string;
  recordedAt: string;
  vitals: Vitals;
}> {
  if (!history) return [];
  const out: Array<{ visitDisplayId: string; recordedAt: string; vitals: Vitals }> = [];
  for (const entry of history.entries) {
    if (!entry.exam) continue;
    const v = entry.exam.vitals;
    const hasAny = v.systolicBp != null || v.heartRate != null
      || v.temperatureC != null || v.weightKg != null || v.oxygenSaturation != null;
    if (!hasAny) continue;
    out.push({
      visitDisplayId: entry.visitDisplayId,
      recordedAt: entry.exam.finalizedAt ?? entry.exam.createdAt,
      vitals: v,
    });
    if (out.length >= limit) break;
  }
  return out;
}

/** Returns the most recent results-summary entries from forwarded visits, oldest first. */
export function deriveRecentResults(history: PatientHistory | undefined, limit = 5): Array<{
  visitDisplayId: string;
  visitType: string;
  endedAt: string | null;
  summary: string;
}> {
  if (!history) return [];
  return history.entries
    .filter((e) => e.parentVisitId != null && e.resultsSummary && e.status === 'COMPLETED')
    .slice(0, limit)
    .map((e) => ({
      visitDisplayId: e.visitDisplayId,
      visitType: e.visitType,
      endedAt: e.endedAt,
      summary: e.resultsSummary as string,
    }));
}
