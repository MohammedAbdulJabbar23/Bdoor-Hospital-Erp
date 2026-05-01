import { create } from 'zustand';
import { persist } from 'zustand/middleware';

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

export type AuthUser = {
  id: string;
  username: string;
  fullName: string;
  roles: Role[];
};

type AuthState = {
  token: string | null;
  expiresAt: string | null;
  user: AuthUser | null;
  set: (token: string, expiresAt: string, user: AuthUser) => void;
  clear: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      expiresAt: null,
      user: null,
      set: (token, expiresAt, user) => set({ token, expiresAt, user }),
      clear: () => set({ token: null, expiresAt: null, user: null }),
    }),
    { name: 'hms-auth' },
  ),
);

export function hasRole(role: Role): boolean {
  return useAuthStore.getState().user?.roles.includes(role) ?? false;
}
