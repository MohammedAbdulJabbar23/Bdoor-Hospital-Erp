import { api } from '@/shared/api/client';

export type BatchStatus = 'OK' | 'EXPIRING_SOON' | 'EXPIRED' | 'EMPTY';

export type DrugBatch = {
  id: string;
  drugServiceItemId: string;
  drugCode: string | null;
  drugName: string | null;
  batchNo: string;
  expiryDate: string;        // ISO date
  qtyReceived: number;
  qtyRemaining: number;
  unitCost: number | null;
  supplier: string | null;
  status: BatchStatus;
};

export type DrugStock = {
  drugServiceItemId: string;
  drugCode: string | null;
  drugName: string | null;
  totalRemaining: number;
  earliestExpiry: string | null;
  batchCount: number;
};

export type ReceiveBatchBody = {
  drugServiceItemId: string;
  batchNo: string;
  expiryDate: string;
  qty: number;
  unitCost?: number;
  supplier?: string;
};

export async function listStock(): Promise<DrugStock[]> {
  const res = await api.get('/pharmacy/inventory/stock');
  return res.data;
}

export async function listBatches(drugId?: string): Promise<DrugBatch[]> {
  const params = drugId ? { drugId } : {};
  const res = await api.get('/pharmacy/inventory/batches', { params });
  return res.data;
}

export async function listExpiring(withinDays = 60): Promise<DrugBatch[]> {
  const res = await api.get('/pharmacy/inventory/expiring', { params: { withinDays } });
  return res.data;
}

export async function receiveBatch(body: ReceiveBatchBody): Promise<DrugBatch> {
  const res = await api.post('/pharmacy/inventory/batches', body);
  return res.data;
}

/* ---------------- Walk-in OTC sale ---------------- */

export type OtcSaleLine = { drugServiceItemId: string; quantity: number };

export type OtcSaleResponse = {
  dispense: import('./api').Dispense;
  visitId: string;
  visitDisplayId: string;
};

export async function createOtcSale(patientId: string, lines: OtcSaleLine[]): Promise<OtcSaleResponse> {
  const res = await api.post('/pharmacy/walk-in-sales', { patientId, lines });
  return res.data;
}
