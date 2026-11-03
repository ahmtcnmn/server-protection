import { apiClient } from './client';
import type { AllowlistCreated, AllowlistEntry } from '../types';

export function listAllowlist() {
  return apiClient.get<AllowlistEntry[]>('/allowlist').then((r) => r.data);
}

export interface CreateAllowlistRequest {
  value: string;
  // boş → süresiz
  durationMinutes?: number;
  reason?: string;
}

export function createAllowlistEntry(body: CreateAllowlistRequest) {
  return apiClient.post<AllowlistCreated>('/allowlist', body).then((r) => r.data);
}

export function deleteAllowlistEntry(id: number) {
  return apiClient.delete(`/allowlist/${id}`);
}
