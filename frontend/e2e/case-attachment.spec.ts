import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';

/**
 * Verifies the case-attachment upload + list + download path. Builds a department case
 * via API (lab forwarded from a doctor visit) so we don't depend on UI flow.
 */
test('lab staff can upload, list, and download an attachment', async () => {
  const stamp = Date.now();
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const doctor = await authedContext('doctor');
  const lab = await authedContext('lab');

  // 1. Register patient + create a walk-in doctor visit with consult charge
  const patient = await (await admin.post('http://localhost:8080/api/patients', {
    data: {
      fullName: `Attachment Test ${stamp}`,
      gender: 'MALE',
      dateOfBirth: '1975-02-09',
      mobileNumber: `077${String(stamp).slice(-7)}`,
      vip: false,
    },
  })).json();

  const doctorsList = await (await admin.get('http://localhost:8080/api/doctors')).json();
  const doc = doctorsList.find((d: any) => d.fullName.includes('Kareem'));

  const appt = await (await admin.post('http://localhost:8080/api/appointments', {
    data: { patientId: patient.id, doctorId: doc.id, type: 'WALKIN' },
  })).json();
  await admin.post(`http://localhost:8080/api/appointments/${appt.id}/check-in`);

  // Approve the consult payment so visit becomes IN_PROGRESS
  await expect(async () => {
    const list = await cashier.get('http://localhost:8080/api/payments?status=PENDING&size=100');
    const p = (await list.json()).content.find(
      (x: any) => x.patientMrn === patient.mrn && x.stage === 'INITIAL',
    );
    expect(p).toBeTruthy();
    const ar = await cashier.post(`http://localhost:8080/api/payments/${p.id}/approve`, { data: { paymentMethod: 'CASH' } });
    expect(ar.ok()).toBeTruthy();
  }).toPass({ timeout: 10_000 });

  // 2. Doctor forwards-with-tests to lab — picks a lab service so case is auto-opened
  const labItems = await (await doctor.get('http://localhost:8080/api/catalogue/items?category=LAB')).json();
  expect(labItems.length).toBeGreaterThan(0);
  const labService = labItems[0];

  const consultVisitId = appt.visitId;
  const forwardRes = await doctor.post(
    `http://localhost:8080/api/visits/${consultVisitId}/forward-with-tests`,
    { data: { targetType: 'LABORATORY', services: [{ serviceItemId: labService.id, quantity: 1 }] } },
  );
  expect(forwardRes.status()).toBe(200);
  const forwarded = await forwardRes.json();

  // 3. Cashier approves the referral payment so the case unlocks for findings
  await expect(async () => {
    const list = await cashier.get('http://localhost:8080/api/payments?status=PENDING&size=100');
    const p = (await list.json()).content.find(
      (x: any) => x.patientMrn === patient.mrn && x.stage === 'REFERRAL',
    );
    expect(p).toBeTruthy();
    const ar = await cashier.post(`http://localhost:8080/api/payments/${p.id}/approve`, { data: { paymentMethod: 'CASH' } });
    expect(ar.ok()).toBeTruthy();
  }).toPass({ timeout: 10_000 });

  // 4. Lab uploads an attachment to the case service line
  const caseId = forwarded.caseId;
  const fileContent = Buffer.from('PDF dummy content for attachment test', 'utf-8');
  const upRes = await lab.post(
    `http://localhost:8080/api/dept-cases/${caseId}/services/${labService.id}/attachments`,
    {
      multipart: {
        file: {
          name: 'lab-result.pdf',
          mimeType: 'application/pdf',
          buffer: fileContent,
        },
      },
    },
  );
  expect(upRes.status()).toBe(200);
  const att = await upRes.json();
  expect(att.fileName).toBe('lab-result.pdf');
  expect(att.sizeBytes).toBe(fileContent.length);

  // 5. List attachments and verify ours is present
  const listRes = await lab.get(`http://localhost:8080/api/dept-cases/${caseId}/attachments`);
  const all = await listRes.json();
  expect(all.some((a: any) => a.id === att.id)).toBeTruthy();

  // 6. Download the file and verify bytes match
  const dlRes = await lab.get(`http://localhost:8080/api/dept-cases/attachments/${att.id}/file`);
  const body = await dlRes.body();
  expect(body.toString()).toBe(fileContent.toString());

  await admin.dispose();
  await cashier.dispose();
  await doctor.dispose();
  await lab.dispose();
});
