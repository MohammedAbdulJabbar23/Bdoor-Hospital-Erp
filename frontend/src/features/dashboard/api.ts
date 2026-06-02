import { api } from '@/shared/api/client';

export type DashboardSummary = {
  patientsToday: number;
  pendingPayments: number;
  bedsOccupied: number;
  bedsTotal: number;
  activeQueues: number;
  pendingPaymentsCount: number;
  labResultsAwaiting: number;
  bedsExpiringSoon: number;
};

export async function getDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await api.get<DashboardSummary>('/dashboard/summary');
  return data;
}
