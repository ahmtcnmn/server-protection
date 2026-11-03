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
  // Agent'taki YAML kuralı: hangi kural, hangi alanda, neyi yakaladı (eski event'lerde null)
  rule: string | null;
  matchField: string | null;
  matched: string | null;
  // Parser'ın ayrıştırdığı alanlar (nginx-access: method, url, status, userAgent, referer...)
  parser: string | null;
  fields: Record<string, string> | null;
  observedAt: string | null;
  queued: boolean;
}

export interface DashboardSummary {
  totalAgents: number;
  onlineAgents: number;
  eventsTotal: number;
  criticalEventsToday: number;
  activeBlocks: number;
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

// Agent'ın desteklediği komutlar (CommandExecutor implementasyonları).
// IP engelleme komutla değil, ban kararlarıyla (Blocks sayfası) yapılır.
export const COMMAND_TYPES = ['PING'] as const;

export type DecisionStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';

export interface DecisionResponse {
  id: number;
  value: string;
  scope: 'GLOBAL' | 'AGENT';
  type: string;
  origin: 'SCENARIO' | 'MANUAL' | 'ML';
  scenario: string | null;
  reason: string | null;
  targetAgentId: number | null;
  targetAgentHostname: string | null;
  alertId: number | null;
  alertEventsCount: number | null;
  alertAgentHostname: string | null;
  createdBy: string | null;
  createdAt: string;
  expiresAt: string;
  revokedAt: string | null;
  revokedBy: string | null;
  status: DecisionStatus;
}

// builtIn = true → Manager config'inden gelir (security.whitelisted-ips), id'si yoktur ve silinemez
export interface AllowlistEntry {
  id: number | null;
  value: string;
  reason: string | null;
  createdBy: string | null;
  createdAt: string | null;
  expiresAt: string | null;
  builtIn: boolean;
}

export interface AllowlistCreated {
  entry: AllowlistEntry;
  revokedDecisions: number;
}
