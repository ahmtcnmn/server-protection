import { apiClient } from './client';
import type { EventResponse, EventTypeCount, Page } from '../types';

export interface ListEventsParams {
  agentId?: number;
  type?: string;
  severityMin?: number;
  severityMax?: number;
  from?: string;
  to?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export function listEvents(params: ListEventsParams) {
  return apiClient.get<Page<EventResponse>>('/events', { params }).then((r) => r.data);
}

export function listEventsByAgent(agentId: number, page = 0, size = 20) {
  return apiClient.get<Page<EventResponse>>(`/events/${agentId}/events`, { params: { page, size, sort: 'timestamp,desc' } }).then((r) => r.data);
}

export function getEventTypeBreakdown(agentId: number) {
  return apiClient.get<EventTypeCount[]>(`/events/${agentId}/event-type-breakdown`).then((r) => r.data);
}
