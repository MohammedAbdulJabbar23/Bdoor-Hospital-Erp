import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  ListOrdered, FlaskConical, Scan, HeartPulse, ChevronRight, Plus, X,
  CheckCircle2, Receipt, Clock, ArrowLeft, Printer, type LucideIcon,
} from 'lucide-react';
import { printResults } from '@/shared/print/ResultsPrintout';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';
import { extractApiError } from '@/shared/api/client';
import { listItems } from '@/features/admin/catalogues/api';
import {
  listCases, openCase, uploadFindings, finalizeCase, listIncomingVisits,
  listCaseAttachments, uploadAttachment, deleteAttachment, attachmentDownloadUrl,
  CaseStatus, DepartmentCase, DepartmentCategory, CaseLine,
} from './api';
import type { Visit } from '@/features/reception/visits/api';
import { cn } from '@/shared/ui/cn';

type Config = {
  category: DepartmentCategory;
  catalogueCategory: 'LAB' | 'IMAGING' | 'ECO';
  visitType: 'LABORATORY' | 'RADIOLOGY' | 'ECO';
  title: string;
  description: string;
  icon: LucideIcon;
};

const STATUS_TONE: Record<CaseStatus, { tone: 'neutral'|'info'|'success'|'warning'|'brand'; label: string }> = {
  NEW:               { tone: 'neutral', label: 'New' },
  AWAITING_PAYMENT:  { tone: 'warning', label: 'Awaiting payment' },
  AWAITING_STUDY:    { tone: 'info',    label: 'Awaiting study' },
  FINDINGS_COMPLETE: { tone: 'brand',   label: 'Findings complete' },
  CLOSED:            { tone: 'success', label: 'Closed' },
  RETURNED:          { tone: 'success', label: 'Returned' },
  CANCELLED:         { tone: 'neutral', label: 'Cancelled' },
};

export function DepartmentWorkspace({ config }: { config: Config }) {
  const [selected, setSelected] = useState<DepartmentCase | null>(null);
  const queryClient = useQueryClient();

  const { data: cases, isLoading } = useQuery({
    queryKey: ['dept-cases', config.category],
    queryFn: () => listCases(config.category),
    refetchInterval: 15000,
  });
  const { data: incoming } = useQuery({
    queryKey: ['dept-incoming', config.visitType],
    queryFn: () => listIncomingVisits(config.visitType),
    refetchInterval: 20000,
  });

  // Visits in queue that don't yet have a case opened.
  const visitsWithoutCase = useMemo(() => {
    if (!incoming || !cases) return [];
    const caseVisits = new Set(cases.map((c) => c.visitId));
    return incoming.filter((v) => !caseVisits.has(v.id));
  }, [incoming, cases]);

  const Icon = config.icon;
  return (
    <>
      <PageHeader
        title={config.title}
        description={config.description}
      />

      {selected ? (
        <CaseDetail
          caseId={selected.id}
          config={config}
          onBack={() => setSelected(null)}
          onChange={async () => {
            await queryClient.invalidateQueries({ queryKey: ['dept-cases', config.category] });
            await queryClient.invalidateQueries({ queryKey: ['dept-incoming', config.visitType] });
            await queryClient.invalidateQueries({ queryKey: ['payments'] });
            await queryClient.invalidateQueries({ queryKey: ['visits'] });
          }}
        />
      ) : (
        <div className="space-y-4">
          {visitsWithoutCase.length > 0 && (
            <Card>
              <div className="flex items-center gap-3 border-b border-ink-100 px-5 py-4">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-amber-50 text-amber-700">
                  <ListOrdered size={16} />
                </span>
                <div>
                  <h3 className="text-sm font-semibold text-ink-900">Incoming — open a case</h3>
                  <p className="text-xs text-ink-500">{visitsWithoutCase.length} visit{visitsWithoutCase.length === 1 ? '' : 's'} waiting for service selection</p>
                </div>
              </div>
              <ul className="divide-y divide-ink-100">
                {visitsWithoutCase.map((v) => (
                  <IncomingVisitRow key={v.id} v={v} config={config} onOpened={(c) => setSelected(c)} />
                ))}
              </ul>
            </Card>
          )}

          <Card>
            <div className="flex items-center gap-3 border-b border-ink-100 px-5 py-4">
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                <Icon size={16} />
              </span>
              <div>
                <h3 className="text-sm font-semibold text-ink-900">Active cases</h3>
                <p className="text-xs text-ink-500">{cases ? `${cases.length} case${cases.length === 1 ? '' : 's'}` : ''}</p>
              </div>
            </div>
            {isLoading ? (
              <div className="space-y-2 p-4">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-12" />)}</div>
            ) : !cases || cases.length === 0 ? (
              <EmptyState icon={Icon} title="No active cases" description="Cases will appear here once services are assigned." />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-ink-100 bg-ink-50/60 text-[11px] font-semibold uppercase tracking-wide text-ink-500">
                    <tr>
                      <Th>Visit</Th>
                      <Th>Patient</Th>
                      <Th>Origin</Th>
                      <Th>Services</Th>
                      <Th>Status</Th>
                      <Th className="text-end">Actions</Th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-100">
                    {cases.map((c) => (
                      <tr key={c.id} className="hover:bg-ink-50/60">
                        <Td>
                          <span className="font-mono text-xs font-semibold text-ink-900">{c.visitDisplayId}</span>
                        </Td>
                        <Td>
                          <div className="font-medium text-ink-900">{c.patientName}</div>
                          <div className="font-mono text-[11px] text-ink-500">{c.patientMrn}</div>
                        </Td>
                        <Td>
                          {c.visitOrigin === 'FORWARDED' ? <Badge tone="warning">forwarded</Badge> : <span className="text-xs text-ink-500">walk-in</span>}
                        </Td>
                        <Td>
                          <div className="text-xs">
                            {c.services.filter((s) => s.status === 'RESULT_UPLOADED').length}
                            <span className="text-ink-400"> / </span>
                            {c.services.length}
                            <span className="ms-1 text-ink-500">complete</span>
                          </div>
                        </Td>
                        <Td>
                          <Badge tone={STATUS_TONE[c.status].tone} dot>{STATUS_TONE[c.status].label}</Badge>
                        </Td>
                        <Td className="text-end">
                          <button
                            type="button"
                            onClick={() => setSelected(c)}
                            className="inline-flex items-center gap-1 rounded-md bg-brand-600 px-2 py-1 text-xs font-medium text-white hover:bg-brand-700"
                          >
                            Open
                            <ChevronRight size={12} className="rtl:rotate-180" />
                          </button>
                        </Td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      )}
    </>
  );
}

/* ------------------------------------------------------------------ Incoming row + open dialog ------------------------------------------------------------------ */

function IncomingVisitRow({
  v, config, onOpened,
}: {
  v: Visit;
  config: Config;
  onOpened: (c: DepartmentCase) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <li className="flex items-center justify-between gap-4 px-5 py-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs font-semibold text-ink-900">{v.visitDisplayId}</span>
          {v.origin === 'FORWARDED' && <Badge tone="warning">from {v.originatingType?.replace(/_/g, ' ')}</Badge>}
        </div>
        <div className="font-medium text-ink-900">{v.patientName}</div>
        <div className="font-mono text-[11px] text-ink-500">{v.patientMrn}</div>
      </div>
      <Button size="sm" onClick={() => setOpen(true)}>
        <Plus size={12} className="me-1" /> Pick services
      </Button>
      {open && <OpenCaseDialog visit={v} config={config} onClose={() => setOpen(false)} onCreated={(c) => { setOpen(false); onOpened(c); }} />}
    </li>
  );
}

function OpenCaseDialog({
  visit, config, onClose, onCreated,
}: {
  visit: Visit;
  config: Config;
  onClose: () => void;
  onCreated: (c: DepartmentCase) => void;
}) {
  const { data: catalogue } = useQuery({
    queryKey: ['catalogue-active', config.catalogueCategory],
    queryFn: () => listItems(config.catalogueCategory, true),
  });
  const [picked, setPicked] = useState<Set<string>>(new Set());

  const mutation = useMutation({
    mutationFn: () =>
      openCase({
        category: config.category,
        visitId: visit.id,
        services: Array.from(picked).map((id) => ({ serviceItemId: id, quantity: 1 })),
      }),
    onSuccess: (c) => {
      toast.success(`Case opened — payment routed to cashier`);
      onCreated(c);
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Open failed'),
  });

  const toggle = (id: string) => {
    const next = new Set(picked);
    if (next.has(id)) next.delete(id); else next.add(id);
    setPicked(next);
  };

  const totalFee = useMemo(() => {
    if (!catalogue) return 0;
    return catalogue.filter((x) => picked.has(x.id)).reduce((sum, x) => sum + (x.fee ?? 0), 0);
  }, [catalogue, picked]);

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-2xl overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">Open {config.title} case</h2>
            <p className="mt-0.5 text-xs text-ink-500">{visit.visitDisplayId} · {visit.patientName} · {visit.patientMrn}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100"><X size={18} /></button>
        </div>
        <div className="max-h-[60vh] overflow-y-auto p-5">
          <p className="mb-3 text-sm text-ink-600">Select services from the catalogue. The total will be billed at the central cashier.</p>
          {!catalogue ? (
            <Skeleton className="h-32" />
          ) : (
            <ul className="space-y-1">
              {catalogue.filter((x) => x.active && !x.forwardTo).map((it) => (
                <li key={it.id}>
                  <button
                    type="button"
                    onClick={() => toggle(it.id)}
                    className={cn(
                      'flex w-full items-center justify-between rounded-lg border px-3 py-2 text-start text-sm transition-colors',
                      picked.has(it.id) ? 'border-brand-300 bg-brand-50' : 'border-ink-200 hover:bg-ink-50',
                    )}
                  >
                    <div>
                      <div className="font-mono text-[11px] text-ink-500">{it.code}</div>
                      <div className="text-ink-900">{it.nameEn}</div>
                      {it.nameAr && <div className="text-xs text-ink-500" dir="rtl">{it.nameAr}</div>}
                    </div>
                    <div className="text-end">
                      <div className="font-mono text-sm">{(it.fee ?? 0).toLocaleString()} {it.currency}</div>
                      {picked.has(it.id) && <CheckCircle2 size={14} className="ms-auto mt-1 text-brand-600" />}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex items-center justify-between border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <span className="text-sm">
            <span className="text-ink-500">Total: </span>
            <span className="font-mono font-semibold text-ink-900">{totalFee.toLocaleString()} IQD</span>
            <span className="text-ink-500"> · {picked.size} service{picked.size === 1 ? '' : 's'}</span>
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
            <Button type="button" disabled={picked.size === 0 || mutation.isPending} onClick={() => mutation.mutate()}>
              {mutation.isPending ? 'Opening…' : <><Receipt size={14} className="me-1.5" /> Route to cashier</>}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ Case detail + findings entry ------------------------------------------------------------------ */

function CaseDetail({
  caseId, config, onBack, onChange,
}: {
  caseId: string;
  config: Config;
  onBack: () => void;
  onChange: () => Promise<void>;
}) {
  const { data: deptCase, isLoading } = useQuery({
    queryKey: ['dept-case', caseId],
    queryFn: async () => (await import('./api')).getCase(caseId),
    refetchInterval: 8000,
  });
  const queryClient = useQueryClient();

  const finalizeMut = useMutation({
    mutationFn: () => finalizeCase(caseId),
    onSuccess: async () => {
      toast.success('Case finalized');
      await queryClient.invalidateQueries({ queryKey: ['dept-case', caseId] });
      await onChange();
      onBack();
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Finalize failed'),
  });

  if (isLoading || !deptCase) {
    return <Skeleton className="h-96" />;
  }

  const allComplete = deptCase.services.length > 0 && deptCase.services.every((s) => s.status === 'RESULT_UPLOADED');
  const canUpload = deptCase.status === 'AWAITING_STUDY';

  return (
    <div className="space-y-4">
      <button type="button" onClick={onBack} className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-900">
        <ArrowLeft size={12} className="rtl:rotate-180" /> Back to queue
      </button>

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 px-5 py-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-sm font-semibold text-ink-900">{deptCase.visitDisplayId}</span>
              <Badge tone={STATUS_TONE[deptCase.status].tone} dot>{STATUS_TONE[deptCase.status].label}</Badge>
              {deptCase.visitOrigin === 'FORWARDED' && <Badge tone="warning">forwarded</Badge>}
            </div>
            <div className="mt-1 font-medium text-ink-900">{deptCase.patientName}</div>
            <div className="font-mono text-[11px] text-ink-500">{deptCase.patientMrn}</div>
          </div>
          <div className="flex items-center gap-2">
            {(deptCase.status === 'FINDINGS_COMPLETE' || deptCase.status === 'CLOSED' || deptCase.status === 'RETURNED') && (
              <Button
                variant="secondary"
                onClick={() => printResults(
                  deptCase as any,
                  { name: deptCase.patientName, mrn: deptCase.patientMrn },
                )}
              >
                <Printer size={14} className="me-1.5" /> Print results
              </Button>
            )}
            {allComplete && deptCase.status === 'FINDINGS_COMPLETE' && (
              <Button onClick={() => finalizeMut.mutate()} disabled={finalizeMut.isPending}>
                {finalizeMut.isPending ? 'Finalizing…' : (deptCase.visitOrigin === 'FORWARDED' ? 'Return results to origin' : 'Finalize & close case')}
              </Button>
            )}
          </div>
        </div>

        {deptCase.status === 'AWAITING_PAYMENT' && (
          <div className="mx-5 mt-4 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            <Clock size={14} /> Waiting for cashier to approve payment. Findings entry unlocks once paid.
          </div>
        )}

        <div className="space-y-3 p-5">
          {deptCase.services.map((line) => (
            <ServiceLineCard
              key={line.serviceItemId}
              caseId={caseId}
              line={line}
              category={config.category}
              canUpload={canUpload}
              onUploaded={async () => {
                await queryClient.invalidateQueries({ queryKey: ['dept-case', caseId] });
                await onChange();
              }}
            />
          ))}
        </div>
      </Card>
    </div>
  );
}

function ServiceLineCard({
  caseId, line, category, canUpload, onUploaded,
}: {
  caseId: string;
  line: CaseLine;
  category: DepartmentCategory;
  canUpload: boolean;
  onUploaded: () => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const queryClient = useQueryClient();

  const isUploaded = line.status === 'RESULT_UPLOADED';

  const { data: attachments } = useQuery({
    queryKey: ['case-attachments', caseId],
    queryFn: () => listCaseAttachments(caseId),
  });
  const myAttachments = (attachments ?? []).filter((a) => a.serviceItemId === line.serviceItemId);

  const uploadMut = useMutation({
    mutationFn: (file: File) => uploadAttachment(caseId, line.serviceItemId, file),
    onSuccess: async () => {
      toast.success('File attached');
      await queryClient.invalidateQueries({ queryKey: ['case-attachments', caseId] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Upload failed'),
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => deleteAttachment(id),
    onSuccess: async () => {
      toast.success('Attachment removed');
      await queryClient.invalidateQueries({ queryKey: ['case-attachments', caseId] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Delete failed'),
  });

  return (
    <div className={cn('rounded-lg border', isUploaded ? 'border-emerald-200 bg-emerald-50/30' : 'border-ink-200')}>
      <div className="flex items-center justify-between gap-3 px-4 py-3">
        <div className="min-w-0 flex-1">
          <div className="font-mono text-[11px] text-ink-500">{line.code}</div>
          <div className="font-medium text-ink-900">{line.name}</div>
          {line.fee != null && <div className="mt-0.5 text-xs text-ink-500">Fee: {line.fee.toLocaleString()}</div>}
        </div>
        {isUploaded ? (
          <Badge tone="success" dot>Result uploaded</Badge>
        ) : (
          <Badge tone="warning" dot>Pending</Badge>
        )}
        {canUpload && !isUploaded && (
          <Button size="sm" onClick={() => setEditing((e) => !e)}>
            {editing ? 'Cancel' : 'Enter findings'}
          </Button>
        )}
      </div>

      {(isUploaded || editing) && (
        <div className="border-t border-ink-100 p-4">
          {editing && !isUploaded ? (
            <FindingsForm
              caseId={caseId} line={line} category={category}
              onSaved={async () => { setEditing(false); await onUploaded(); }}
              onCancel={() => setEditing(false)}
            />
          ) : (
            <ReadOnlyFindings line={line} />
          )}
        </div>
      )}

      {/* Attachments — always visible once the case is past the cashier stage */}
      {canUpload || myAttachments.length > 0 ? (
        <div className="border-t border-ink-100 px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <div className="text-[11px] font-semibold uppercase tracking-wide text-ink-500">
              Attachments {myAttachments.length > 0 && `(${myAttachments.length})`}
            </div>
            {canUpload && (
              <label className={cn(
                'inline-flex cursor-pointer items-center gap-1 rounded-md border border-ink-200 bg-white px-2.5 py-1 text-xs font-medium text-ink-700 hover:bg-ink-50',
                uploadMut.isPending && 'pointer-events-none opacity-60',
              )}>
                <Plus size={12} /> {uploadMut.isPending ? 'Uploading…' : 'Add file'}
                <input
                  type="file"
                  className="hidden"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) uploadMut.mutate(f);
                    e.target.value = '';
                  }}
                />
              </label>
            )}
          </div>
          {myAttachments.length === 0 ? (
            <p className="text-xs text-ink-400">No files attached yet.</p>
          ) : (
            <ul className="space-y-1">
              {myAttachments.map((a) => (
                <li key={a.id} className="flex items-center gap-2 rounded-md bg-ink-50/50 px-2 py-1.5">
                  <a
                    href={attachmentDownloadUrl(a.id)}
                    target="_blank"
                    rel="noopener"
                    className="flex-1 truncate text-sm text-brand-700 hover:underline"
                  >
                    {a.fileName}
                  </a>
                  <span className="font-mono text-[10px] text-ink-500">{(a.sizeBytes / 1024).toFixed(0)} KB</span>
                  {canUpload && (
                    <button
                      type="button"
                      onClick={() => deleteMut.mutate(a.id)}
                      className="rounded p-1 text-ink-400 hover:bg-brand-50 hover:text-brand-700"
                      title="Remove attachment"
                    >
                      <X size={12} />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}

function ReadOnlyFindings({ line }: { line: CaseLine }) {
  return (
    <dl className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
      {line.numericValue != null && (
        <Field label="Value">
          <span className="font-mono">{line.numericValue}</span> {line.unit && <span className="text-ink-500">{line.unit}</span>}
          {line.referenceRange && <span className="ms-2 text-xs text-ink-500">(ref {line.referenceRange})</span>}
          {line.flag && line.flag !== 'NORMAL' && <Badge tone={line.flag === 'CRITICAL' ? 'danger' : 'warning'} className="ms-2">{line.flag}</Badge>}
        </Field>
      )}
      {line.textFindings && <Field label="Findings"><span className="whitespace-pre-wrap text-ink-700">{line.textFindings}</span></Field>}
      {line.measurements && <Field label="Measurements"><span className="text-ink-700">{line.measurements}</span></Field>}
      {line.comments && <Field label="Comments"><span className="text-ink-700">{line.comments}</span></Field>}
      {line.diagnosis && <Field label="Diagnosis"><span className="text-ink-700">{line.diagnosis}</span></Field>}
      {line.uploadedAt && (
        <Field label="Uploaded">
          <span className="text-xs text-ink-500">{new Date(line.uploadedAt).toLocaleString()}</span>
        </Field>
      )}
    </dl>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] font-semibold uppercase tracking-wide text-ink-500">{label}</dt>
      <dd className="mt-0.5 text-sm">{children}</dd>
    </div>
  );
}

function FindingsForm({
  caseId, line, category, onSaved, onCancel,
}: {
  caseId: string;
  line: CaseLine;
  category: DepartmentCategory;
  onSaved: () => Promise<void>;
  onCancel: () => void;
}) {
  const [textFindings, setTextFindings] = useState('');
  const [numericValue, setNumericValue] = useState('');
  const [unit, setUnit] = useState('');
  const [referenceRange, setReferenceRange] = useState('');
  const [flag, setFlag] = useState<'NORMAL'|'LOW'|'HIGH'|'CRITICAL'|''>('NORMAL');
  const [measurements, setMeasurements] = useState('');
  const [comments, setComments] = useState('');
  const [diagnosis, setDiagnosis] = useState('');

  const mut = useMutation({
    mutationFn: () =>
      uploadFindings(caseId, {
        serviceItemId: line.serviceItemId,
        textFindings: textFindings || undefined,
        numericValue: numericValue ? Number(numericValue) : undefined,
        unit: unit || undefined,
        referenceRange: referenceRange || undefined,
        flag: flag || undefined,
        measurements: measurements || undefined,
        comments: comments || undefined,
        diagnosis: diagnosis || undefined,
      }),
    onSuccess: () => onSaved(),
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Save failed'),
  });

  return (
    <div className="space-y-3">
      {category === 'LAB' && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Input label="Numeric value" type="number" step="0.001" value={numericValue} onChange={(e) => setNumericValue(e.target.value)} />
          <Input label="Unit" placeholder="mg/dL, g/L…" value={unit} onChange={(e) => setUnit(e.target.value)} />
          <Input label="Reference range" placeholder="70-100" value={referenceRange} onChange={(e) => setReferenceRange(e.target.value)} />
          <Select label="Flag" value={flag} onChange={(e) => setFlag(e.target.value as 'NORMAL'|'LOW'|'HIGH'|'CRITICAL'|'')}>
            <option value="NORMAL">Normal</option>
            <option value="LOW">Low</option>
            <option value="HIGH">High</option>
            <option value="CRITICAL">Critical</option>
          </Select>
        </div>
      )}
      <Input label="Findings (text)" value={textFindings} onChange={(e) => setTextFindings(e.target.value)} />
      {category !== 'LAB' && (
        <Input label="Measurements" placeholder="e.g. EF 60%, LV diameter 4.5cm" value={measurements} onChange={(e) => setMeasurements(e.target.value)} />
      )}
      <Input label="Comments" value={comments} onChange={(e) => setComments(e.target.value)} />
      <Input label="Diagnosis (optional)" value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)} />

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="button" onClick={() => mut.mutate()} disabled={mut.isPending}>
          {mut.isPending ? 'Saving…' : 'Save findings'}
        </Button>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ Three thin route entries ------------------------------------------------------------------ */

export function LaboratoryPage() {
  return <DepartmentWorkspace config={{
    category: 'LAB',
    catalogueCategory: 'LAB',
    visitType: 'LABORATORY',
    title: 'Laboratory',
    description: 'Open lab cases, route to cashier, and upload results. Forwarded cases auto-return to the originating department on finalisation.',
    icon: FlaskConical,
  }} />;
}

export function RadiologyPage() {
  return <DepartmentWorkspace config={{
    category: 'RADIOLOGY',
    catalogueCategory: 'IMAGING',
    visitType: 'RADIOLOGY',
    title: 'Radiology',
    description: 'Open imaging cases, conduct studies, and upload findings.',
    icon: Scan,
  }} />;
}

export function EcoPage() {
  return <DepartmentWorkspace config={{
    category: 'ECO',
    catalogueCategory: 'ECO',
    visitType: 'ECO',
    title: 'Echocardiography',
    description: 'Open ECO cases, conduct studies, and upload cardiologist findings.',
    icon: HeartPulse,
  }} />;
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={cn('px-4 py-3 text-start font-semibold', className)}>{children}</th>;
}
function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn('px-4 py-3 align-middle', className)}>{children}</td>;
}
