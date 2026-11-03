import { apiClient, type RootEntity } from './client';
import type { AgentResponse, Page } from '../types';

export interface ListAgentsParams {
  search?: string;
  status?: 'true' | 'false';
  page?: number;
  size?: number;
  sort?: string;
}

export function listAgents(params: ListAgentsParams) {
  return apiClient.get<Page<AgentResponse>>('/agents', { params }).then((r) => r.data);
}

export function getAgent(id: number) {
  return apiClient.get<AgentResponse>(`/agents/${id}`).then((r) => r.data);
}

export interface RegisterAgentResult {
  agentId: number;
  apiKey: string; // yalnız bu yanıtta görünür; Manager'da hash'i saklanır
}

export function registerAgent(hostName: string) {
  return apiClient
    .post<RootEntity<RegisterAgentResult>>('/agents/register', { hostName })
    .then((r) => r.data.data);
}
