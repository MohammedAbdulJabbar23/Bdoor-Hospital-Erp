import axios, { AxiosError } from 'axios';
import { useAuthStore } from '../auth/authStore';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().clear();
    }
    return Promise.reject(error);
  },
);

export type ApiError = {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  violations: { field: string; message: string }[];
};

export function extractApiError(err: unknown): ApiError | null {
  if (err && typeof err === 'object' && 'response' in err) {
    const res = (err as AxiosError<ApiError>).response;
    return res?.data ?? null;
  }
  return null;
}
