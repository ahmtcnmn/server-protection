import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { clearToken, getToken, setToken } from '../lib/auth';

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

// Aynı anda 401 alan birden çok istek tek bir refresh çağrısını bekler
let refreshing: Promise<string | null> | null = null;

function callRefresh() {
  // apiClient yerine düz axios: bu isteğin 401'i tekrar interceptor'a girmesin
  return axios
    .post<RootEntity<{ token: string }>>('/api/auth/refresh')
    .then((r) => r.data.data.token);
}

/**
 * Cookie'deki refresh token ile yeni access token alır. Başarısızsa null döner.
 * Başka bir sekme aynı anda yenilediyse ilk deneme reddedilebilir; kısa bir beklemeyle bir kez daha denenir.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    refreshing = callRefresh()
      .catch(() => new Promise<string>((resolve) => setTimeout(resolve, 500)).then(callRefresh))
      .then((token) => {
        setToken(token);
        return token;
      })
      .catch(() => {
        clearToken();
        return null;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    const next = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.assign(`/login?next=${next}`);
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthCall = config?.url?.startsWith('/auth/');
    // Access token süresi dolmuş: bir kez yenileyip isteği tekrarla. Login/refresh'in kendi 401'i hariç.
    if (error.response?.status === 401 && config && !isAuthCall && !config._retried) {
      config._retried = true;
      const token = await refreshAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
        return apiClient(config);
      }
      redirectToLogin();
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
