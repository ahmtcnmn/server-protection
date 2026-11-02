import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listAgents } from '../api/agents';
import { listEvents } from '../api/events';
import { SeverityBadge } from '../components/SeverityBadge';
import { usePolling } from '../hooks/usePolling';
import { severityToLevel } from '../lib/mappers';
import { EVENT_TYPES, type EventResponse } from '../types';

function formatRawData(rawData: string) {
  try {
    return JSON.stringify(JSON.parse(rawData), null, 2);
  } catch {
    return rawData;
  }
}

// <input type="datetime-local"> yerel saat verir; API ISO-8601 (UTC) bekler
function toIso(local: string) {
  return local ? new Date(local).toISOString() : undefined;
}

const severityOptions = [
  { label: 'All', value: undefined },
  { label: 'Med+', value: 3 },
  { label: 'High+', value: 4 },
  { label: 'Crit', value: 5 },
] as const;

const inputClass =
  'bg-background border border-border text-on-surface font-body-sm text-body-sm rounded px-2 py-1.5 focus:outline-none focus:border-primary-container focus:ring-1 focus:ring-primary-container h-8';

export function EventsListPage() {
  // ?agentId=1 ile gelinirse (ör. agent detayından) filtre hazır gelsin
  const [params] = useSearchParams();
  const [agentId, setAgentId] = useState<string>(params.get('agentId') ?? '');
  const [type, setType] = useState('');
  const [search, setSearch] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [severityMin, setSeverityMin] = useState<number | undefined>(undefined);
  const [live, setLive] = useState(true);
  const [page, setPage] = useState(0);
  const [selectedEvent, setSelectedEvent] = useState<EventResponse | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    setPage(0);
  }, [agentId, type, search, from, to, severityMin]);

  const { data: agentsPage } = usePolling(() => listAgents({ page: 0, size: 100, sort: 'hostname,asc' }), [], 0);

  const { data: pageData, loading, error } = usePolling(
    () =>
      listEvents({
        agentId: agentId ? Number(agentId) : undefined,
        type: type || undefined,
        severityMin,
        from: toIso(from),
        to: toIso(to),
        search: search || undefined,
        page,
        size: 20,
        sort: 'timestamp,desc',
      }),
    [agentId, type, search, from, to, severityMin, page],
    live ? 10000 : 0,
  );

  const events = pageData?.content ?? [];
  const hasFilter = agentId || type || search || from || to || severityMin !== undefined;

  const clearFilters = () => {
    setAgentId('');
    setType('');
    setSearch('');
    setFrom('');
    setTo('');
    setSeverityMin(undefined);
  };

  const copyRaw = () => {
    if (!selectedEvent) return;
    navigator.clipboard
      ?.writeText(selectedEvent.rawData)
      .then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      })
      .catch(() => {});
  };

  return (
    <div className="flex-1 flex flex-col relative overflow-hidden">
      {/* Header & Filter Bar */}
      <div className="flex-none p-container-padding pb-0 border-b border-border bg-surface">
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-4 gap-4">
          <div>
            <h2 className="font-headline-lg text-headline-lg font-bold text-text-primary tracking-tight">Events</h2>
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
              Security events reported by agents.
            </p>
          </div>
          <div className="flex items-center gap-2">
            {hasFilter && (
              <button
                onClick={clearFilters}
                className="flex items-center gap-2 px-3 py-1.5 border border-border text-text-primary font-body-sm text-body-sm rounded hover:bg-hover transition-colors"
              >
                <span className="material-symbols-outlined text-[16px]">filter_alt_off</span>
                Clear filters
              </button>
            )}
            <button
              onClick={() => setLive((v) => !v)}
              className={`flex items-center gap-2 px-3 py-1.5 font-body-sm text-body-sm rounded font-semibold transition-opacity ${
                live
                  ? 'bg-primary-container text-on-primary-container hover:opacity-90'
                  : 'border border-border text-text-primary hover:bg-hover'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">{live ? 'pause' : 'play_arrow'}</span>
              {live ? 'Pause live' : 'Resume live'}
            </button>
          </div>
        </div>

        <div className="flex flex-wrap gap-element-gap mb-4">
          <div className="flex flex-col gap-1 min-w-[150px]">
            <label className="font-label-caps text-label-caps text-on-surface-variant">Agent</label>
            <select value={agentId} onChange={(e) => setAgentId(e.target.value)} className={inputClass}>
              <option value="">All Agents</option>
              {agentsPage?.content.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.hostname}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1 min-w-[200px]">
            <label className="font-label-caps text-label-caps text-on-surface-variant">Event Type</label>
            <select value={type} onChange={(e) => setType(e.target.value)} className={inputClass}>
              <option value="">All Types</option>
              {EVENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1">
            <label className="font-label-caps text-label-caps text-on-surface-variant">Timeframe</label>
            <div className="flex items-center gap-2">
              <input value={from} onChange={(e) => setFrom(e.target.value)} className={`${inputClass} w-[190px]`} type="datetime-local" />
              <span className="text-on-surface-variant">-</span>
              <input value={to} onChange={(e) => setTo(e.target.value)} className={`${inputClass} w-[190px]`} type="datetime-local" />
            </div>
          </div>
          <div className="flex flex-col gap-1">
            <label className="font-label-caps text-label-caps text-on-surface-variant">Min Severity</label>
            <div className="flex bg-background border border-border rounded overflow-hidden h-8">
              {severityOptions.map((opt, i) => (
                <button
                  key={opt.label}
                  onClick={() => setSeverityMin(opt.value)}
                  className={`px-3 text-body-sm font-body-sm ${i < severityOptions.length - 1 ? 'border-r border-border' : ''} ${
                    severityMin === opt.value ? 'bg-primary/15 text-primary' : 'text-on-surface-variant hover:bg-hover'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
          <div className="flex flex-col gap-1 min-w-[200px] flex-1">
            <label className="font-label-caps text-label-caps text-on-surface-variant">Search raw data</label>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="e.g. 1.2.3.4 or /login"
              className={inputClass}
            />
          </div>
        </div>
      </div>

      {/* Data Table */}
      <div className="flex-1 overflow-auto bg-background p-container-padding">
        {loading && !pageData && <div className="text-on-surface-variant font-body-sm text-body-sm">Loading...</div>}
        {error && !pageData && <div className="text-error font-body-sm text-body-sm">Failed to load data.</div>}
        {pageData && (
          <>
            <div className="w-full border border-border rounded bg-surface overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead className="sticky top-0 bg-surface z-10 shadow-[0_1px_0_0_#2A333D]">
                  <tr>
                    <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant w-[180px]">Timestamp</th>
                    <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant w-[140px]">Severity</th>
                    <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant w-[150px]">Agent</th>
                    <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant w-[120px]">Type</th>
                    <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant w-[140px]">Source IP</th>
                    <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant">Raw Data (Truncated)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border font-body-sm text-body-sm">
                  {events.length === 0 && (
                    <tr>
                      <td colSpan={6} className="py-6 px-4 text-center text-on-surface-variant">
                        No events match the filters.
                      </td>
                    </tr>
                  )}
                  {events.map((event) => (
                    <tr
                      key={event.id}
                      className={`hover:bg-hover transition-colors cursor-pointer ${selectedEvent?.id === event.id ? 'bg-hover' : ''}`}
                      onClick={() => setSelectedEvent(event)}
                    >
                      <td className="py-2 px-4 text-on-surface-variant whitespace-nowrap">
                        {new Date(event.timestamp).toLocaleString()}
                      </td>
                      <td className="py-2 px-4">
                        <SeverityBadge severity={severityToLevel(event.severity)} />
                      </td>
                      <td className="py-2 px-4">
                        <Link
                          className="text-primary hover:underline"
                          to={`/agents/${event.agentId}`}
                          onClick={(e) => e.stopPropagation()}
                        >
                          {event.agentHostname}
                        </Link>
                      </td>
                      <td className="py-2 px-4">
                        <span className="border border-border text-on-surface-variant px-1.5 py-0.5 rounded text-[10px] uppercase tracking-wider whitespace-nowrap">
                          {event.eventType}
                        </span>
                      </td>
                      <td className="py-2 px-4 font-data-mono text-data-mono text-on-surface whitespace-nowrap">
                        {event.sourceIp ?? '—'}
                      </td>
                      <td className="py-2 px-4">
                        <div className="font-data-mono text-data-mono text-on-surface-variant truncate max-w-[400px]">
                          {event.rawData}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-4 flex justify-between items-center text-on-surface-variant font-body-sm text-body-sm">
              <span>
                {`Showing ${pageData.numberOfElements} of ${pageData.totalElements} events · page ${pageData.number + 1}/${Math.max(1, pageData.totalPages)}`}
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={pageData.first}
                  className="px-2 py-1 border border-border rounded hover:bg-hover disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Prev
                </button>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={pageData.last}
                  className="px-2 py-1 border border-border rounded hover:bg-hover disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Event Detail Drawer */}
      <div
        className={`absolute top-0 right-0 h-full w-full sm:w-[80%] md:w-[40%] bg-surface border-l border-border shadow-[-10px_0_30px_rgba(0,0,0,0.5)] transform transition-transform duration-300 ease-in-out z-20 flex flex-col ${
          selectedEvent ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        {selectedEvent && (
          <>
            <div className="flex items-center justify-between p-4 border-b border-border">
              <div className="flex items-center gap-3">
                <SeverityBadge severity={severityToLevel(selectedEvent.severity)} />
                <h3 className="font-headline-sm text-headline-sm font-bold text-text-primary">Event #{selectedEvent.id}</h3>
              </div>
              <button
                className="text-on-surface-variant hover:text-text-primary transition-colors"
                onClick={() => setSelectedEvent(null)}
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>
            <div className="flex-1 overflow-auto p-4 flex flex-col gap-6">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="font-label-caps text-label-caps text-on-surface-variant mb-1">Timestamp</p>
                  <p className="font-data-mono text-data-mono text-on-surface">
                    {new Date(selectedEvent.timestamp).toLocaleString()}
                  </p>
                </div>
                <div>
                  <p className="font-label-caps text-label-caps text-on-surface-variant mb-1">Agent</p>
                  <Link className="font-data-mono text-data-mono text-primary hover:underline" to={`/agents/${selectedEvent.agentId}`}>
                    {selectedEvent.agentHostname}
                  </Link>
                </div>
                <div>
                  <p className="font-label-caps text-label-caps text-on-surface-variant mb-1">Event Type</p>
                  <p className="font-body-sm text-body-sm text-on-surface">{selectedEvent.eventType}</p>
                </div>
                <div>
                  <p className="font-label-caps text-label-caps text-on-surface-variant mb-1">Severity</p>
                  <p className="font-data-mono text-data-mono text-on-surface">{selectedEvent.severity} / 5</p>
                </div>
                <div>
                  <p className="font-label-caps text-label-caps text-on-surface-variant mb-1">Source IP</p>
                  <p className="font-data-mono text-data-mono text-on-surface">{selectedEvent.sourceIp ?? '—'}</p>
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <div className="flex justify-between items-end">
                  <p className="font-label-caps text-label-caps text-on-surface-variant">Raw Payload</p>
                  <button onClick={copyRaw} className="text-primary font-body-sm text-[11px] hover:underline flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">{copied ? 'check' : 'content_copy'}</span>
                    {copied ? 'Copied' : 'Copy'}
                  </button>
                </div>
                <div className="bg-background border border-border rounded p-4 overflow-x-auto">
                  <pre className="font-data-mono text-data-mono text-text-primary whitespace-pre-wrap break-words">
                    {formatRawData(selectedEvent.rawData)}
                  </pre>
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
