import { test, expect, APIRequestContext } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * Doctor visit page — the dedicated Lab tab shows full detailed results. Seeds a consult to
 * IN_PROGRESS, the doctor forwards it to the lab, drives the lab case to COMPLETED with a detailed
 * result (value / unit / reference range / abnormal flag), then opens the exam and asserts the Lab
 * tab renders that detail.
 */

async function approvePending(cashier: APIRequestContext, visitId: string) {
  const pend = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=300`)).json();
  const pay = pend.content.find((p: any) => p.visitId === visitId);
  await cashier.post(`${API_BASE}/payments/${pay.id}/approve`, { data: { paymentMethod: 'CASH' } });
}

test('doctor sees full detailed lab result on the exam Lab tab', async ({ page }) => {
  const admin = await authedContext('admin');
  const recep = await authedContext('receptionist');
  const cashier = await authedContext('cashier');
  const doctorApi = await authedContext('doctor');
  const lab = await authedContext('lab');

  // --- Consult → IN_PROGRESS. ---
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const doctor = (await (await admin.get(`${API_BASE}/doctors`)).json())[0];
  const appt = await (await recep.post(`${API_BASE}/appointments`, {
    data: { patientId: patient.id, doctorId: doctor.id, type: 'WALKIN' },
  })).json();
  await recep.post(`${API_BASE}/appointments/${appt.id}/check-in`);
  const visit = (await (await recep.get(`${API_BASE}/visits?size=50`)).json()).content
    .find((v: any) => v.patientId === patient.id);
  await approvePending(cashier, visit.id);
  await expect(async () => {
    const v = await (await recep.get(`${API_BASE}/visits/${visit.id}`)).json();
    expect(v.status).toBe('IN_PROGRESS');
  }).toPass({ timeout: 10_000 });

  // --- Doctor forwards to lab; drive the lab child to COMPLETED with a detailed result. ---
  const fwd = await (await doctorApi.post(`${API_BASE}/visits/${visit.id}/forward`, {
    data: { targetType: 'LABORATORY' },
  })).json();
  const childId = fwd.child.id;
  const childDisplay = fwd.child.visitDisplayId as string;
  const svc = (await (await lab.get(`${API_BASE}/catalogue/items?category=LAB&activeOnly=true`)).json())[0].id;
  const dc = await (await lab.post(`${API_BASE}/dept-cases/open`, {
    data: { category: 'LAB', visitId: childId, services: [{ serviceItemId: svc, quantity: 1 }] },
  })).json();
  await approvePending(cashier, childId);
  await expect(async () => {
    const c = await (await lab.get(`${API_BASE}/dept-cases/${dc.id}`)).json();
    expect(c.status).toBe('AWAITING_STUDY');
  }).toPass({ timeout: 10_000 });
  await lab.post(`${API_BASE}/dept-cases/${dc.id}/findings`, {
    data: { serviceItemId: svc, numericValue: 18.2, unit: 'x10^9/L', referenceRange: '4.0-11.0', flag: 'HIGH', textFindings: 'Leukocytosis.' },
  });
  await lab.post(`${API_BASE}/dept-cases/${dc.id}/finalize`, { data: {} });

  await admin.dispose(); await recep.dispose(); await cashier.dispose(); await doctorApi.dispose(); await lab.dispose();

  // --- Doctor opens the exam → Lab tab → expand the case → detailed result is shown. ---
  await login(page, 'doctor');
  await page.goto(`/clinical/exam/${visit.id}`);
  await page.getByTestId('exam-tab-lab').click();
  await page.getByRole('button', { name: new RegExp(childDisplay) }).click(); // expand the case
  // The value/range/flag appear in both the per-test detail row and the results-summary box.
  await expect(page.getByText(/18\.2/).first()).toBeVisible({ timeout: 10_000 });
  await expect(page.getByText('HIGH').first()).toBeVisible();
  await expect(page.getByText(/4\.0-11\.0/).first()).toBeVisible();
});
