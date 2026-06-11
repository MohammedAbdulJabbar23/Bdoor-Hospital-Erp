import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  Activity, Scale, Unlock,
  Plus, Trash2, Lock, CheckCircle2, ArrowLeft, ClipboardList,
  Printer, Send, Save, AlertCircle, FileText,
  CornerDownRight, Pill, FlaskConical, Scan, Stethoscope, ChevronDown,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Sparkline } from '@/shared/ui/Sparkline';
import { extractApiError } from '@/shared/api/client';
import { listItems, ServiceCategory, ServiceItem } from '@/features/admin/catalogues/api';
import { forwardVisit, forwardWithTests, Visit, VisitType } from '@/features/reception/visits/api';
import {
  DoctorExam, Diagnosis, Prescription, UpsertBody, Vitals,
  upsertExam, getExamByVisit, finalizeExam, reopenExam, getPatientRecord, getChildVisits,
  PatientRecord, DeptCase, DeptCaseLine, Dispense,
} from './api';
import { useAuthStore } from '@/shared/auth/authStore';
import {
  deriveActiveMedications, deriveProblemList, deriveVitalsTrend,
} from './derive';
import { Link } from 'react-router-dom';
import { cn } from '@/shared/ui/cn';

const COMMON_DIAGNOSES = [
  { code: 'I10',   label: 'Hypertension' },
  { code: 'E11',   label: 'Diabetes Mellitus, type 2' },
  { code: 'J06.9', label: 'Acute upper respiratory infection' },
  { code: 'R51',   label: 'Headache' },
  { code: 'A09',   label: 'Gastroenteritis' },
  { code: 'M54.5', label: 'Low back pain' },
  { code: 'L20',   label: 'Atopic dermatitis' },
  { code: 'K30',   label: 'Functional dyspepsia' },
];

const ROUTES = ['oral', 'IV', 'IM', 'subcutaneous', 'topical', 'inhalation', 'rectal'];

type ExamTab = 'consultation' | 'vitals' | 'diagnoses' | 'prescriptions' | 'orders'
  | 'lab' | 'radiology' | 'medications' | 'history';

/** Returns true when the BP / HR / etc. is outside a safe range; used for subtle UI warnings. */
function vitalsOutOfRange(v: Vitals): { [k: string]: 'warn' | 'critical' | undefined } {
  const r: { [k: string]: 'warn' | 'critical' | undefined } = {};
  if (v.systolicBp != null) {
    if (v.systolicBp >= 180 || v.systolicBp < 90) r.bp = 'critical';
    else if (v.systolicBp >= 140 || v.systolicBp < 100) r.bp = 'warn';
  }
  if (v.diastolicBp != null && (v.diastolicBp >= 110 || v.diastolicBp < 60)) r.bp = r.bp ?? 'critical';
  if (v.heartRate != null) {
    if (v.heartRate >= 130 || v.heartRate < 40) r.hr = 'critical';
    else if (v.heartRate > 100 || v.heartRate < 60) r.hr = 'warn';
  }
  if (v.temperatureC != null) {
    if (v.temperatureC >= 39 || v.temperatureC < 35) r.temp = 'critical';
    else if (v.temperatureC >= 38) r.temp = 'warn';
  }
  if (v.oxygenSaturation != null) {
    if (v.oxygenSaturation < 90) r.spo2 = 'critical';
    else if (v.oxygenSaturation < 95) r.spo2 = 'warn';
  }
  return r;
}

export function ClinicalExamPage() {
  const { visitId } = useParams<{ visitId: string }>();
  const navigate = useNavigate();
  const { i18n } = useTranslation();
  const queryClient = useQueryClient();

  // ---------- Load existing exam (or null) and patient history -----------
  const { data: existingExam, isLoading } = useQuery({
    queryKey: ['exam', visitId],
    queryFn: () => getExamByVisit(visitId!),
    enabled: !!visitId,
  });

  // ---------- Local form state ----------------
  const [form, setForm] = useState<UpsertBody>({ visitId: visitId ?? '' });
  const [vitals, setVitals] = useState<Vitals>(emptyVitals());
  const [diagnoses, setDiagnoses] = useState<Diagnosis[]>([]);
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [exam, setExam] = useState<DoctorExam | null>(null);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  const lastSaveBody = useRef<string>('');
  const [tab, setTab] = useState<ExamTab>('consultation');

  useEffect(() => {
    if (existingExam) {
      setExam(existingExam);
      setVitals(existingExam.vitals);
      setDiagnoses(existingExam.diagnoses);
      setPrescriptions(existingExam.prescriptions);
      setForm({
        visitId: existingExam.visitId,
        chiefComplaint: existingExam.chiefComplaint ?? '',
        historyOfPresentIllness: existingExam.historyOfPresentIllness ?? '',
        examinationNotes: existingExam.examinationNotes ?? '',
        plan: existingExam.plan ?? '',
        referralInstructions: existingExam.referralInstructions ?? '',
      });
      setSavedAt(existingExam.updatedAt ? new Date(existingExam.updatedAt) : null);
    }
  }, [existingExam]);

  const isFinalized = exam?.status === 'FINALIZED';

  // ---------- Autosave (1.2s debounce) ----------
  useEffect(() => {
    if (!visitId || isFinalized) return;
    const body: UpsertBody = {
      visitId,
      vitals: stripBmi(vitals),
      chiefComplaint: form.chiefComplaint || undefined,
      historyOfPresentIllness: form.historyOfPresentIllness || undefined,
      examinationNotes: form.examinationNotes || undefined,
      plan: form.plan || undefined,
      referralInstructions: form.referralInstructions || undefined,
      diagnoses,
      prescriptions,
    };
    const serialized = JSON.stringify(body);
    if (serialized === lastSaveBody.current) return;
    if (serialized === '{"visitId":"' + visitId + '","diagnoses":[],"prescriptions":[]}' && !exam) return;

    setSaveStatus('saving');
    const timer = setTimeout(async () => {
      try {
        const saved = await upsertExam(body);
        setExam(saved);
        setSavedAt(new Date());
        setSaveStatus('saved');
        lastSaveBody.current = serialized;
      } catch (err) {
        setSaveStatus('error');
        toast.error(extractApiError(err)?.message ?? 'Autosave failed');
      }
    }, 1200);
    return () => clearTimeout(timer);
  }, [vitals, form, diagnoses, prescriptions, visitId, isFinalized, exam]);

  // ---------- Patient record (visits + dept findings + pharmacy) ----------------
  const { data: record } = useQuery({
    queryKey: ['patient-record', exam?.patientId],
    queryFn: () => getPatientRecord(exam!.patientId),
    enabled: !!exam?.patientId,
  });
  // History-shaped view used by the existing derive helpers (problem list, meds, vitals trend).
  const history = useMemo(
    () => record ? { patientId: record.patientId, totalVisits: record.totalVisits, entries: record.visits, timeline: [] } : undefined,
    [record],
  );
  // Detailed clinical data for the dedicated Lab / Radiology / Medications tabs (across all visits).
  const labCases = useMemo(() => (record?.departmentCases ?? []).filter((c) => c.category === 'LAB'), [record]);
  const imagingCases = useMemo(
    () => (record?.departmentCases ?? []).filter((c) => c.category === 'RADIOLOGY' || c.category === 'ECO'),
    [record],
  );
  const dispenses = record?.pharmacyDispenses ?? [];
  const meds = useMemo(() => deriveActiveMedications(history), [history]);

  // ---------- Child visits (forwarded orders) ----------------
  const { data: children } = useQuery({
    queryKey: ['visit-children', visitId],
    queryFn: () => getChildVisits(visitId!),
    enabled: !!visitId,
    refetchInterval: 8000,
  });

  // ---------- Finalize ----------
  const finalizeMut = useMutation({
    mutationFn: () => finalizeExam(exam!.id),
    onSuccess: async (saved) => {
      toast.success('Visit completed');
      setExam(saved);
      await queryClient.invalidateQueries({ queryKey: ['exam', visitId] });
      navigate(-1);
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Finalize failed'),
  });

  // ---------- Forward ----------
  const forwardMut = useMutation({
    mutationFn: (targetType: 'LABORATORY' | 'RADIOLOGY' | 'ECO') => forwardVisit(visitId!, targetType),
    onSuccess: (r) => {
      toast.success(`Forwarded to ${r.child.visitType.replace(/_/g, ' ')} — ${r.child.visitDisplayId}`);
      queryClient.invalidateQueries({ queryKey: ['visit-children', visitId] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Forward failed'),
  });

  const forwardWithTestsMut = useMutation({
    mutationFn: (input: { targetType: VisitType; serviceItemIds: string[] }) =>
      forwardWithTests(
        visitId!,
        input.targetType,
        input.serviceItemIds.map((id) => ({ serviceItemId: id, quantity: 1 })),
      ),
    onSuccess: (r) => {
      toast.success(`Forwarded with ${r.child.visitType.replace(/_/g, ' ')} order — ${r.child.visitDisplayId}`);
      queryClient.invalidateQueries({ queryKey: ['visit-children', visitId] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Forward failed'),
  });

  const [forwardPicker, setForwardPicker] = useState<'LABORATORY' | 'RADIOLOGY' | 'ECO' | null>(null);

  // ---------- Reopen (admin) ----------
  const reopenMut = useMutation({
    mutationFn: () => reopenExam(exam!.id),
    onSuccess: async (saved) => {
      toast.success('Exam reopened — back to draft');
      setExam(saved);
      await queryClient.invalidateQueries({ queryKey: ['exam', visitId] });
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Reopen failed'),
  });
  const isAdmin = useAuthStore((s) => s.user?.roles.includes('ADMIN') ?? false);

  // ---------- Helpers ----------
  const dt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: 'short' }),
    [i18n.language],
  );

  if (isLoading || !visitId) {
    return (
      <>
        <PageHeader title="Clinical exam" />
        <div className="space-y-4"><Skeleton className="h-32" /><Skeleton className="h-64" /></div>
      </>
    );
  }

  return (
    <div className="space-y-4 pb-24">
      {/* ------------ Patient + visit strip ------------ */}
      <div className="sticky top-0 z-10 -mx-6 -mt-4 border-b border-ink-200 bg-white/90 px-6 py-3 backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <button onClick={() => navigate(-1)} className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-900">
            <ArrowLeft size={12} className="rtl:rotate-180" /> Back
          </button>
          <SaveIndicator status={saveStatus} savedAt={savedAt} isFinalized={isFinalized} />
        </div>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              {exam && (
                <span className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-brand-50 text-brand-700 text-sm font-semibold">
                  {initials(exam.patientName)}
                </span>
              )}
              <div>
                <h1 className="text-lg font-semibold text-ink-900">{exam?.patientName ?? '—'}</h1>
                <div className="flex items-center gap-2 text-xs text-ink-500">
                  <span className="font-mono">{exam?.patientMrn}</span>
                  {exam?.visitDisplayId && <><span>·</span><span className="font-mono">{exam.visitDisplayId}</span></>}
                  {exam?.doctorName && <><span>·</span><span>{exam.doctorName}</span></>}
                </div>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {isFinalized ? (
              <>
                <Badge tone="success" dot><Lock size={10} className="me-0.5" /> Finalized</Badge>
                {isAdmin && (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => {
                      if (confirm('Reopen this finalized exam? The doctor will be able to amend it.')) {
                        reopenMut.mutate();
                      }
                    }}
                    disabled={reopenMut.isPending}
                  >
                    <Unlock size={12} className="me-1" /> Reopen
                  </Button>
                )}
              </>
            ) : (
              <Badge tone="warning" dot>Draft</Badge>
            )}
          </div>
        </div>
      </div>

      {/* ------------ Quick actions ------------ */}
      <div className="flex flex-wrap gap-2 print-hide">
        <Button variant="secondary" onClick={() => window.print()}>
          <Printer size={14} className="me-1.5" /> Print prescription
        </Button>
        {!isFinalized && exam && (
          <Button
            onClick={() => finalizeMut.mutate()}
            disabled={finalizeMut.isPending || (diagnoses.length === 0 && !form.chiefComplaint)}
          >
            <CheckCircle2 size={14} className="me-1.5" />
            {finalizeMut.isPending ? 'Finalizing…' : 'Finalize & complete visit'}
          </Button>
        )}
      </div>

      {/* ------------ Tabs (EncounterPage-style) ------------ */}
      <div className="flex flex-wrap gap-1 border-b border-ink-100 print-hide">
        {([
          { key: 'consultation', label: 'Consultation' },
          { key: 'vitals', label: 'Vitals' },
          { key: 'diagnoses', label: 'Diagnoses', count: diagnoses.length },
          { key: 'prescriptions', label: 'Prescriptions', count: prescriptions.length },
          { key: 'orders', label: 'Orders', count: (children ?? []).length },
          { key: 'lab', label: 'Lab', count: labCases.length },
          { key: 'radiology', label: 'Radiology', count: imagingCases.length },
          { key: 'medications', label: 'Medications', count: dispenses.length || meds.length },
          { key: 'history', label: 'History' },
        ] as { key: ExamTab; label: string; count?: number }[]).map((tb) => (
          <button
            key={tb.key}
            type="button"
            onClick={() => setTab(tb.key)}
            className={cn(
              'inline-flex items-center gap-1.5 border-b-2 px-3.5 py-2.5 text-sm font-medium transition-colors',
              tab === tb.key ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-600 hover:text-ink-900',
            )}
            data-testid={`exam-tab-${tb.key}`}
          >
            {tb.label}
            {tb.count != null && tb.count > 0 && (
              <span className="rounded-full bg-ink-100 px-1.5 text-[10px] font-semibold text-ink-600">{tb.count}</span>
            )}
          </button>
        ))}
      </div>

      {/* ------------ Tab content (each tab reuses the existing cards) ------------ */}
      <div className="print-hide">
        {tab === 'vitals' && (
          <VitalsCard vitals={vitals} onChange={setVitals} disabled={isFinalized} />
        )}

        {tab === 'consultation' && (
          <div className="space-y-4">
            <Card>
              <SectionHead icon={ClipboardList} title="Consultation" />
              <div className="space-y-4 px-5 pb-5">
                <Field label="Chief complaint" hint="Patient's reason for visit, in their own words.">
                  <textarea
                    className={textareaCls} rows={2} disabled={isFinalized}
                    value={form.chiefComplaint ?? ''}
                    onChange={(e) => setForm((f) => ({ ...f, chiefComplaint: e.target.value }))}
                    placeholder="e.g. Headache for 3 days"
                  />
                </Field>
                <Field label="History of present illness">
                  <textarea
                    className={textareaCls} rows={3} disabled={isFinalized}
                    value={form.historyOfPresentIllness ?? ''}
                    onChange={(e) => setForm((f) => ({ ...f, historyOfPresentIllness: e.target.value }))}
                  />
                </Field>
                <Field label="Examination notes">
                  <textarea
                    className={textareaCls} rows={4} disabled={isFinalized}
                    value={form.examinationNotes ?? ''}
                    onChange={(e) => setForm((f) => ({ ...f, examinationNotes: e.target.value }))}
                    placeholder="General appearance, system review, focused findings…"
                  />
                </Field>
                <Field label="Plan">
                  <textarea
                    className={textareaCls} rows={3} disabled={isFinalized}
                    value={form.plan ?? ''}
                    onChange={(e) => setForm((f) => ({ ...f, plan: e.target.value }))}
                  />
                </Field>
              </div>
            </Card>
            <Card>
              <SectionHead icon={Send} title="Referral instructions" />
              <div className="px-5 pb-5">
                <textarea
                  className={textareaCls} rows={2} disabled={isFinalized}
                  value={form.referralInstructions ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, referralInstructions: e.target.value }))}
                  placeholder="Notes for the receiving department; or follow-up instructions."
                />
              </div>
            </Card>
          </div>
        )}

        {tab === 'diagnoses' && (
          <DiagnosesCard diagnoses={diagnoses} onChange={setDiagnoses} disabled={isFinalized} />
        )}

        {tab === 'prescriptions' && (
          <PrescriptionsCard prescriptions={prescriptions} onChange={setPrescriptions} disabled={isFinalized} />
        )}

        {tab === 'orders' && (
          <OrdersPanel
            visitId={visitId}
            children={children ?? []}
            onForward={(t) => setForwardPicker(t)}
            forwarding={forwardMut.isPending}
            disabled={isFinalized}
            dt={dt}
          />
        )}

        {tab === 'lab' && (
          <Card>
            <SectionHead icon={FlaskConical} title="Laboratory results" subtitle="All lab work for this patient — newest first. Expand a case for per-test values, ranges and flags." />
            <div className="px-5 pb-5"><DeptCasesTab cases={labCases} kind="lab" dt={dt} /></div>
          </Card>
        )}

        {tab === 'radiology' && (
          <Card>
            <SectionHead icon={Scan} title="Radiology & imaging reports" subtitle="Radiology and ECO studies with findings and impression." />
            <div className="px-5 pb-5"><DeptCasesTab cases={imagingCases} kind="imaging" dt={dt} /></div>
          </Card>
        )}

        {tab === 'medications' && (
          <Card>
            <SectionHead icon={Pill} title="Medication history" subtitle="Pharmacy dispenses and medications from past prescriptions." />
            <div className="px-5 pb-5"><MedicationsTab dispenses={dispenses} meds={meds} dt={dt} /></div>
          </Card>
        )}

        {tab === 'history' && (
          <PatientRecordPanel
            record={record}
            history={history}
            currentVisitId={visitId}
            patientId={exam?.patientId}
            dt={dt}
          />
        )}
      </div>

      {/* ------------ Print-only prescription page ------------ */}
      {exam && (
        <div className="print-only">
          <PrintablePrescription exam={exam} vitals={vitals} diagnoses={diagnoses} prescriptions={prescriptions} chiefComplaint={form.chiefComplaint} />
        </div>
      )}

      {forwardPicker && (
        <ForwardTestsDialog
          targetType={forwardPicker}
          onClose={() => setForwardPicker(null)}
          onForwardWithoutTests={() => {
            forwardMut.mutate(forwardPicker);
            setForwardPicker(null);
          }}
          onForwardWithTests={(ids) => {
            forwardWithTestsMut.mutate({ targetType: forwardPicker, serviceItemIds: ids });
            setForwardPicker(null);
          }}
          submitting={forwardMut.isPending || forwardWithTestsMut.isPending}
        />
      )}

    </div>
  );
}

/* ============================================================== Printable prescription ============================================================== */

function PrintablePrescription({
  exam, vitals, diagnoses, prescriptions, chiefComplaint,
}: {
  exam: DoctorExam;
  vitals: Vitals;
  diagnoses: Diagnosis[];
  prescriptions: Prescription[];
  chiefComplaint?: string;
}) {
  const today = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' });
  return (
    <div className="font-sans">
      <header className="mb-4 flex items-start justify-between border-b-2 border-black pb-3">
        <div>
          <h1 className="text-xl font-bold">Albudoor Hospital</h1>
          <p className="text-xs">Reception · Doctor Consultation</p>
        </div>
        <div className="text-end text-xs">
          <div><strong>Visit:</strong> {exam.visitDisplayId}</div>
          <div><strong>Date:</strong> {today}</div>
        </div>
      </header>

      <section className="mb-3 grid grid-cols-2 gap-x-6 gap-y-1 text-xs">
        <div><strong>Patient:</strong> {exam.patientName}</div>
        <div><strong>MRN:</strong> {exam.patientMrn}</div>
        <div><strong>Doctor:</strong> {exam.doctorName}</div>
        {chiefComplaint && <div><strong>Complaint:</strong> {chiefComplaint}</div>}
      </section>

      {(vitals.systolicBp != null || vitals.heartRate != null || vitals.temperatureC != null) && (
        <section className="mb-3 border border-black p-2 text-xs">
          <div className="mb-1 font-bold">Vitals</div>
          <div className="grid grid-cols-4 gap-2 font-mono">
            {vitals.systolicBp != null && <span>BP {vitals.systolicBp}/{vitals.diastolicBp ?? '—'} mmHg</span>}
            {vitals.heartRate != null && <span>HR {vitals.heartRate} bpm</span>}
            {vitals.respiratoryRate != null && <span>RR {vitals.respiratoryRate}</span>}
            {vitals.temperatureC != null && <span>T {vitals.temperatureC}°C</span>}
            {vitals.oxygenSaturation != null && <span>SpO₂ {vitals.oxygenSaturation}%</span>}
            {vitals.weightKg != null && <span>W {vitals.weightKg}kg</span>}
            {vitals.heightCm != null && <span>H {vitals.heightCm}cm</span>}
          </div>
        </section>
      )}

      {diagnoses.length > 0 && (
        <section className="mb-3">
          <div className="mb-1 font-bold">Diagnoses</div>
          <ol className="list-inside list-decimal space-y-0.5 text-sm">
            {diagnoses.map((d, i) => (
              <li key={i}>
                {d.code && <span className="me-1 font-mono text-xs">{d.code}</span>}
                {d.description}
                {d.primary && <span className="ms-1">★ primary</span>}
              </li>
            ))}
          </ol>
        </section>
      )}

      {prescriptions.length > 0 && (
        <section className="mb-3">
          <div className="mb-1 font-bold">Prescription (Rx)</div>
          <table className="w-full border border-black text-xs">
            <thead>
              <tr className="bg-ink-50">
                <th className="border border-black p-1 text-start">Drug</th>
                <th className="border border-black p-1 text-start">Dose</th>
                <th className="border border-black p-1 text-start">Frequency</th>
                <th className="border border-black p-1 text-start">Duration</th>
                <th className="border border-black p-1 text-start">Route</th>
                <th className="border border-black p-1 text-start">Notes</th>
              </tr>
            </thead>
            <tbody>
              {prescriptions.map((p, i) => (
                <tr key={i}>
                  <td className="border border-black p-1">{p.drugName} {p.strength && <span className="text-ink-700">{p.strength}</span>}</td>
                  <td className="border border-black p-1">{p.dose ?? '—'}</td>
                  <td className="border border-black p-1">{p.frequency ?? '—'}</td>
                  <td className="border border-black p-1">{p.duration ?? '—'}</td>
                  <td className="border border-black p-1">{p.route ?? '—'}</td>
                  <td className="border border-black p-1">{p.notes ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <footer className="mt-8 border-t border-black pt-4 text-xs">
        <div className="flex items-end justify-between">
          <div>
            <div className="mb-8">Patient signature:</div>
            <div className="border-t border-black pt-1">_______________________</div>
          </div>
          <div className="text-end">
            <div className="mb-8">Doctor signature & stamp:</div>
            <div className="border-t border-black pt-1">_______________________</div>
            <div className="mt-1">{exam.doctorName}</div>
          </div>
        </div>
      </footer>
    </div>
  );
}

/* ============================================================== Vitals card ============================================================== */

function VitalsCard({ vitals, onChange, disabled }: { vitals: Vitals; onChange: (v: Vitals) => void; disabled: boolean }) {
  const ranges = vitalsOutOfRange(vitals);
  const set = (patch: Partial<Vitals>) => onChange({ ...vitals, ...patch, bmi: computeBmi({ ...vitals, ...patch }) });

  return (
    <Card>
      <SectionHead icon={Activity} title="Vitals" />
      <div className="grid grid-cols-2 gap-3 px-5 pb-5 sm:grid-cols-4">
        <VitalField label="Systolic BP" suffix="mmHg" tone={ranges.bp}>
          <input type="number" min={0} disabled={disabled} value={vitals.systolicBp ?? ''}
            onChange={(e) => set({ systolicBp: blankToNullN(e.target.value) })} className={inputCls} />
        </VitalField>
        <VitalField label="Diastolic BP" suffix="mmHg" tone={ranges.bp}>
          <input type="number" min={0} disabled={disabled} value={vitals.diastolicBp ?? ''}
            onChange={(e) => set({ diastolicBp: blankToNullN(e.target.value) })} className={inputCls} />
        </VitalField>
        <VitalField label="Heart rate" suffix="bpm" tone={ranges.hr}>
          <input type="number" min={0} disabled={disabled} value={vitals.heartRate ?? ''}
            onChange={(e) => set({ heartRate: blankToNullN(e.target.value) })} className={inputCls} />
        </VitalField>
        <VitalField label="Resp. rate" suffix="rpm">
          <input type="number" min={0} disabled={disabled} value={vitals.respiratoryRate ?? ''}
            onChange={(e) => set({ respiratoryRate: blankToNullN(e.target.value) })} className={inputCls} />
        </VitalField>
        <VitalField label="Temperature" suffix="°C" tone={ranges.temp}>
          <input type="number" step="0.1" disabled={disabled} value={vitals.temperatureC ?? ''}
            onChange={(e) => set({ temperatureC: blankToNullN(e.target.value, true) })} className={inputCls} />
        </VitalField>
        <VitalField label="SpO₂" suffix="%" tone={ranges.spo2}>
          <input type="number" min={0} max={100} disabled={disabled} value={vitals.oxygenSaturation ?? ''}
            onChange={(e) => set({ oxygenSaturation: blankToNullN(e.target.value) })} className={inputCls} />
        </VitalField>
        <VitalField label="Weight" suffix="kg">
          <input type="number" step="0.1" disabled={disabled} value={vitals.weightKg ?? ''}
            onChange={(e) => set({ weightKg: blankToNullN(e.target.value, true) })} className={inputCls} />
        </VitalField>
        <VitalField label="Height" suffix="cm">
          <input type="number" step="0.1" disabled={disabled} value={vitals.heightCm ?? ''}
            onChange={(e) => set({ heightCm: blankToNullN(e.target.value, true) })} className={inputCls} />
        </VitalField>
      </div>
      {vitals.bmi != null && (
        <div className="mx-5 mb-4 flex items-center gap-2 rounded-md bg-ink-50 px-3 py-1.5 text-xs text-ink-700">
          <Scale size={12} /> BMI: <span className="font-mono font-semibold">{vitals.bmi}</span> kg/m²
        </div>
      )}
    </Card>
  );
}

function VitalField({ label, suffix, tone, children }: { label: string; suffix?: string; tone?: 'warn' | 'critical'; children: React.ReactNode }) {
  return (
    <div className={cn('rounded-lg border p-2',
      tone === 'critical' ? 'border-brand-300 bg-brand-50/50' : tone === 'warn' ? 'border-amber-300 bg-amber-50/50' : 'border-ink-200')}>
      <div className="flex items-center justify-between">
        <label className="text-[11px] font-medium uppercase tracking-wide text-ink-500">{label}</label>
        {tone && <AlertCircle size={10} className={tone === 'critical' ? 'text-brand-600' : 'text-amber-600'} />}
      </div>
      <div className="mt-1 flex items-baseline gap-1">
        <div className="flex-1">{children}</div>
        {suffix && <span className="text-[11px] text-ink-500">{suffix}</span>}
      </div>
    </div>
  );
}

/* ============================================================== Diagnoses ============================================================== */

function DiagnosesCard({ diagnoses, onChange, disabled }: { diagnoses: Diagnosis[]; onChange: (d: Diagnosis[]) => void; disabled: boolean }) {
  const addRow = (preset?: { code?: string; label?: string }) => {
    onChange([...diagnoses, {
      code: preset?.code ?? null,
      description: preset?.label ?? '',
      primary: diagnoses.length === 0,
      notes: null,
    }]);
  };
  const remove = (i: number) => onChange(diagnoses.filter((_, idx) => idx !== i));
  const setRow = (i: number, patch: Partial<Diagnosis>) => onChange(diagnoses.map((d, idx) => idx === i ? { ...d, ...patch } : d));
  const setPrimary = (i: number) => onChange(diagnoses.map((d, idx) => ({ ...d, primary: idx === i })));

  return (
    <Card>
      <SectionHead icon={FileText} title="Diagnoses" />
      <div className="space-y-2 px-5 pb-5">
        {diagnoses.length === 0 && (
          <p className="rounded-lg border border-dashed border-ink-200 p-3 text-center text-xs text-ink-500">
            No diagnoses yet. Add one below.
          </p>
        )}
        {diagnoses.map((d, i) => (
          <div key={i} className="grid grid-cols-12 items-start gap-2 rounded-lg border border-ink-200 p-2">
            <div className="col-span-2"><Input placeholder="Code (ICD)" value={d.code ?? ''} disabled={disabled} onChange={(e) => setRow(i, { code: e.target.value || null })} /></div>
            <div className="col-span-7"><Input placeholder="Description" value={d.description} disabled={disabled} onChange={(e) => setRow(i, { description: e.target.value })} /></div>
            <div className="col-span-2 flex items-center gap-2 pt-2">
              <input type="radio" checked={d.primary} disabled={disabled} onChange={() => setPrimary(i)} className="accent-brand-600" />
              <span className="text-xs text-ink-600">Primary</span>
            </div>
            <div className="col-span-1 text-end">
              <button type="button" disabled={disabled} onClick={() => remove(i)} className="rounded-md p-1.5 text-ink-400 hover:bg-brand-50 hover:text-brand-700 disabled:opacity-40">
                <Trash2 size={14} />
              </button>
            </div>
          </div>
        ))}

        {!disabled && (
          <>
            <div className="flex items-center justify-between pt-2">
              <span className="text-[11px] uppercase tracking-wide text-ink-400">Quick add</span>
              <Button variant="secondary" size="sm" onClick={() => addRow()}>
                <Plus size={12} className="me-1" /> Custom diagnosis
              </Button>
            </div>
            <div className="flex flex-wrap gap-1">
              {COMMON_DIAGNOSES.map((c) => (
                <button key={c.code} type="button" onClick={() => addRow(c)}
                  className="inline-flex items-center gap-1 rounded-full border border-ink-200 bg-white px-3 py-1 text-xs text-ink-700 hover:border-brand-300 hover:bg-brand-50">
                  <span className="font-mono text-[10px] text-ink-500">{c.code}</span>
                  {c.label}
                </button>
              ))}
            </div>
          </>
        )}
      </div>
    </Card>
  );
}

/* ============================================================== Prescriptions ============================================================== */

function PrescriptionsCard({ prescriptions, onChange, disabled }: {
  prescriptions: Prescription[]; onChange: (p: Prescription[]) => void; disabled: boolean;
}) {
  const [pickerOpen, setPickerOpen] = useState(false);

  const add = (drug: ServiceItem) => {
    onChange([...prescriptions, {
      drugServiceItemId: drug.id,
      drugCode: drug.code,
      drugName: drug.nameEn,
      strength: drug.drug?.strength ?? null,
      dose: null, frequency: null, duration: null,
      route: drug.drug?.dosageForm?.toLowerCase().includes('inject') ? 'IV' : 'oral',
      notes: null,
    }]);
    setPickerOpen(false);
  };
  const setRow = (i: number, patch: Partial<Prescription>) =>
    onChange(prescriptions.map((p, idx) => idx === i ? { ...p, ...patch } : p));
  const remove = (i: number) => onChange(prescriptions.filter((_, idx) => idx !== i));

  return (
    <Card>
      <SectionHead icon={Pill} title="Prescriptions" />
      <div className="space-y-2 px-5 pb-5">
        {prescriptions.length === 0 && (
          <p className="rounded-lg border border-dashed border-ink-200 p-3 text-center text-xs text-ink-500">No prescriptions.</p>
        )}
        {prescriptions.map((p, i) => (
          <div key={i} className="rounded-lg border border-ink-200 p-3">
            <div className="mb-2 flex items-start justify-between gap-2">
              <div>
                <div className="font-mono text-[11px] text-ink-500">{p.drugCode}</div>
                <div className="font-medium text-ink-900">{p.drugName} {p.strength && <span className="text-ink-500">{p.strength}</span>}</div>
              </div>
              {!disabled && (
                <button type="button" onClick={() => remove(i)} className="rounded-md p-1.5 text-ink-400 hover:bg-brand-50 hover:text-brand-700">
                  <Trash2 size={14} />
                </button>
              )}
            </div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <Input placeholder="Dose" value={p.dose ?? ''} disabled={disabled} onChange={(e) => setRow(i, { dose: e.target.value || null })} />
              <Input placeholder="Frequency" value={p.frequency ?? ''} disabled={disabled} onChange={(e) => setRow(i, { frequency: e.target.value || null })} />
              <Input placeholder="Duration" value={p.duration ?? ''} disabled={disabled} onChange={(e) => setRow(i, { duration: e.target.value || null })} />
              <Select value={p.route ?? ''} disabled={disabled} onChange={(e) => setRow(i, { route: e.target.value || null })}>
                <option value="">Route…</option>
                {ROUTES.map((r) => <option key={r} value={r}>{r}</option>)}
              </Select>
            </div>
            <Input className="mt-2" placeholder="Notes (optional)" value={p.notes ?? ''} disabled={disabled} onChange={(e) => setRow(i, { notes: e.target.value || null })} />
          </div>
        ))}
        {!disabled && (
          <Button variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
            <Plus size={12} className="me-1" /> Add medication
          </Button>
        )}
      </div>
      {pickerOpen && <DrugPickerDialog onClose={() => setPickerOpen(false)} onPick={add} />}
    </Card>
  );
}

function DrugPickerDialog({ onClose, onPick }: { onClose: () => void; onPick: (d: ServiceItem) => void }) {
  const [q, setQ] = useState('');
  const { data } = useQuery({ queryKey: ['drugs'], queryFn: () => listItems('DRUG', true) });
  const filtered = (data ?? []).filter((d) =>
    !q || d.nameEn.toLowerCase().includes(q.toLowerCase()) || d.code.toLowerCase().includes(q.toLowerCase()) || (d.drug?.genericName ?? '').toLowerCase().includes(q.toLowerCase())
  );

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-lg overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="border-b border-ink-100 px-5 py-4">
          <h2 className="text-base font-semibold text-ink-900">Select medication</h2>
          <Input className="mt-3" autoFocus placeholder="Search by name, code, or generic…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="max-h-80 overflow-y-auto">
          {filtered.length === 0 ? (
            <p className="px-5 py-8 text-center text-sm text-ink-500">No drugs match.</p>
          ) : (
            <ul className="divide-y divide-ink-100">
              {filtered.map((d) => (
                <li key={d.id}>
                  <button type="button" onClick={() => onPick(d)} className="w-full px-5 py-3 text-start text-sm hover:bg-ink-50">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="font-medium text-ink-900">{d.nameEn} {d.drug?.strength && <span className="text-ink-500">{d.drug.strength}</span>}</div>
                        <div className="text-[11px] text-ink-500"><span className="font-mono">{d.code}</span>{d.drug?.dosageForm && <> · {d.drug.dosageForm}</>}</div>
                      </div>
                      <span className="font-mono text-xs text-ink-700">{d.fee?.toLocaleString()} {d.currency}</span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex justify-end border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}

/* ============================================================== Orders panel + History ============================================================== */

function OrdersPanel({
  children, onForward, forwarding, disabled, dt,
}: {
  visitId: string;
  children: Visit[];
  onForward: (t: 'LABORATORY' | 'RADIOLOGY' | 'ECO') => void;
  forwarding: boolean;
  disabled: boolean;
  dt: Intl.DateTimeFormat;
}) {
  return (
    <Card>
      <SectionHead icon={CornerDownRight} title="Orders" subtitle="Forward this visit to a department for tests, imaging, or echocardiography." />
      <div className="px-5 pb-3">
        {!disabled && (
          <div className="mb-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
            <Button size="sm" variant="secondary" disabled={forwarding} onClick={() => onForward('LABORATORY')} data-testid="forward-LABORATORY">Lab</Button>
            <Button size="sm" variant="secondary" disabled={forwarding} onClick={() => onForward('RADIOLOGY')} data-testid="forward-RADIOLOGY">Radiology</Button>
            <Button size="sm" variant="secondary" disabled={forwarding} onClick={() => onForward('ECO')} data-testid="forward-ECO">ECO</Button>
            {/* Phase-2 departments — BRD §6.7 promises these as forward options; surface as disabled
                so doctors see the documented choice list and know what's coming. */}
            <Button size="sm" variant="secondary" disabled title="Coming in a later release">Emergency</Button>
            <Button size="sm" variant="secondary" disabled title="Coming in a later release">ICU</Button>
            <Button size="sm" variant="secondary" disabled title="Coming in a later release">CCU</Button>
            <Button size="sm" variant="secondary" disabled title="Coming in a later release">Kidney Dialysis</Button>
            <Button size="sm" variant="secondary" disabled title="Coming in a later release">External Clinic</Button>
            <Button size="sm" variant="secondary" disabled title="Coming in a later release">Internal Stay</Button>
          </div>
        )}
        {children.length === 0 ? (
          <p className="text-xs text-ink-500">No active orders for this visit.</p>
        ) : (
          <ul className="divide-y divide-ink-100">
            {children.map((c) => (
              <li key={c.id} className="py-2 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-xs text-ink-900">{c.visitDisplayId}</span>
                  <Badge tone={statusTone(c.status)} dot>{c.status.replace(/_/g, ' ')}</Badge>
                </div>
                <div className="text-xs text-ink-500">{c.visitType.replace(/_/g, ' ')} · {dt.format(new Date(c.startedAt))}</div>
                {c.resultsSummary && (
                  <div className="mt-1.5 rounded bg-ink-50 p-2 font-mono text-[11px] whitespace-pre-line text-ink-700">{c.resultsSummary}</div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}

/* ============================================================== Forward-with-tests dialog ============================================================== */

function ForwardTestsDialog({
  targetType, onClose, onForwardWithoutTests, onForwardWithTests, submitting,
}: {
  targetType: 'LABORATORY' | 'RADIOLOGY' | 'ECO';
  onClose: () => void;
  onForwardWithoutTests: () => void;
  onForwardWithTests: (serviceItemIds: string[]) => void;
  submitting: boolean;
}) {
  const cat: ServiceCategory = targetType === 'LABORATORY' ? 'LAB' : targetType === 'RADIOLOGY' ? 'IMAGING' : 'ECO';
  const { data: items, isLoading } = useQuery({
    queryKey: ['catalogue-items', cat],
    queryFn: () => listItems(cat, true),
  });
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => {
    const list = items ?? [];
    if (!query.trim()) return list;
    const q = query.toLowerCase();
    return list.filter((it: ServiceItem) =>
      it.code.toLowerCase().includes(q) || it.nameEn.toLowerCase().includes(q));
  }, [items, query]);

  const toggle = (id: string) => {
    const next = new Set(picked);
    if (next.has(id)) next.delete(id); else next.add(id);
    setPicked(next);
  };
  const total = filtered.reduce((sum: number, it: ServiceItem) => (
    picked.has(it.id) && it.fee != null ? sum + it.fee : sum
  ), 0);

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="flex max-h-[85vh] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">Forward to {targetType.toLowerCase()}</h2>
            <p className="mt-0.5 text-xs text-ink-500">Select the tests the patient should pay for — the receiving department will see them pre-loaded. You can also forward without picking and let the dept choose.</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
            ✕
          </button>
        </div>
        <div className="border-b border-ink-100 px-5 py-3">
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by code or name…"
            className="h-9 w-full rounded-lg border border-ink-200 bg-white px-3 text-sm placeholder:text-ink-400 focus:border-brand-500"
          />
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-3">
          {isLoading ? (
            <p className="text-sm text-ink-500">Loading catalogue…</p>
          ) : filtered.length === 0 ? (
            <p className="text-sm text-ink-500">No services found.</p>
          ) : (
            <ul className="divide-y divide-ink-100">
              {filtered.map((it: ServiceItem) => (
                <li key={it.id} className="py-2">
                  <label className="flex cursor-pointer items-center gap-3">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded accent-brand-600"
                      checked={picked.has(it.id)}
                      onChange={() => toggle(it.id)}
                    />
                    <div className="min-w-0 flex-1">
                      <div className="text-sm text-ink-900">{it.nameEn}</div>
                      <div className="font-mono text-[11px] text-ink-500">{it.code}</div>
                    </div>
                    {it.fee != null && (
                      <span className="font-mono text-sm text-ink-700">{it.fee.toLocaleString()} {it.currency}</span>
                    )}
                  </label>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex items-center justify-between border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <div className="text-sm">
            <span className="text-ink-500">{picked.size} selected</span>
            {picked.size > 0 && (
              <> · <span className="font-mono text-ink-900">{total.toLocaleString()}</span></>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Button type="button" variant="ghost" onClick={onForwardWithoutTests} disabled={submitting}>
              Skip — let dept pick
            </Button>
            <Button
              type="button"
              onClick={() => onForwardWithTests(Array.from(picked))}
              disabled={submitting || picked.size === 0}
            >
              {submitting ? 'Forwarding…' : `Forward with ${picked.size} test${picked.size === 1 ? '' : 's'}`}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ============================================================== Patient record (tabbed) ============================================================== */

type RecordTab = 'summary' | 'diagnoses' | 'vitals' | 'visits';

function PatientRecordPanel({
  record, history, currentVisitId, patientId, dt,
}: {
  record: PatientRecord | undefined;
  history: import('./api').PatientHistory | undefined;
  currentVisitId: string;
  patientId?: string;
  dt: Intl.DateTimeFormat;
}) {
  const [tab, setTab] = useState<RecordTab>('summary');

  const dispenses = record?.pharmacyDispenses ?? [];
  const problems = useMemo(() => deriveProblemList(history), [history]);
  const meds = useMemo(() => deriveActiveMedications(history), [history]);
  const trend = useMemo(() => deriveVitalsTrend(history, 8), [history]);
  const otherVisits = (record?.visits ?? []).filter((v) => v.visitId !== currentVisitId);

  const tabs: { key: RecordTab; label: string; count: number; icon: typeof Activity }[] = [
    { key: 'summary',     label: 'Summary',  count: 0, icon: ClipboardList },
    { key: 'diagnoses',   label: 'Diagnoses',count: problems.length, icon: FileText },
    { key: 'vitals',      label: 'Vitals',   count: trend.length, icon: Activity },
    { key: 'visits',      label: 'Visits',   count: otherVisits.length, icon: Stethoscope },
  ];

  return (
    <Card>
      <div className="flex items-start justify-between gap-2 border-b border-ink-100 px-5 py-4">
        <div className="flex items-start gap-3">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
            <ClipboardList size={16} />
          </span>
          <div>
            <h3 className="text-sm font-semibold text-ink-900">History</h3>
            <p className="text-[11px] text-ink-500">{record?.totalVisits ?? '—'} total visits on file</p>
          </div>
        </div>
        {patientId && (
          <Link to={`/patients/${patientId}`} className="inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:underline">
            Full profile <ArrowLeft size={10} className="rotate-180 rtl:rotate-0" />
          </Link>
        )}
      </div>

      {/* Tab strip */}
      <div className="flex flex-wrap gap-1 border-b border-ink-100 bg-ink-50/40 px-3 py-2">
        {tabs.map((t) => {
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              className={cn(
                'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors',
                active ? 'bg-white text-brand-700 shadow-sm ring-1 ring-brand-100' : 'text-ink-600 hover:bg-white hover:text-ink-900',
              )}
            >
              <t.icon size={12} className={active ? 'text-brand-600' : 'text-ink-400'} />
              {t.label}
              {t.count > 0 && (
                <span className={cn(
                  'inline-flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-semibold',
                  active ? 'bg-brand-100 text-brand-700' : 'bg-ink-200 text-ink-600',
                )}>
                  {t.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className="px-5 py-4">
        {!record ? (
          <Skeleton className="h-32" />
        ) : tab === 'summary' ? (
          <SummaryTab problems={problems} meds={meds} trend={trend} dispenses={dispenses} dt={dt} />
        ) : tab === 'diagnoses' ? (
          <DiagnosesTab problems={problems} dt={dt} />
        ) : tab === 'vitals' ? (
          <VitalsTab trend={trend} dt={dt} />
        ) : (
          <VisitsTab visits={otherVisits} dt={dt} />
        )}
      </div>
    </Card>
  );
}

function SummaryTab({
  problems, meds, trend, dispenses, dt,
}: {
  problems: ReturnType<typeof deriveProblemList>;
  meds: ReturnType<typeof deriveActiveMedications>;
  trend: ReturnType<typeof deriveVitalsTrend>;
  dispenses: Dispense[];
  dt: Intl.DateTimeFormat;
}) {
  const hasAny = problems.length || meds.length || trend.length || dispenses.length;
  if (!hasAny) {
    return <EmptyState icon={ClipboardList} title="No history yet" hint="This is the first recorded visit on this patient." />;
  }
  return (
    <div className="space-y-4">
      {problems.length > 0 && (
        <SubSection title="Top problems" count={problems.length}>
          <ul className="space-y-1">
            {problems.slice(0, 4).map((p) => (
              <li key={(p.code ?? '') + p.description} className="flex items-start gap-2 text-sm">
                {p.code && <span className="mt-0.5 font-mono text-[10px] text-ink-500">{p.code}</span>}
                <span className="flex-1 text-ink-900">{p.description}</span>
                {p.chronic && <Badge tone="warning">recurring</Badge>}
                {p.primaryCount > 0 && <span className="text-brand-600" title="Was primary diagnosis">★</span>}
              </li>
            ))}
          </ul>
        </SubSection>
      )}
      {trend.length > 0 && (
        <SubSection title="Latest vitals" count={trend.length}>
          <VitalsSparklines trend={trend} />
        </SubSection>
      )}
      {meds.length > 0 && (
        <SubSection title="Active medications" count={meds.length}>
          <ul className="space-y-1">
            {meds.slice(0, 3).map((m, i) => (
              <li key={i} className="text-sm">
                <div className="font-medium text-ink-900">{m.drugName} {m.strength && <span className="text-ink-500">{m.strength}</span>}</div>
                <div className="text-[12px] text-ink-700">{[m.dose, m.frequency, m.duration].filter(Boolean).join(' · ')}</div>
                <div className="text-[11px] text-ink-500">{dt.format(new Date(m.prescribedAt))} · {m.prescribedByDoctor}</div>
              </li>
            ))}
          </ul>
        </SubSection>
      )}
    </div>
  );
}

function DiagnosesTab({ problems, dt }: { problems: ReturnType<typeof deriveProblemList>; dt: Intl.DateTimeFormat }) {
  if (problems.length === 0) {
    return <EmptyState icon={FileText} title="No diagnoses on file" hint="Diagnoses recorded in past visits will appear here." />;
  }
  return (
    <ul className="divide-y divide-ink-100">
      {problems.map((p) => (
        <li key={(p.code ?? '') + p.description} className="py-2.5 text-sm">
          <div className="flex items-start gap-2">
            {p.code && <span className="mt-0.5 font-mono text-[10px] text-ink-500">{p.code}</span>}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="text-ink-900">{p.description}</span>
                {p.chronic && <Badge tone="warning">recurring</Badge>}
                {p.primaryCount > 0 && <span className="text-brand-600" title="Was primary diagnosis">★</span>}
              </div>
              <div className="text-[11px] text-ink-500">
                {p.occurrences > 1 ? `${p.occurrences} visits · ` : ''}last {dt.format(new Date(p.lastSeen))}
              </div>
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}

function DeptCasesTab({ cases, kind, dt }: { cases: DeptCase[]; kind: 'lab' | 'imaging'; dt: Intl.DateTimeFormat }) {
  const [open, setOpen] = useState<string | null>(null);
  if (cases.length === 0) {
    return (
      <EmptyState
        icon={kind === 'lab' ? FlaskConical : Scan}
        title={kind === 'lab' ? 'No lab results' : 'No imaging studies'}
        hint={`Forward this visit to ${kind === 'lab' ? 'the lab' : 'radiology / ECO'} from the Orders panel above.`}
      />
    );
  }
  return (
    <ul className="space-y-2">
      {cases.map((c) => {
        const isOpen = open === c.id;
        const completedLines = c.services.filter((s) => s.status === 'COMPLETED').length;
        return (
          <li key={c.id} className="rounded-lg border border-ink-200">
            <button
              type="button"
              onClick={() => setOpen(isOpen ? null : c.id)}
              className="flex w-full items-center justify-between gap-2 px-3 py-2 text-start text-sm hover:bg-ink-50"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[11px] text-ink-500">{c.visitDisplayId}</span>
                  <Badge tone={deptStatusTone(c.status)} dot>{c.status.replace(/_/g, ' ')}</Badge>
                </div>
                <div className="mt-0.5 truncate text-xs text-ink-700">
                  {completedLines}/{c.services.length} {kind === 'lab' ? 'tests' : 'studies'} · {dt.format(new Date(c.createdAt))}
                </div>
              </div>
              <ChevronDown size={14} className={cn('shrink-0 text-ink-400 transition-transform', isOpen && 'rotate-180')} />
            </button>
            {isOpen && (
              <div className="space-y-2 border-t border-ink-100 bg-ink-50/40 px-3 py-2">
                {c.services.length === 0 && <p className="text-xs text-ink-500">No services on this case.</p>}
                {c.services.map((line, i) => (
                  <DeptLineRow key={i} line={line} kind={kind} dt={dt} />
                ))}
                {c.resultsSummary && (
                  <div className="rounded-md border border-ink-200 bg-white p-2 text-[11px] whitespace-pre-line text-ink-700">
                    <div className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-ink-500">Summary</div>
                    {c.resultsSummary}
                  </div>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function DeptLineRow({ line, kind, dt }: { line: DeptCaseLine; kind: 'lab' | 'imaging'; dt: Intl.DateTimeFormat }) {
  const flagged = line.flag && line.flag !== 'NORMAL';
  return (
    <div className="rounded-md border border-ink-100 bg-white p-2 text-xs">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="font-medium text-ink-900">{line.name}</span>
          {flagged && <Badge tone="danger">{line.flag}</Badge>}
        </div>
        <Badge tone={lineStatusTone(line.status)}>{line.status}</Badge>
      </div>
      {kind === 'lab' && line.numericValue != null && (
        <div className="mt-1 font-mono text-sm text-ink-900">
          {line.numericValue} {line.unit ?? ''}
          {line.referenceRange && <span className="ms-2 text-[11px] text-ink-500">ref {line.referenceRange}</span>}
        </div>
      )}
      {line.measurements && (
        <pre className="mt-1 whitespace-pre-wrap font-mono text-[11px] text-ink-700">{line.measurements}</pre>
      )}
      {line.diagnosis && (
        <div className="mt-1 text-[12px] text-ink-700"><span className="font-medium">Impression: </span>{line.diagnosis}</div>
      )}
      {line.textFindings && (
        <div className="mt-1 whitespace-pre-line text-[12px] text-ink-700">{line.textFindings}</div>
      )}
      {line.comments && (
        <div className="mt-1 text-[11px] italic text-ink-500">{line.comments}</div>
      )}
      {line.uploadedAt && (
        <div className="mt-1 text-[10px] text-ink-400">Uploaded {dt.format(new Date(line.uploadedAt))}</div>
      )}
    </div>
  );
}

function MedicationsTab({
  dispenses, meds, dt,
}: {
  dispenses: Dispense[];
  meds: ReturnType<typeof deriveActiveMedications>;
  dt: Intl.DateTimeFormat;
}) {
  const [open, setOpen] = useState<string | null>(null);
  if (dispenses.length === 0 && meds.length === 0) {
    return <EmptyState icon={Pill} title="No medications" hint="Prescriptions and pharmacy dispenses will appear here." />;
  }
  return (
    <div className="space-y-4">
      {dispenses.length > 0 && (
        <SubSection title="Pharmacy dispenses" count={dispenses.length}>
          <ul className="space-y-2">
            {dispenses.map((d) => {
              const isOpen = open === d.id;
              return (
                <li key={d.id} className="rounded-lg border border-ink-200">
                  <button
                    type="button"
                    onClick={() => setOpen(isOpen ? null : d.id)}
                    className="flex w-full items-center justify-between gap-2 px-3 py-2 text-start text-sm hover:bg-ink-50"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-[11px] text-ink-500">{d.dispenseDisplayId}</span>
                        <Badge tone={dispenseStatusTone(d.status)} dot>{d.status.replace(/_/g, ' ')}</Badge>
                      </div>
                      <div className="mt-0.5 truncate text-xs text-ink-700">
                        {d.lines.length} drug{d.lines.length === 1 ? '' : 's'} · {dt.format(new Date(d.createdAt))}
                      </div>
                    </div>
                    <ChevronDown size={14} className={cn('shrink-0 text-ink-400 transition-transform', isOpen && 'rotate-180')} />
                  </button>
                  {isOpen && (
                    <ul className="divide-y divide-ink-100 border-t border-ink-100 bg-ink-50/40 text-xs">
                      {d.lines.map((line, i) => (
                        <li key={i} className="px-3 py-1.5">
                          <div className="font-medium text-ink-900">{line.drugName} {line.strength && <span className="text-ink-500">{line.strength}</span>}</div>
                          <div className="text-[11px] text-ink-700">
                            {[line.dose, line.frequency, line.duration].filter(Boolean).join(' · ')}
                            {line.route && <span className="ms-1 text-ink-500">({line.route})</span>}
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        </SubSection>
      )}
      {meds.length > 0 && (
        <SubSection title="From past prescriptions" count={meds.length}>
          <ul className="divide-y divide-ink-100">
            {meds.map((m, i) => (
              <li key={i} className="py-2 text-sm">
                <div className="font-medium text-ink-900">{m.drugName} {m.strength && <span className="text-ink-500">{m.strength}</span>}</div>
                <div className="text-[12px] text-ink-700">
                  {[m.dose, m.frequency, m.duration].filter(Boolean).join(' · ')}
                  {m.route && <span className="ms-1 text-ink-500">({m.route})</span>}
                </div>
                <div className="text-[11px] text-ink-500">{dt.format(new Date(m.prescribedAt))} · {m.prescribedByDoctor}</div>
              </li>
            ))}
          </ul>
        </SubSection>
      )}
    </div>
  );
}

function VitalsTab({ trend, dt }: { trend: ReturnType<typeof deriveVitalsTrend>; dt: Intl.DateTimeFormat }) {
  if (trend.length === 0) {
    return <EmptyState icon={Activity} title="No vitals on file" hint="Vitals recorded in past exams will trend here." />;
  }
  return (
    <div className="space-y-4">
      <VitalsSparklines trend={trend} />
      <div>
        <div className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-ink-500">History ({trend.length})</div>
        <ul className="divide-y divide-ink-100 text-xs">
          {trend.map((t, i) => (
            <li key={i} className="grid grid-cols-6 items-center gap-2 py-1.5 font-mono text-ink-700">
              <span className="col-span-2 text-[11px] text-ink-500">{dt.format(new Date(t.recordedAt))}</span>
              <span>{t.vitals.systolicBp ?? '—'}/{t.vitals.diastolicBp ?? '—'}</span>
              <span>{t.vitals.heartRate ?? '—'}</span>
              <span>{t.vitals.temperatureC ?? '—'}</span>
              <span>{t.vitals.oxygenSaturation ?? '—'}</span>
            </li>
          ))}
        </ul>
        <div className="mt-1 grid grid-cols-6 gap-2 text-[10px] uppercase tracking-wide text-ink-400">
          <span className="col-span-2">When</span><span>BP</span><span>HR</span><span>T°</span><span>SpO₂</span>
        </div>
      </div>
    </div>
  );
}

function VitalsSparklines({ trend }: { trend: ReturnType<typeof deriveVitalsTrend> }) {
  const chrono = [...trend].reverse();
  const last = trend[0].vitals;
  const sysVals = chrono.map((t) => t.vitals.systolicBp ?? null);
  const diaVals = chrono.map((t) => t.vitals.diastolicBp ?? null);
  const hrVals  = chrono.map((t) => t.vitals.heartRate ?? null);
  const tempVals = chrono.map((t) => t.vitals.temperatureC != null ? Number(t.vitals.temperatureC) : null);
  const spo2Vals = chrono.map((t) => t.vitals.oxygenSaturation ?? null);
  return (
    <div className="grid grid-cols-2 gap-2">
      <SparkTile label="Blood pressure" unit="mmHg"
        value={last.systolicBp != null ? `${last.systolicBp}/${last.diastolicBp ?? '—'}` : '—'}
        chart={<Sparkline width={120} height={32} series={[
          { values: sysVals, color: 'text-brand-600' },
          { values: diaVals, color: 'text-sky-600' },
        ]} normalRange={[80, 130]} />} />
      <SparkTile label="Heart rate" unit="bpm" value={last.heartRate ?? '—'}
        chart={<Sparkline width={120} height={32} series={[{ values: hrVals, color: 'text-emerald-600' }]} normalRange={[60, 100]} />} />
      <SparkTile label="Temperature" unit="°C" value={last.temperatureC ?? '—'}
        chart={<Sparkline width={120} height={32} series={[{ values: tempVals, color: 'text-amber-600' }]} normalRange={[36, 37.2]} />} />
      <SparkTile label="SpO₂" unit="%" value={last.oxygenSaturation ?? '—'}
        chart={<Sparkline width={120} height={32} series={[{ values: spo2Vals, color: 'text-sky-600' }]} normalRange={[95, 100]} />} />
    </div>
  );
}

function SparkTile({ label, value, unit, chart }: { label: string; value: string | number; unit: string; chart: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-ink-200 bg-white p-2.5">
      <div className="text-[10px] font-semibold uppercase tracking-wide text-ink-500">{label}</div>
      <div className="mt-1 font-mono text-base font-semibold text-ink-900">{value}<span className="ms-1 text-[10px] text-ink-500">{unit}</span></div>
      <div className="mt-1 text-ink-400">{chart}</div>
    </div>
  );
}

function VisitsTab({
  visits, dt,
}: {
  visits: import('./api').HistoryEntry[];
  dt: Intl.DateTimeFormat;
}) {
  const [open, setOpen] = useState<string | null>(null);
  if (visits.length === 0) {
    return <EmptyState icon={Stethoscope} title="No prior visits" hint="This is the patient's first visit on record." />;
  }
  return (
    <ul className="divide-y divide-ink-100">
      {visits.map((e) => {
        const isOpen = open === e.visitId;
        const primary = e.exam?.diagnoses?.find((d) => d.primary) ?? e.exam?.diagnoses?.[0];
        return (
          <li key={e.visitId}>
            <button type="button" onClick={() => setOpen(isOpen ? null : e.visitId)} className="w-full py-2 text-start text-sm hover:bg-ink-50">
              <div className="flex items-center justify-between gap-2">
                <span className="font-mono text-[11px] text-ink-500">{e.visitDisplayId}</span>
                <span className="text-[11px] text-ink-500">{dt.format(new Date(e.startedAt))}</span>
              </div>
              <div className="text-xs text-ink-700">
                {e.visitType.replace(/_/g, ' ')} · <span className="text-ink-500">{e.status.replace(/_/g, ' ')}</span>
              </div>
              {primary && <div className="mt-0.5 truncate text-xs text-ink-900">{primary.description}</div>}
              {!primary && e.resultsSummary && (
                <div className="mt-0.5 truncate text-[11px] text-ink-500">{e.resultsSummary.split('\n')[0]}</div>
              )}
            </button>
            {isOpen && e.exam && (
              <div className="mb-2 ms-2 space-y-1 rounded-lg border border-ink-100 bg-ink-50/60 p-3 text-xs">
                {e.exam.chiefComplaint && <div><span className="font-medium">Complaint: </span>{e.exam.chiefComplaint}</div>}
                {e.exam.diagnoses.length > 0 && (
                  <div>
                    <span className="font-medium">Diagnoses: </span>
                    {e.exam.diagnoses.map((d, i) => (
                      <span key={i}>{i > 0 ? ', ' : ''}{d.description}{d.primary && <span className="ms-1 text-brand-600">★</span>}</span>
                    ))}
                  </div>
                )}
                {e.exam.prescriptions.length > 0 && (
                  <div>
                    <span className="font-medium">Rx: </span>
                    {e.exam.prescriptions.map((p, i) => (
                      <span key={i}>{i > 0 ? ', ' : ''}{p.drugName} {p.dose}</span>
                    ))}
                  </div>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function SubSection({ title, count, children }: { title: string; count?: number; children: React.ReactNode }) {
  return (
    <div>
      <div className="mb-1.5 flex items-center gap-2">
        <span className="text-[10px] font-semibold uppercase tracking-wide text-ink-500">{title}</span>
        {count != null && <span className="text-[10px] text-ink-400">{count}</span>}
      </div>
      {children}
    </div>
  );
}

function EmptyState({ icon: Icon, title, hint }: { icon: typeof Activity; title: string; hint?: string }) {
  return (
    <div className="flex flex-col items-center gap-1.5 py-6 text-center">
      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-ink-100 text-ink-400">
        <Icon size={16} />
      </span>
      <div className="text-sm font-medium text-ink-700">{title}</div>
      {hint && <p className="max-w-xs text-[11px] text-ink-500">{hint}</p>}
    </div>
  );
}

function deptStatusTone(s: string): 'neutral'|'info'|'success'|'warning'|'brand'|'danger' {
  switch (s) {
    case 'CLOSED': case 'RETURNED': case 'FINDINGS_COMPLETE': return 'success';
    case 'AWAITING_PAYMENT': return 'warning';
    case 'AWAITING_STUDY': case 'NEW': return 'info';
    case 'CANCELLED': return 'danger';
    default: return 'neutral';
  }
}

function lineStatusTone(s: string): 'neutral'|'info'|'success'|'warning'|'brand'|'danger' {
  switch (s) {
    case 'COMPLETED': return 'success';
    case 'IN_PROGRESS': return 'info';
    case 'CANCELLED': return 'danger';
    default: return 'neutral';
  }
}

function dispenseStatusTone(s: string): 'neutral'|'info'|'success'|'warning'|'brand'|'danger' {
  switch (s) {
    case 'DISPENSED': return 'success';
    case 'READY_TO_GIVE': return 'brand';
    case 'AWAITING_PAYMENT': return 'warning';
    case 'CANCELLED': return 'danger';
    case 'PENDING': return 'info';
    default: return 'neutral';
  }
}

/* ============================================================== Bits ============================================================== */

function SaveIndicator({ status, savedAt, isFinalized }: { status: 'idle' | 'saving' | 'saved' | 'error'; savedAt: Date | null; isFinalized: boolean }) {
  if (isFinalized) {
    return <span className="inline-flex items-center gap-1.5 text-xs text-emerald-700"><Lock size={12} /> Locked</span>;
  }
  if (status === 'saving') {
    return <span className="inline-flex items-center gap-1.5 text-xs text-ink-500"><Save size={12} className="animate-pulse" /> Saving…</span>;
  }
  if (status === 'error') {
    return <span className="inline-flex items-center gap-1.5 text-xs text-brand-700"><AlertCircle size={12} /> Save failed</span>;
  }
  if (savedAt) {
    return <span className="inline-flex items-center gap-1.5 text-xs text-ink-500"><CheckCircle2 size={12} className="text-emerald-600" /> Saved {savedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>;
  }
  return null;
}

function SectionHead({ icon: Icon, title, subtitle }: { icon: typeof Activity; title: string; subtitle?: string }) {
  return (
    <div className="flex items-start gap-3 border-b border-ink-100 px-5 py-4">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
        <Icon size={16} />
      </span>
      <div>
        <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
        {subtitle && <p className="text-xs text-ink-500">{subtitle}</p>}
      </div>
    </div>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs font-medium text-ink-700">{label}</label>
      {hint && <p className="mb-1 text-[11px] text-ink-500">{hint}</p>}
      <div className="mt-1">{children}</div>
    </div>
  );
}

function statusTone(s: string): 'neutral'|'info'|'success'|'warning'|'brand'|'danger' {
  switch (s) {
    case 'COMPLETED': case 'RETURNED': return 'success';
    case 'AWAITING_PAYMENT': case 'AWAITING_FINAL_PAYMENT': return 'warning';
    case 'OUTSTANDING_BALANCE': return 'danger';
    case 'IN_PROGRESS': case 'AWAITING_RESULTS': case 'AWAITING_STUDY': return 'info';
    case 'TREATMENT_FINISHED': case 'FINDINGS_COMPLETE': return 'brand';
    default: return 'neutral';
  }
}

function emptyVitals(): Vitals {
  return {
    systolicBp: null, diastolicBp: null, heartRate: null, respiratoryRate: null,
    temperatureC: null, oxygenSaturation: null, weightKg: null, heightCm: null, bmi: null, notes: null,
  };
}

function computeBmi(v: Vitals): number | null {
  if (v.weightKg == null || v.heightCm == null || v.heightCm <= 0) return null;
  const m = v.heightCm / 100;
  return Math.round((v.weightKg / (m * m)) * 10) / 10;
}

function stripBmi(v: Vitals) {
  // Server doesn't accept bmi; it's derived.
  const { bmi: _bmi, ...rest } = v;
  return rest;
}

function blankToNullN(v: string, allowDecimal = false): number | null {
  if (v === '' || v == null) return null;
  return allowDecimal ? parseFloat(v) : parseInt(v, 10);
}

function initials(name: string) {
  const p = name.trim().split(/\s+/).filter(Boolean);
  if (p.length === 0) return '?';
  if (p.length === 1) return p[0][0].toUpperCase();
  return (p[0][0] + p[p.length - 1][0]).toUpperCase();
}

const inputCls = 'h-8 w-full rounded-md border border-ink-200 bg-white px-2 text-sm text-ink-900 focus:border-brand-500 disabled:bg-ink-50';
const textareaCls = 'w-full rounded-lg border border-ink-200 bg-white p-2 text-sm text-ink-900 focus:border-brand-500 disabled:bg-ink-50';
