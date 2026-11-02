import axios, { AxiosError } from 'axios';
import { clearToken, getToken } from '../lib/auth';

export interface RootEntity<T> {
  result: boolean;
  messages: string[] | null;
  data: T;
}

// Manager'ın GlobalExceptionHandler'ı hatalarda bu gövdeyi döner
export interface ApiError {
  status: number;
  code: string;
  message: string;
}

export const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // Token geçersiz/süresi dolmuş: login'e dön. Login isteğinin kendi 401'i (yanlış şifre) hariç.
    const isLogin = error.config?.url?.includes('/auth/login');
    if (error.response?.status === 401 && !isLogin) {
      clearToken();
      if (window.location.pathname !== '/login') {
        const next = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.assign(`/login?next=${next}`);
      }
    }
    return Promise.reject(error);
  },
);

/** Hata mesajını kullanıcıya gösterilecek kısa bir metne çevirir. */
export function errorMessage(error: unknown, fallback = 'Bir hata oluştu'): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as Partial<ApiError> | undefined;
    if (body?.message) return body.message;
    if (!error.response) return 'Manager\'a ulaşılamıyor';
  }
  return fallback;
}
