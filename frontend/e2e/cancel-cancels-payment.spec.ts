import { test, expect, request as pwRequest } from '@playwright/test';
import { authedContext } from './helpers/api';

/**
 * Regression for HMS punch-list item: cancelling an appointment must also cancel the
 * pending consult-charge payment in the cashier queue.
 * Pure-API test — fast and deterministic.
 */
test('cancelling a booked appointment auto-rejects the pending consult payment', async () => {
  const stamp = Date.now();
  const admin = await authedContext('admin');

  // 1. Register a fresh patient
  const pres = await admin.post('http://localhost:8080/api/patients', {
    data: {
      fullName: `Cancel Test ${stamp}`,
      gender: 'MALE',
      dateOfBirth: '1985-01-15',
      mobileNumber: `077${String(stamp).slice(-7)}`,
      vip: false,
    },
  });
  expect(pres.status()).toBe(201);
  const patient = await pres.json();

  // 2. Pick a doctor
  const dres = await admin.get('http://localhost:8080/api/doctors');
  const doctors = await dres.json();
  const doctor = doctors.find((d: any) => d.fullName.includes('Kareem'));
  expect(doctor).toBeTruthy();

  // 3. Book a walk-in appointment (creates a visit, fires consult-charge)
  const ares = await admin.post('http://localhost:8080/api/appointments', {
    data: { patientId: patient.id, doctorId: doctor.id, type: 'WALKIN' },
  });
  expect(ares.status()).toBe(201);
  const appt = await ares.json();

  // 4. Check the patient in (triggers payment creation via bridge)
  const cres = await admin.post(`http://localhost:8080/api/appointments/${appt.id}/check-in`);
  expect(cres.ok()).toBeTruthy();

  // 5. There should now be a pending payment for this visit
  await expect(async () => {
    const list = await admin.get('http://localhost:8080/api/payments?status=PENDING&size=100');
    const body = await list.json();
    const mine = body.content.filter((p: any) => p.patientMrn === patient.mrn);
    expect(mine.length).toBeGreaterThan(0);
    expect(mine[0].status).toBe('PENDING');
  }).toPass({ timeout: 10_000 });

  // 6. Cancel the appointment
  const xres = await admin.post(`http://localhost:8080/api/appointments/${appt.id}/cancel`, {
    data: { reason: 'Patient no-show' },
  });
  expect(xres.ok()).toBeTruthy();

  // 7. The previously-pending payment should have been auto-rejected by VisitCancelPaymentBridge
  await expect(async () => {
    const list = await admin.get('http://localhost:8080/api/payments?size=100');
    const body = await list.json();
    const mine = body.content.filter((p: any) => p.patientMrn === patient.mrn);
    expect(mine.length).toBeGreaterThan(0);
    expect(mine.every((p: any) => p.status !== 'PENDING')).toBeTruthy();
    expect(mine.some((p: any) => p.status === 'REJECTED' && /cancel/i.test(p.rejectionReason ?? ''))).toBeTruthy();
  }).toPass({ timeout: 10_000 });

  await admin.dispose();
});
