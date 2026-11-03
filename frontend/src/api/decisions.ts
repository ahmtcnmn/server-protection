import { apiClient } from './client';
import type { DecisionResponse, Page } from '../types';

export interface ListDecisionsParams {
  status?: 'active' | 'inactive';
  value?: string;
  page?: number;
  size?: number;
}

export function listDecisions(params: ListDecisionsParams) {
  return apiClient.get<Page<DecisionResponse>>('/decisions', { params }).then((r) => r.data);
}

export interface CreateDecisionRequest {
  value: string;
  scope: 'GLOBAL' | 'AGENT';
  agentId?: number;
  durationMinutes: number;
  reason?: string;
}

export function createDecision(body: CreateDecisionRequest) {
  return apiClient.post<DecisionResponse>('/decisions', body).then((r) => r.data);
}

export function revokeDecision(id: number) {
  return apiClient.delete<DecisionResponse>(`/decisions/${id}`).then((r) => r.data);
}
