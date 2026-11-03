import { apiClient, type RootEntity } from './client';

// Refresh token httpOnly cookie'de durur; tarayıcı /api/auth isteklerinde kendisi gönderir.

export function login(username: string, password: string) {
  return apiClient
    .post<RootEntity<{ token: string }>>('/auth/login', { username, password })
    .then((r) => r.data.data.token);
}

export function logout() {
  return apiClient.post('/auth/logout').catch(() => undefined);
}
