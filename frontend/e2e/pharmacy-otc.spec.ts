import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';

/**
 * Walk-in OTC sale: pharmacist creates a sale, which spins up a PHARMACY visit + dispense
 * AWAITING_PAYMENT. Cashier approves. Dispense becomes READY_TO_GIVE. Pharmacist marks given.
 * Pure-API test — covers the whole inventory/walk-in path without UI.
 */
test('walk-in OTC sale flows through cashier and decrements stock', async () => {
  const stamp = Date.now();
  const admin = await authedContext('admin');
  const cashier = await authedContext('cashier');
  const pharmacist = await authedContext('pharmacist');

  // 1. Register a patient
  const pres = await admin.post('http://localhost:8080/api/patients', {
    data: {
      fullName: `OTC Customer ${stamp}`,
      gender: 'FEMALE',
      dateOfBirth: '1980-06-15',
      mobileNumber: `077${String(stamp).slice(-7)}`,
      vip: false,
    },
  });
  const patient = await pres.json();

  // 2. Find a drug from the catalogue
  const cres = await admin.get('http://localhost:8080/api/catalogue/items?category=DRUG');
  const drugs = await cres.json();
  expect(drugs.length).toBeGreaterThan(0);
  const drug = drugs[0];

  // 3. Receive 100 units of stock (so OTC + mark-given succeeds)
  const future = new Date();
  future.setFullYear(future.getFullYear() + 1);
  const expiryIso = future.toISOString().slice(0, 10);
  const bres = await pharmacist.post('http://localhost:8080/api/pharmacy/inventory/batches', {
    data: {
      drugServiceItemId: drug.id,
      batchNo: `BATCH-${stamp}`,
      expiryDate: expiryIso,
      qty: 100,
      unitCost: 100,
      supplier: 'Test Supplier',
    },
  });
  expect(bres.status()).toBe(200);
  const batch = await bres.json();

  // Snapshot remaining stock for this drug
  const beforeStock = await pharmacist.get('http://localhost:8080/api/pharmacy/inventory/stock');
  const beforeRow = (await beforeStock.json()).find((s: any) => s.drugServiceItemId === drug.id);
  const stockBefore = beforeRow?.totalRemaining ?? 0;

  // 4. Create the OTC sale (3 units)
  const sres = await pharmacist.post('http://localhost:8080/api/pharmacy/walk-in-sales', {
    data: {
      patientId: patient.id,
      lines: [{ drugServiceItemId: drug.id, quantity: 3 }],
    },
  });
  expect(sres.status()).toBe(200);
  const sale = await sres.json();
  expect(sale.dispense.status).toBe('AWAITING_PAYMENT');

  // 5. Cashier approves the pharmacy payment
  const plist = await cashier.get('http://localhost:8080/api/payments?status=PENDING&size=100');
  const pending = (await plist.json()).content.find(
    (p: any) => p.patientMrn === patient.mrn && p.stage === 'PHARMACY',
  );
  expect(pending).toBeTruthy();
  const aRes = await cashier.post(`http://localhost:8080/api/payments/${pending.id}/approve`, {
    data: { paymentMethod: 'CASH' },
  });
  expect(aRes.ok()).toBeTruthy();

  // 6. Dispense should now be READY_TO_GIVE
  await expect(async () => {
    const r = await pharmacist.get(`http://localhost:8080/api/dispenses/${sale.dispense.id}`);
    expect((await r.json()).status).toBe('READY_TO_GIVE');
  }).toPass({ timeout: 5_000 });

  // 7. Mark given — triggers stock decrement
  const gRes = await pharmacist.post(`http://localhost:8080/api/dispenses/${sale.dispense.id}/mark-given`);
  expect(gRes.ok()).toBeTruthy();

  // 8. Stock decreased by 3 from the snapshot taken right after receiving the batch
  const afterStock = await pharmacist.get('http://localhost:8080/api/pharmacy/inventory/stock');
  const afterRow = (await afterStock.json()).find((s: any) => s.drugServiceItemId === drug.id);
  expect(afterRow.totalRemaining).toBe(stockBefore - 3);

  // 9. Total batch quantity across all batches for this drug decreased by exactly 3
  // (FEFO picks the earliest-expiring batch, which may not be ours if older stock exists.)
  const bAfter = await pharmacist.get(`http://localhost:8080/api/pharmacy/inventory/drug/${drug.id}`);
  const totalRemaining = (await bAfter.json()).reduce((s: number, b: any) => s + b.qtyRemaining, 0);
  expect(totalRemaining).toBe(stockBefore - 3);

  await admin.dispose();
  await cashier.dispose();
  await pharmacist.dispose();
});
