import type { DeptCase } from '@/features/clinical/api';

/**
 * Standalone print window for a finalized lab/radiology/ECO case. Opens in a child
 * window so the print uses an A4 layout without polluting the SPA's print styles.
 */
export function printResults(c: DeptCase, patient: { name: string; mrn: string }, doctor?: string) {
  const w = window.open('', '_blank', 'width=900,height=700');
  if (!w) return;
  const lines = c.services.map((s) => `
    <tr>
      <td><strong>${escape(s.name)}</strong><div class="muted">${escape(s.code)}</div>${(s as any).bodyRegion ? `<div class="muted">Region: ${escape((s as any).bodyRegion)}</div>` : ''}</td>
      <td>${s.numericValue != null ? escape(String(s.numericValue)) : '—'}${s.unit ? ' ' + escape(s.unit) : ''}</td>
      <td>${s.referenceRange ? escape(s.referenceRange) : '—'}</td>
      <td>${s.flag ? `<span class="flag-${(s.flag || '').toLowerCase()}">${escape(s.flag)}</span>` : ''}</td>
    </tr>
    ${s.textFindings ? `<tr><td colspan="4" class="findings"><strong>Findings:</strong> ${escape(s.textFindings)}</td></tr>` : ''}
    ${s.measurements ? `<tr><td colspan="4" class="findings"><strong>Measurements:</strong> ${escape(s.measurements)}</td></tr>` : ''}
    ${s.diagnosis ? `<tr><td colspan="4" class="findings"><strong>Diagnosis:</strong> ${escape(s.diagnosis)}</td></tr>` : ''}
    ${s.comments ? `<tr><td colspan="4" class="findings"><strong>Comments:</strong> ${escape(s.comments)}</td></tr>` : ''}
    ${s.uploadedAt ? `<tr><td colspan="4" class="upmeta">Uploaded ${escape(new Date(s.uploadedAt).toLocaleString())}${s.uploadedBy ? ` · by user ${escape(String(s.uploadedBy).slice(0, 8))}…` : ''}</td></tr>` : ''}
  `).join('');

  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${escape(c.category)} results — ${escape(patient.name)}</title>
    <style>
      body { font-family: -apple-system, Segoe UI, sans-serif; font-size: 13px; max-width: 800px; margin: 0 auto; padding: 32px; color: #0F172A; }
      h1 { margin: 0; font-size: 22px; }
      .hospital { text-align: center; border-bottom: 2px solid #0F172A; padding-bottom: 12px; margin-bottom: 16px; }
      .header { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; margin: 16px 0 24px; }
      .header > div > span { color: #64748B; display: block; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; }
      .header > div > strong { font-size: 14px; }
      table { width: 100%; border-collapse: collapse; }
      th, td { border-bottom: 1px solid #E2E8F0; padding: 10px 8px; text-align: start; vertical-align: top; }
      th { background: #F8FAFC; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; color: #475569; }
      .muted { color: #64748B; font-size: 11px; font-family: ui-monospace, monospace; }
      .findings { background: #F8FAFC; font-size: 12px; padding-left: 18px; }
      .upmeta { font-size: 10px; color: #64748B; font-style: italic; padding-left: 18px; }
      .flag-h { color: #C8102E; font-weight: 600; }
      .flag-l { color: #2563EB; font-weight: 600; }
      .flag-crit { color: #B91C1C; font-weight: 700; }
      .footer { margin-top: 32px; padding-top: 12px; border-top: 1px solid #E2E8F0; font-size: 11px; color: #64748B; display: flex; justify-content: space-between; }
      @media print {
        body { padding: 16px; }
      }
    </style></head><body>
    <div class="hospital">
      <h1>Albudoor Hospital</h1>
      <div>${escape(categoryTitle(c.category))} report</div>
    </div>
    <div class="header">
      <div><span>Patient</span><strong>${escape(patient.name)}</strong></div>
      <div><span>MRN</span><strong style="font-family:ui-monospace,monospace">${escape(patient.mrn)}</strong></div>
      <div><span>Visit</span><strong style="font-family:ui-monospace,monospace">${escape(c.visitDisplayId)}</strong></div>
      <div><span>Case opened</span><strong>${escape(new Date(c.createdAt).toLocaleString())}</strong></div>
      ${doctor ? `<div><span>Requesting clinician</span><strong>${escape(doctor)}</strong></div>` : ''}
      ${c.finalizedAt ? `<div><span>Finalized</span><strong>${escape(new Date(c.finalizedAt).toLocaleString())}</strong></div>` : ''}
    </div>
    <table>
      <thead><tr>
        <th>Test</th><th>Result</th><th>Reference</th><th>Flag</th>
      </tr></thead>
      <tbody>${lines}</tbody>
    </table>
    ${c.resultsSummary ? `<div style="margin-top:24px"><strong>Summary</strong><div style="white-space:pre-wrap;margin-top:6px">${escape(c.resultsSummary)}</div></div>` : ''}
    <div class="footer">
      <span>Printed ${escape(new Date().toLocaleString())}</span>
      <span>Albudoor Hospital · Iraq</span>
    </div>
    <script>window.onload = () => { window.print(); };</script>
  </body></html>`);
  w.document.close();
}

function categoryTitle(c: string): string {
  switch (c) {
    case 'LAB': return 'Laboratory';
    case 'IMAGING': return 'Radiology';
    case 'ECO': return 'Echocardiography';
    case 'EMERGENCY': return 'Emergency';
    default: return c;
  }
}

function escape(s: string): string {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!));
}
