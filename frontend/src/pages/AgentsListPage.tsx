import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listAgents } from '../api/agents';
import { usePolling } from '../hooks/usePolling';
import { StatusDot } from '../components/StatusDot';
import { isOnlineToStatus } from '../lib/mappers';

type StatusFilter = 'all' | 'true' | 'false';

export function AgentsListPage() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    setPage(0);
  }, [statusFilter, query]);

  const { data: pageData, loading, error } = usePolling(
    () =>
      listAgents({
        search: query || undefined,
        status: statusFilter === 'all' ? undefined : statusFilter,
        page,
        size: 10,
      }),
    [statusFilter, query, page],
  );

  const agents = pageData?.content ?? [];

  return (
    <div className="p-container-padding flex-1 flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <h2 className="font-headline-lg text-headline-lg text-text-primary tracking-tight">Agents</h2>
        <div className="flex items-center gap-3">
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[16px]">
              filter_list
            </span>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
              className="appearance-none bg-surface border border-border rounded pl-9 pr-8 py-1.5 font-body-sm text-body-sm text-text-primary focus:outline-none focus:border-primary-container transition-colors cursor-pointer"
            >
              <option value="all">All Status</option>
              <option value="true">Online</option>
              <option value="false">Offline</option>
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-on-surface-variant text-[16px] pointer-events-none">
              arrow_drop_down
            </span>
          </div>
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[16px]">
              search
            </span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="w-48 bg-background border border-border rounded pl-9 pr-3 py-1.5 font-body-sm text-body-sm text-text-primary focus:outline-none focus:border-primary-container transition-colors placeholder:text-on-surface-variant"
              placeholder="Filter agents..."
              type="text"
            />
          </div>
        </div>
      </div>

      <div className="bg-surface border border-border rounded-lg flex-1 flex flex-col overflow-hidden">
        {loading && !pageData && <div className="p-6 text-on-surface-variant font-body-sm text-body-sm">Loading...</div>}
        {!loading && error && !pageData && <div className="p-6 text-error font-body-sm text-body-sm">Failed to load data.</div>}
        {pageData && (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-border">
                  <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant w-12 text-center">Status</th>
                  <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant">Hostname</th>
                  <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant">Last Seen</th>
                  <th className="py-3 px-4 font-label-caps text-label-caps text-on-surface-variant">Registered</th>
                </tr>
              </thead>
              <tbody className="font-data-mono text-data-mono text-text-primary divide-y divide-border">
                {agents.length === 0 && (
                  <tr>
                    <td colSpan={4} className="py-6 px-4 text-center text-on-surface-variant font-body-sm text-body-sm">
                      No agents found.
                    </td>
                  </tr>
                )}
                {agents.map((agent) => {
                  const status = isOnlineToStatus(agent.isOnline);
                  return (
                    <tr
                      key={agent.id}
                      onClick={() => navigate(`/agents/${agent.id}`)}
                      className={`hover:bg-hover transition-colors group cursor-pointer ${
                        status === 'offline' ? 'bg-surface-container-low/50' : ''
                      }`}
                    >
                      <td className="py-2 px-4 text-center">
                        <StatusDot status={status} className="mx-auto" />
                      </td>
                      <td className={`py-2 px-4 ${status === 'offline' ? 'text-on-surface-variant' : ''}`}>{agent.hostname}</td>
                      <td className={`py-2 px-4 ${status === 'offline' ? 'text-warning' : 'text-on-surface-variant'}`}>
                        {agent.lastSeen ? new Date(agent.lastSeen).toLocaleString() : '—'}
                      </td>
                      <td className="py-2 px-4 text-on-surface-variant">{new Date(agent.createdAt).toLocaleString()}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-auto border-t border-border p-3 flex items-center justify-between bg-surface-container-lowest">
          <span className="font-body-sm text-body-sm text-on-surface-variant">
            {pageData ? `Showing ${pageData.numberOfElements} of ${pageData.totalElements} agents` : ''}
          </span>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={!pageData || pageData.first}
              className="p-1 rounded text-on-surface-variant hover:bg-hover hover:text-text-primary disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={!pageData || pageData.last}
              className="p-1 rounded text-on-surface-variant hover:bg-hover hover:text-text-primary disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
