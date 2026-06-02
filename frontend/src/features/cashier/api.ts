import { api } from '@/shared/api/client';
import { VisitType } from '../reception/visits/api';

export type PaymentStage =
  | 'INITIAL'
  | 'REFERRAL'
  | 'FINAL'
  | 'STAY_EXTENSION'
  | 'PHARMACY';

export type PaymentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type PaymentMethod = 'CASH' | 'CARD' | 'BANK_TRANSFER' | 'VIP_BYPASS';

export type PaymentLine = {
  serviceItemId: string;
  code: string;
  name: string;
  unitFee: number;
  quantity: number;
  lineTotal: number;
};

export type Payment = {
  id: string;
  paymentDisplayId: string;
  visitId: string;
  visitDisplayId: string;
  visitType: VisitType;
  patientId: string;
  patientMrn: string;
  patientName: string;
  patientWasVip: boolean;
  stage: PaymentStage;
  status: PaymentStatus;
  vipBypass: boolean;
  totalDue: number;
  currency: string;
  paymentMethod: PaymentMethod | null;
  cashierUserId: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
  lines: PaymentLine[];
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function searchPayments(
  status: PaymentStatus | null,
  stage: PaymentStage | null,
  page = 0,
  size = 20,
  q?: string,
): Promise<Page<Payment>> {
  const params: Record<string, string | number> = { page, size };
  if (status) params.status = status;
  if (stage) params.stage = stage;
  if (q && q.trim()) params.q = q.trim();
  const res = await api.get('/payments', { params });
  return res.data;
}

export type PaymentSummary = {
  pendingCount: number;
  pendingTotal: number;
  receivedToday: number;
  approvedTodayCount: number;
  oldestPendingAt: string | null;
  pendingByStage: Partial<Record<PaymentStage, number>>;
};

export async function getPaymentSummary(): Promise<PaymentSummary> {
  const res = await api.get('/payments/summary');
  return res.data;
}

export type ReconciliationBucket = { key: string; total: number; count: number };
export type ReconciliationTotal = { total: number; count: number };

export type Reconciliation = {
  date: string;
  byMethod: ReconciliationBucket[];
  byStage: ReconciliationBucket[];
  grandTotal: ReconciliationTotal;
  vipBypass: ReconciliationTotal;
  cashCollected: ReconciliationTotal;
};

export async function getReconciliation(date?: string): Promise<Reconciliation> {
  const params: Record<string, string> = {};
  if (date) params.date = date;
  const res = await api.get('/payments/reconciliation', { params });
  return res.data;
}

export async function approvePayment(id: string, paymentMethod: Exclude<PaymentMethod, 'VIP_BYPASS'>): Promise<Payment> {
  const res = await api.post(`/payments/${id}/approve`, { paymentMethod });
  return res.data;
}

export async function rejectPayment(id: string, reason: string): Promise<Payment> {
  const res = await api.post(`/payments/${id}/reject`, { reason });
  return res.data;
}
