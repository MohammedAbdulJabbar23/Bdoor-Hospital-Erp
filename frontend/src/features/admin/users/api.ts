import { api } from '@/shared/api/client';

export type Role =
  | 'ADMIN'
  | 'RECEPTIONIST'
  | 'DOCTOR'
  | 'NURSE'
  | 'CASHIER'
  | 'LAB_STAFF'
  | 'RADIOLOGY_STAFF'
  | 'ECO_STAFF'
  | 'EMERGENCY_STAFF'
  | 'PREMATURE_STAFF'
  | 'PHARMACIST';

export type AppUser = {
  id: string;
  username: string;
  fullName: string;
  active: boolean;
  roles: Role[];
  createdAt: string;
};

export async function listUsers(): Promise<AppUser[]> {
  const res = await api.get('/users');
  return res.data;
}

export async function createUser(body: {
  username: string;
  password: string;
  fullName: string;
  roles: Role[];
}): Promise<AppUser> {
  const res = await api.post('/users', body);
  return res.data;
}
