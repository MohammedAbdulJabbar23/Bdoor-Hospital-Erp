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
): Promise<Page<Payment>> {
  const params: Record<string, string | number> = { page, size };
  if (status) params.status = status;
  if (stage) params.stage = stage;
  const res = await api.get('/payments', { params });
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
