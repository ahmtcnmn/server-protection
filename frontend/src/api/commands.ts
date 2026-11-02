import { apiClient } from './client';
import type { CommandResponse, Page } from '../types';

export function getCommandHistory(agentId: number, page = 0, size = 20) {
  return apiClient
    .get<Page<CommandResponse>>(`/agents/${agentId}/commands`, { params: { page, size, sort: 'createdAt,desc' } })
    .then((r) => r.data);
}

export function sendCommand(agentId: number, commandType: string, payload?: string) {
  return apiClient
    .post<CommandResponse>(`/agents/${agentId}/commands`, { commandType, payload })
    .then((r) => r.data);
}
