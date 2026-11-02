import { useEffect, useState } from 'react';
import { usePolling } from '../hooks/usePolling';
import { Link } from 'react-router-dom';
import { getDashboardSummary, getEventsTimeline } from '../api/dashboard';
import { listAgents } from '../api/agents';
import { listEvents } from '../api/events';
import { StatusDot } from '../components/StatusDot';
import { isOnlineToStatus, severityToLevel } from '../lib/mappers';
import type { AgentResponse, EventResponse, EventSeverityLevel } from '../types';

const typeBadgeClasses: Record<EventSeverityLevel, string> = {
  info: 'bg-primary/15 text-primary border-primary/30',
  low: 'bg-success/15 text-success border-success/30',
  medium: 'bg-warning/15 text-warning border-warning/30',
  high: 'bg-high-severity/15 text-high-severity border-high-severity/30',
  critical: 'bg-error/15 text-error border-error/30',
};

function useIsoClock() {
  const [now, setNow] = useState(() => new Date().toISOString().replace('T', ' ').substring(0, 19) + 'Z');
  useEffect(() => {
    const id = setInterval(() => {
      setNow(new Date().toISOString().replace('T', ' ').substring(0, 19) + 'Z');
    }, 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}

function formatHour(hour: string) {
  return new Date(hour).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
}

export function DashboardPage() {
  const clock = useIsoClock();
  const { data, loading, error } = usePolling(
    () =>
      Promise.all([
        getDashboardSummary(),
        getEventsTimeline(24),
        listAgents({ page: 0, size: 10, sort: 'lastSeen,desc' }),
        listEvents({ page: 0, size: 8, sort: 'timestamp,desc' }),
      ]),
    [],
  );

  if (loading) {
    return <div className="p-container-padding text-on-surface-variant font-body-sm text-body-sm">Loading...</div>;
  }
  if (!data) {
    return <div className="p-container-padding text-error font-body-sm text-body-sm">{error ?? 'Failed to load data.'}</div>;
  }

  const [summary, timeline, agentsPage, eventsPage] = data;
  const agents: AgentResponse[] = agentsPage.content;
  const recentEvents: EventResponse[] = eventsPage.content;

  const totals = timeline.map((b) => b.low + b.medium + b.high + b.critical);
  const maxTotal = Math.max(1, ...totals);

  return (
    <div className="p-container-padding">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="font-headline-lg text-headline-lg font-bold text-text-primary">System Overview</h2>
          <p className="font-data-mono text-data-mono text-on-surface-variant mt-1">
            LAST_UPDATE: <span className="text-primary">{clock}</span>
          </p>
        </div>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-element-gap mb-element-gap">
        <div className="bg-surface border border-border rounded p-6">
          <div className="flex justify-between items-start mb-2">
            <span className="font-label-caps text-label-caps text-on-surface-variant">Total Agents</span>
            <span className="material-symbols-outlined text-on-surface-variant">dns</span>
          </div>
          <div className="font-headline-lg text-headline-lg font-bold text-text-primary">{summary.totalAgents}</div>
        </div>

        <div className="bg-surface border border-border rounded p-6 relative overflow-hidden">
          <div className="absolute inset-0 bg-success/5 border-l-2 border-success" />
          <div className="relative z-10">
            <div className="flex justify-between items-start mb-2">
              <span className="font-label-caps text-label-caps text-on-surface-variant">Online Agents</span>
              <div className="w-2 h-2 rounded-full bg-success animate-pulse mt-1" />
            </div>
            <div className="font-headline-lg text-headline-lg font-bold text-success">{summary.onlineAgents}</div>
          </div>
        </div>

        <div className="bg-surface border border-border rounded p-6 relative overflow-hidden">
          <div className="absolute inset-0 bg-primary/5 border-l-2 border-primary" />
          <div className="relative z-10">
            <div className="flex justify-between items-start mb-2">
              <span className="font-label-caps text-label-caps text-on-surface-variant">Today's Events</span>
              <span className="material-symbols-outlined text-primary">query_stats</span>
            </div>
            <div className="font-headline-lg text-headline-lg font-bold text-primary">{summary.eventsTotal}</div>
          </div>
        </div>

        <div className="bg-surface border border-border rounded p-6 relative overflow-hidden">
          <div className="absolute inset-0 bg-error/5 border-l-2 border-error" />
          <div className="relative z-10">
            <div className="flex justify-between items-start mb-2">
              <span className="font-label-caps text-label-caps text-on-surface-variant">Critical Events</span>
              <span className="material-symbols-outlined text-error">warning</span>
            </div>
            <div className="font-headline-lg text-headline-lg font-bold text-error">{summary.criticalEventsToday}</div>
          </div>
        </div>
      </div>

      {/* Middle Block */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-element-gap mb-element-gap">
        <div className="lg:col-span-2 bg-surface border border-border rounded flex flex-col h-[320px]">
          <div className="p-4 border-b border-border flex justify-between items-center">
            <h3 className="font-headline-sm text-headline-sm font-semibold text-text-primary">Events Timeline (24h)</h3>
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-primary" />
              <span className="font-label-caps text-label-caps text-on-surface-variant">All</span>
              <span className="w-2 h-2 rounded-full bg-error ml-2" />
              <span className="font-label-caps text-label-caps text-on-surface-variant">Critical</span>
            </div>
          </div>
          <div className="flex-1 p-4 flex items-end relative overflow-hidden">
            <div className="absolute bottom-4 left-4 right-4 top-4 border-l border-b border-border flex items-end justify-between px-2 pb-1">
              {timeline.map((bucket) => {
                const total = bucket.low + bucket.medium + bucket.high + bucket.critical;
                return (
                  <div
                    key={bucket.hour}
                    className="w-4 bg-primary/20 hover:bg-primary/40 transition-colors relative group cursor-pointer border-t border-primary"
                    style={{ height: `${(total / maxTotal) * 100}%` }}
                    title={`${formatHour(bucket.hour)}: ${total} events`}
                  >
                    {bucket.critical > 0 && total > 0 && (
                      <div
                        className="absolute bottom-0 w-full bg-error/50 border-t border-error"
                        style={{ height: `${(bucket.critical / total) * 100}%` }}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        <div className="bg-surface border border-border rounded flex flex-col h-[320px]">
          <div className="p-4 border-b border-border">
            <h3 className="font-headline-sm text-headline-sm font-semibold text-text-primary">Agent Status</h3>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            {agents.length === 0 && (
              <p className="p-2 font-body-sm text-body-sm text-on-surface-variant">No agents registered yet.</p>
            )}
            <ul className="space-y-1">
              {agents.map((agent) => {
                const status = isOnlineToStatus(agent.isOnline);
                return (
                  <li key={agent.id}>
                    <Link
                      to={`/agents/${agent.id}`}
                      className="flex items-center justify-between p-2 hover:bg-hover rounded transition-colors group"
                    >
                    <div className="flex items-center gap-3">
                      <StatusDot status={status} />
                      <div>
                        <div
                          className={`font-data-mono text-data-mono group-hover:text-primary transition-colors ${
                            status === 'offline' ? 'text-on-surface-variant' : 'text-text-primary'
                          }`}
                        >
                          {agent.hostname}
                        </div>
                        <div className="font-body-sm text-body-sm text-on-surface-variant">
                          {agent.lastSeen ? new Date(agent.lastSeen).toLocaleString() : 'Never'}
                        </div>
                      </div>
                    </div>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      </div>

      {/* Recent Events Table */}
      <div className="bg-surface border border-border rounded overflow-hidden">
        <div className="p-4 border-b border-border flex justify-between items-center bg-surface-container-low">
          <h3 className="font-headline-sm text-headline-sm font-semibold text-text-primary">Recent Events</h3>
          <Link to="/events" className="font-label-caps text-label-caps text-primary hover:text-primary-container transition-colors">
            VIEW ALL
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface font-label-caps text-label-caps text-on-surface-variant border-b border-border">
                <th className="py-3 px-4 w-32">Time</th>
                <th className="py-3 px-4 w-48">Agent</th>
                <th className="py-3 px-4 w-32">Type</th>
                <th className="py-3 px-4 w-24 text-center">Severity</th>
                <th className="py-3 px-4">Description</th>
              </tr>
            </thead>
            <tbody className="font-body-sm text-body-sm text-on-surface">
              {recentEvents.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-6 px-4 text-center text-on-surface-variant">No events yet.</td>
                </tr>
              )}
              {recentEvents.map((event) => {
                const level = severityToLevel(event.severity);
                return (
                  <tr
                    key={event.id}
                    className={`border-b border-border/50 hover:bg-hover transition-colors ${
                      level === 'critical' ? 'bg-error/5' : ''
                    }`}
                  >
                    <td className="py-2 px-4 font-data-mono text-data-mono text-on-surface-variant">
                      {new Date(event.timestamp).toLocaleTimeString()}
                    </td>
                    <td
                      className={`py-2 px-4 font-data-mono text-data-mono ${
                        level === 'critical' ? 'text-error' : 'text-primary'
                      }`}
                    >
                      {event.agentHostname}
                    </td>
                    <td className="py-2 px-4">
                      <span
                        className={`px-2 py-0.5 rounded font-label-caps text-label-caps inline-block border ${typeBadgeClasses[level]}`}
                      >
                        {event.eventType}
                      </span>
                    </td>
                    <td className="py-2 px-4 text-center">
                      <StatusDot
                        status={level === 'critical' ? 'unresponsive' : level === 'medium' ? 'high-load' : 'online'}
                      />
                    </td>
                    <td className="py-2 px-4 text-on-surface-variant font-data-mono text-data-mono">
                      <div className="truncate max-w-[480px]" title={event.rawData}>{event.rawData}</div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
