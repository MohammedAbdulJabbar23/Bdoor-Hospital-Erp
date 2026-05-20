/**
 * Printable routing slip for handoffs (cashier → dept, dept → dept).
 * Wrap in `.print-only` so it's hidden on-screen and shown on print.
 */
export type RoutingSlipProps = {
  patientName: string;
  patientMrn: string;
  fromLabel: string;             // e.g. "Cashier", "Doctor: Dr. Kareem"
  toLabel: string;               // e.g. "Laboratory", "Radiology"
  visitDisplayId: string;
  paymentDisplayId?: string;
  serviceLines?: { code: string; name: string; qty?: number }[];
  notes?: string;
};

export function RoutingSlip(p: RoutingSlipProps) {
  return (
    <div className="print-only mx-auto max-w-[80mm] p-4 text-[12px] leading-tight">
      <div className="mb-2 text-center">
        <div className="text-lg font-bold">Albudoor Hospital</div>
        <div className="text-[10px] tracking-wide text-ink-500">Routing slip</div>
      </div>
      <hr className="my-2 border-dashed" />
      <div className="grid grid-cols-2 gap-1">
        <div className="text-ink-500">Patient</div><div className="text-end font-medium">{p.patientName}</div>
        <div className="text-ink-500">MRN</div><div className="text-end font-mono">{p.patientMrn}</div>
        <div className="text-ink-500">Visit</div><div className="text-end font-mono">{p.visitDisplayId}</div>
        {p.paymentDisplayId && (<>
          <div className="text-ink-500">Payment</div><div className="text-end font-mono">{p.paymentDisplayId}</div>
        </>)}
      </div>
      <hr className="my-2 border-dashed" />
      <div className="text-center text-[11px]">
        <span className="text-ink-500">From</span> <strong>{p.fromLabel}</strong>
        <span className="mx-1">→</span>
        <span className="text-ink-500">To</span> <strong>{p.toLabel}</strong>
      </div>
      {p.serviceLines && p.serviceLines.length > 0 && (
        <>
          <hr className="my-2 border-dashed" />
          <div className="text-ink-500">Services</div>
          <ul className="mt-1 space-y-0.5">
            {p.serviceLines.map((l, i) => (
              <li key={i} className="flex justify-between gap-2">
                <span><span className="font-mono text-[10px] text-ink-500">{l.code}</span> {l.name}</span>
                {l.qty != null && <span>×{l.qty}</span>}
              </li>
            ))}
          </ul>
        </>
      )}
      {p.notes && (
        <>
          <hr className="my-2 border-dashed" />
          <div className="text-ink-500">Notes</div>
          <div className="mt-1 whitespace-pre-wrap">{p.notes}</div>
        </>
      )}
      <hr className="my-2 border-dashed" />
      <div className="grid grid-cols-2 gap-1 text-[10px] text-ink-500">
        <div>Printed</div><div className="text-end">{new Date().toLocaleString()}</div>
      </div>
    </div>
  );
}

/** Open a new window, render the slip into it, and print. Use for one-off printing. */
export function printRoutingSlip(props: RoutingSlipProps) {
  const w = window.open('', '_blank', 'width=400,height=600');
  if (!w) return;
  const lines = (props.serviceLines ?? [])
    .map((l) => `<li><span style="color:#64748B;font-family:monospace;font-size:10px">${escape(l.code)}</span> ${escape(l.name)}${l.qty != null ? ` ×${l.qty}` : ''}</li>`)
    .join('');
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Routing slip</title>
    <style>
      body { font-family: -apple-system, Segoe UI, sans-serif; font-size: 12px; max-width: 80mm; margin: 0 auto; padding: 16px; color: #0F172A; }
      h1 { text-align: center; font-size: 18px; margin: 0; }
      .sub { text-align: center; font-size: 10px; color: #64748B; letter-spacing: .04em; }
      hr { border: 0; border-top: 1px dashed #CBD5E1; margin: 8px 0; }
      .row { display: flex; justify-content: space-between; gap: 8px; }
      .mono { font-family: ui-monospace, monospace; }
      .muted { color: #64748B; }
    </style></head><body>
    <h1>Albudoor Hospital</h1>
    <div class="sub">ROUTING SLIP</div>
    <hr/>
    <div class="row"><span class="muted">Patient</span><strong>${escape(props.patientName)}</strong></div>
    <div class="row"><span class="muted">MRN</span><span class="mono">${escape(props.patientMrn)}</span></div>
    <div class="row"><span class="muted">Visit</span><span class="mono">${escape(props.visitDisplayId)}</span></div>
    ${props.paymentDisplayId ? `<div class="row"><span class="muted">Payment</span><span class="mono">${escape(props.paymentDisplayId)}</span></div>` : ''}
    <hr/>
    <div style="text-align:center"><span class="muted">From</span> <strong>${escape(props.fromLabel)}</strong> &rarr; <span class="muted">To</span> <strong>${escape(props.toLabel)}</strong></div>
    ${lines ? `<hr/><div class="muted">Services</div><ul style="margin:4px 0;padding-left:16px">${lines}</ul>` : ''}
    ${props.notes ? `<hr/><div class="muted">Notes</div><div style="white-space:pre-wrap">${escape(props.notes)}</div>` : ''}
    <hr/>
    <div class="row" style="color:#64748B;font-size:10px"><span>Printed</span><span>${escape(new Date().toLocaleString())}</span></div>
    <script>window.onload = () => { window.print(); setTimeout(() => window.close(), 500); };</script>
  </body></html>`);
  w.document.close();
}

function escape(s: string): string {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!));
}
