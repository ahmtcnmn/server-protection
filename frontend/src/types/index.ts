export type AgentStatus = 'online' | 'offline' | 'high-load' | 'unresponsive';

export type EventSeverityLevel = 'info' | 'low' | 'medium' | 'high' | 'critical';

export interface AgentResponse {
  id: number;
  hostname: string;
  isOnline: boolean;
  lastSeen: string | null;
  createdAt: string;
}

export interface EventResponse {
  id: number;
  agentId: number;
  agentHostname: string;
  eventType: string;
  timestamp: string;
  severity: number;
  rawData: string;
  sourceIp: string | null;
}

export interface DashboardSummary {
  totalAgents: number;
  onlineAgents: number;
  eventsTotal: number;
  criticalEventsToday: number;
}

export interface TimelinePoint {
  hour: string;
  low: number;
  medium: number;
  high: number;
  critical: number;
}

export interface EventTypeCount {
  type: string;
  count: number;
  percentage: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

export type CommandStatus = 'PENDING' | 'DELIVERED' | 'COMPLETED' | 'FAILED';

export interface CommandResponse {
  id: number;
  commandType: string;
  payload: string | null;
  status: CommandStatus;
  result: string | null;
  createdAt: string;
  completedAt: string | null;
}

// Agent'taki detector'ların ve executor'ların ürettiği event tipleri
export const EVENT_TYPES = ['SQL_INJECTION', 'DB_AUTH_FAILURE', 'SUSPICIOUS_SYSTEM_ACTIVITY', 'IP_BLOCKED'] as const;

// Agent'ın desteklediği komutlar (CommandExecutor implementasyonları)
export const COMMAND_TYPES = ['PING', 'BLOCK_IP'] as const;
