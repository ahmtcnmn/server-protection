import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getAgent } from '../api/agents';
import { getCommandHistory } from '../api/commands';
import { getEventTypeBreakdown, listEventsByAgent } from '../api/events';
import { SendCommandDialog } from '../components/SendCommandDialog';
import { SeverityBadge } from '../components/SeverityBadge';
import { usePolling } from '../hooks/usePolling';
import { severityToLevel } from '../lib/mappers';
import type { CommandStatus } from '../types';

const tabs = ['Overview', 'Events', 'Command History'] as const;
type Tab = (typeof tabs)[number];

export function AgentDetailPage() {
  const { agentId } = useParams();
  const numericId = Number(agentId);
  const [activeTab, setActiveTab] = useState<Tab>('Overview');
  const [eventsPage, setEventsPage] = useState(0);
  const [commandsPage, setCommandsPage] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);

  const valid = !Number.isNaN(numericId);
  const overview = usePolling(
    () => (valid ? Promise.all([getAgent(numericId), getEventTypeBreakdown(numericId)]) : Promise.reject()),
    [numericId],
  );
  const events = usePolling(
    () => listEventsByAgent(numericId, eventsPage, 20),
    [numericId, eventsPage, activeTab],
    activeTab === 'Events' ? 10000 : 0,
  );
  const commands = usePolling(
    () => getCommandHistory(numericId, commandsPage, 20),
    [numericId, commandsPage, activeTab],
    activeTab === 'Command History' ? 5000 : 0,
  );

  if (overview.loading && !overview.data) {
    return <div className="p-container-padding text-on-surface-variant font-body-sm text-body-sm">Loading...</div>;
  }
  if (!overview.data) {
    return <div className="p-container-padding text-error font-body-sm text-body-sm">Failed to load agent.</div>;
  }

  const [agent, breakdown] = overview.data;
  const isOnline = agent.isOnline;

  return (
    <div className="p-container-padding flex flex-col gap-element-gap">
      {/* Page Header: Agent Identity */}
      <header className="flex flex-col md:flex-row md:items-start justify-between gap-4 border-b border-border pb-6">
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-3">
            <span
              className={`w-2.5 h-2.5 rounded-full ${
                isOnline ? 'bg-success animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.4)]' : 'bg-info'
              }`}
            />
            <h1 className="font-data-mono text-headline-lg font-semibold tracking-tight text-text-primary">{agent.hostname}</h1>
          </div>
          <div className="flex items-center gap-4 font-body-sm text-body-sm text-on-surface-variant">
            <span className="flex items-center gap-1">
              <span className="material-symbols-outlined text-[14px]">cell_tower</span> {isOnline ? 'Online' : 'Offline'}
            </span>
            <span className="w-1 h-1 rounded-full bg-border" />
            <span className="flex items-center gap-1">
              <span className="material-symbols-outlined text-[14px]">schedule</span> Last seen:{' '}
              {agent.lastSeen ? new Date(agent.lastSeen).toLocaleString() : 'Never'}
            </span>
            <span className="w-1 h-1 rounded-full bg-border" />
            <span className="flex items-center gap-1 font-data-mono text-data-mono">ID: {agent.id}</span>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Link
            to={`/events?agentId=${agent.id}`}
            className="px-4 py-2 bg-surface text-text-primary font-headline-sm text-headline-sm border border-border hover:bg-hover hover:border-outline transition-colors rounded flex items-center gap-2"
          >
            <span className="material-symbols-outlined text-[18px]">filter_list</span>
            All events
          </Link>
          <button
            onClick={() => setDialogOpen(true)}
            className="px-4 py-2 bg-primary-container text-on-primary-container font-headline-sm text-headline-sm font-semibold hover:bg-primary transition-colors rounded flex items-center gap-2 shadow-[0_0_15px_rgba(34,211,238,0.15)]">
            <span className="material-symbols-outlined text-[18px]">terminal</span>
            Send Command
          </button>
        </div>
      </header>

      {/* In-Page Tabs */}
      <div className="flex items-center gap-8 border-b border-border">
        {tabs.map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`pb-3 border-b-2 font-body-md text-body-md transition-colors ${
              activeTab === tab
                ? 'border-primary text-primary font-semibold'
                : 'border-transparent text-on-surface-variant hover:text-text-primary'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {activeTab === 'Overview' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-element-gap mt-4">
          <div className="col-span-1 lg:col-span-2 bg-surface border border-border rounded p-6 flex flex-col gap-6">
            <h2 className="font-headline-sm text-headline-sm text-text-primary flex items-center gap-2">
              <span className="material-symbols-outlined text-on-surface-variant text-[20px]">memory</span>
              Agent Info
            </h2>
            <div className="grid grid-cols-2 gap-y-6 gap-x-8">
              <div className="flex flex-col gap-1">
                <span className="font-label-caps text-label-caps text-on-surface-variant">ID</span>
                <span className="font-data-mono text-data-mono text-text-primary">{agent.id}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="font-label-caps text-label-caps text-on-surface-variant">Hostname</span>
                <span className="font-data-mono text-data-mono text-text-primary">{agent.hostname}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="font-label-caps text-label-caps text-on-surface-variant">Status</span>
                <span className="font-data-mono text-data-mono text-text-primary">{isOnline ? 'Online' : 'Offline'}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="font-label-caps text-label-caps text-on-surface-variant">Last Seen</span>
                <span className="font-data-mono text-data-mono text-text-primary">
                  {agent.lastSeen ? new Date(agent.lastSeen).toLocaleString() : 'Never'}
                </span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="font-label-caps text-label-caps text-on-surface-variant">Registered At</span>
                <span className="font-data-mono text-data-mono text-text-primary">{new Date(agent.createdAt).toLocaleString()}</span>
              </div>
            </div>
          </div>

          <div className="col-span-1 bg-surface border border-border rounded p-6 flex flex-col gap-4">
            <h2 className="font-headline-sm text-headline-sm text-text-primary flex items-center justify-between">
              <span className="flex items-center gap-2">
                <span className="material-symbols-outlined text-on-surface-variant text-[20px]">pie_chart</span> Event Type Breakdown
              </span>
            </h2>
            <div className="flex-grow flex flex-col justify-center gap-5 pt-2">
              {breakdown.length === 0 && (
                <p className="font-body-sm text-body-sm text-on-surface-variant">No events recorded.</p>
              )}
              {breakdown.map((item) => (
                <div key={item.type} className="flex flex-col gap-2">
                  <div className="flex justify-between items-end font-body-sm text-body-sm">
                    <span className="text-text-primary flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-primary" /> {item.type}
                    </span>
                    <span className="font-data-mono text-data-mono text-on-surface-variant">
                      {item.percentage.toFixed(1)}% ({item.count})
                    </span>
                  </div>
                  <div className="w-full h-1.5 bg-surface-variant rounded overflow-hidden">
                    <div className="h-full bg-primary" style={{ width: `${item.percentage}%` }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {activeTab === 'Events' && (
        <div className="mt-4 bg-surface border border-border rounded overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-border font-label-caps text-label-caps text-on-surface-variant">
                <th className="py-3 px-4 w-[180px]">Timestamp</th>
                <th className="py-3 px-4 w-[120px]">Severity</th>
                <th className="py-3 px-4 w-[200px]">Type</th>
                <th className="py-3 px-4 w-[140px]">Source IP</th>
                <th className="py-3 px-4">Raw Data</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border font-body-sm text-body-sm">
              {events.data?.content.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-6 px-4 text-center text-on-surface-variant">No events from this agent.</td>
                </tr>
              )}
              {events.data?.content.map((event) => (
                <tr key={event.id} className="hover:bg-hover">
                  <td className="py-2 px-4 text-on-surface-variant whitespace-nowrap">{new Date(event.timestamp).toLocaleString()}</td>
                  <td className="py-2 px-4"><SeverityBadge severity={severityToLevel(event.severity)} /></td>
                  <td className="py-2 px-4 text-on-surface">{event.eventType}</td>
                  <td className="py-2 px-4 font-data-mono text-data-mono">{event.sourceIp ?? '—'}</td>
                  <td className="py-2 px-4">
                    <div className="font-data-mono text-data-mono text-on-surface-variant truncate max-w-[480px]" title={event.rawData}>
                      {event.rawData}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={events.data} onPage={setEventsPage} />
        </div>
      )}

      {activeTab === 'Command History' && (
        <div className="mt-4 bg-surface border border-border rounded overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-border font-label-caps text-label-caps text-on-surface-variant">
                <th className="py-3 px-4 w-16">#</th>
                <th className="py-3 px-4 w-[180px]">Created</th>
                <th className="py-3 px-4 w-[120px]">Command</th>
                <th className="py-3 px-4 w-[160px]">Payload</th>
                <th className="py-3 px-4 w-[120px]">Status</th>
                <th className="py-3 px-4">Result</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border font-body-sm text-body-sm">
              {commands.data?.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-6 px-4 text-center text-on-surface-variant">No commands sent to this agent.</td>
                </tr>
              )}
              {commands.data?.content.map((cmd) => (
                <tr key={cmd.id} className="hover:bg-hover">
                  <td className="py-2 px-4 font-data-mono text-data-mono text-on-surface-variant">{cmd.id}</td>
                  <td className="py-2 px-4 text-on-surface-variant whitespace-nowrap">{new Date(cmd.createdAt).toLocaleString()}</td>
                  <td className="py-2 px-4 font-data-mono text-data-mono">{cmd.commandType}</td>
                  <td className="py-2 px-4 font-data-mono text-data-mono">{cmd.payload ?? '—'}</td>
                  <td className="py-2 px-4"><CommandStatusBadge status={cmd.status} /></td>
                  <td className="py-2 px-4 text-on-surface-variant">
                    <div className="truncate max-w-[420px]" title={cmd.result ?? ''}>{cmd.result ?? '—'}</div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={commands.data} onPage={setCommandsPage} />
        </div>
      )}

      {dialogOpen && (
        <SendCommandDialog
          agentId={agent.id}
          hostname={agent.hostname}
          onClose={() => setDialogOpen(false)}
          onSent={() => {
            setActiveTab('Command History');
            commands.refresh();
          }}
        />
      )}
    </div>
  );
}


const commandStatusClasses: Record<CommandStatus, string> = {
  PENDING: 'bg-warning/15 text-warning border-warning/30',
  DELIVERED: 'bg-primary/15 text-primary border-primary/30',
  COMPLETED: 'bg-success/15 text-success border-success/30',
  FAILED: 'bg-error/15 text-error border-error/30',
};

function CommandStatusBadge({ status }: { status: CommandStatus }) {
  return (
    <span className={`px-2 py-0.5 rounded-sm border font-label-caps text-label-caps ${commandStatusClasses[status] ?? ''}`}>
      {status}
    </span>
  );
}

function Pager({ page, onPage }: { page: { number: number; totalPages: number; first: boolean; last: boolean; totalElements: number } | null; onPage: (p: number) => void }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between p-3 border-t border-border font-body-sm text-body-sm text-on-surface-variant">
      <span>{`Page ${page.number + 1}/${page.totalPages} · ${page.totalElements} total`}</span>
      <div className="flex gap-2">
        <button
          onClick={() => onPage(Math.max(0, page.number - 1))}
          disabled={page.first}
          className="px-2 py-1 border border-border rounded hover:bg-hover disabled:opacity-50"
        >
          Prev
        </button>
        <button
          onClick={() => onPage(page.number + 1)}
          disabled={page.last}
          className="px-2 py-1 border border-border rounded hover:bg-hover disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  );
}
