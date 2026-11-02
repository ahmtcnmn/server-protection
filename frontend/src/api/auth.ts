import { apiClient, type RootEntity } from './client';

export function login(username: string, password: string) {
  return apiClient
    .post<RootEntity<{ token: string }>>('/auth/login', { username, password })
    .then((r) => r.data.data.token);
}
