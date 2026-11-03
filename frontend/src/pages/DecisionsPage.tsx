import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { listDecisions, revokeDecision } from '../api/decisions';
import { BlockIpDialog } from '../components/BlockIpDialog';
import { usePolling } from '../hooks/usePolling';
import type { DecisionResponse, DecisionStatus } from '../types';

type Tab = 'active' | 'inactive';

const statusClasses: Record<DecisionStatus, string> = {
  ACTIVE: 'bg-error/15 text-error border-error/30',
  EXPIRED: 'bg-surface-variant text-on-surface-variant border-border',
  REVOKED: 'bg-primary/15 text-primary border-primary/30',
};

// Saniyede bir yenilenen saat: kalan süre sayacı için
function useNow() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}

function formatRemaining(ms: number) {
  if (ms <= 0) return 'expiring...';
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s % 60}s`;
  return `${s}s`;
}

function originText(d: DecisionResponse) {
  if (d.origin === 'MANUAL') return `Manual · ${d.createdBy ?? '?'}`;
  if (d.origin === 'SCENARIO') return d.scenario ?? 'Scenario';
  return d.origin;
}

export function DecisionsPage() {
  const now = useNow();
  const [tab, setTab] = useState<Tab>('active');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    setPage(0);
  }, [tab, search]);

  const { data: pageData, loading, error, refresh } = usePolling(
    () => listDecisions({ status: tab, value: search.trim() || undefined, page, size: 20 }),
    [tab, search, page],
    5000,
  );

  const decisions = pageData?.content ?? [];

  const revoke = (d: DecisionResponse) => {
    if (!window.confirm(`Unblock ${d.value}? Agents will remove the firewall rule within ~10 seconds.`)) return;
    setBusyId(d.id);
    setActionError(null);
    revokeDecision(d.id)
      .then(refresh)
      .catch((err) => setActionError(errorMessage(err, 'Engel kaldırılamadı')))
      .finally(() => setBusyId(null));
  };

  return (
    <div className="p-container-padding flex-1 flex flex-col gap-4">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="font-headline-lg text-headline-lg text-text-primary tracking-tight">Blocks</h2>
          <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
            Ban decisions enforced by agents through ufw. Expired bans are removed automatically.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[16px]">
              search
            </span>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search IP..."
              className="w-48 bg-background border border-border rounded pl-9 pr-3 py-1.5 font-body-sm text-body-sm text-text-primary focus:outline-none focus:border-primary-container placeholder:text-on-surface-variant"
            />
          </div>
          <button
            onClick={() => setDialogOpen(true)}
            className="bg-primary-container text-on-primary-container font-body-sm font-semibold px-4 py-1.5 rounded hover:bg-primary transition-colors flex items-center gap-1"
          >
            <span className="material-symbols-outlined text-[18px]">block</span>
            Block IP
          </button>
        </div>
      </div>

      <div className="flex items-center gap-8 border-b border-border">
        {(['active', 'inactive'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`pb-3 border-b-2 font-body-md text-body-md transition-colors ${
              tab === t ? 'border-primary text-primary font-semibold' : 'border-transparent text-on-surface-variant hover:text-text-primary'
            }`}
          >
            {t === 'active' ? 'Active' : 'History'}
          </button>
        ))}
      </div>

      {actionError && <p className="font-body-sm text-body-sm text-error">{actionError}</p>}

      <div className="bg-surface border border-border rounded overflow-x-auto">
        {loading && !pageData && <div className="p-6 text-on-surface-variant font-body-sm text-body-sm">Loading...</div>}
        {error && !pageData && <div className="p-6 text-error font-body-sm text-body-sm">Failed to load data.</div>}
        {pageData && (
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-border font-label-caps text-label-caps text-on-surface-variant">
                <th className="py-3 px-4">IP / CIDR</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Source</th>
                <th className="py-3 px-4">Applies to</th>
                <th className="py-3 px-4">Created</th>
                <th className="py-3 px-4">{tab === 'active' ? 'Remaining' : 'Ended'}</th>
                {tab === 'active' && <th className="py-3 px-4 w-28" />}
              </tr>
            </thead>
            <tbody className="divide-y divide-border font-body-sm text-body-sm">
              {decisions.length === 0 && (
                <tr>
                  <td colSpan={7} className="py-8 px-4 text-center text-on-surface-variant">
                    {tab === 'active' ? 'No active blocks.' : 'No past blocks.'}
                  </td>
                </tr>
              )}
              {decisions.map((d) => (
                <tr key={d.id} className="hover:bg-hover">
                  <td className="py-2 px-4">
                    <Link to={`/events?search=${encodeURIComponent(d.value)}`} className="font-data-mono text-data-mono text-text-primary hover:text-primary">
                      {d.value}
                    </Link>
                    <div className="text-on-surface-variant text-[11px]">#{d.id}</div>
                  </td>
                  <td className="py-2 px-4">
                    <span className={`px-2 py-0.5 rounded-sm border font-label-caps text-label-caps ${statusClasses[d.status]}`}>
                      {d.status}
                    </span>
                  </td>
                  <td className="py-2 px-4">
                    <div className="text-on-surface">{originText(d)}</div>
                    <div className="text-on-surface-variant text-[11px] truncate max-w-[260px]" title={d.reason ?? ''}>
                      {d.alertEventsCount != null
                        ? `${d.alertEventsCount} events on ${d.alertAgentHostname ?? '?'}`
                        : d.reason ?? ''}
                    </div>
                  </td>
                  <td className="py-2 px-4 text-on-surface">
                    {d.scope === 'GLOBAL' ? (
                      'All agents'
                    ) : (
                      <Link to={`/agents/${d.targetAgentId}`} className="text-primary hover:underline">
                        {d.targetAgentHostname}
                      </Link>
                    )}
                  </td>
                  <td className="py-2 px-4 text-on-surface-variant whitespace-nowrap">{new Date(d.createdAt).toLocaleString()}</td>
                  <td className="py-2 px-4 whitespace-nowrap">
                    {tab === 'active' ? (
                      <span className="font-data-mono text-data-mono text-warning" title={new Date(d.expiresAt).toLocaleString()}>
                        {formatRemaining(new Date(d.expiresAt).getTime() - now)}
                      </span>
                    ) : (
                      <span className="text-on-surface-variant">
                        {d.revokedAt ? new Date(d.revokedAt).toLocaleString() : '—'}
                        {d.status === 'REVOKED' && d.revokedBy ? ` · by ${d.revokedBy}` : ''}
                      </span>
                    )}
                  </td>
                  {tab === 'active' && (
                    <td className="py-2 px-4 text-right">
                      <button
                        onClick={() => revoke(d)}
                        disabled={busyId === d.id}
                        className="px-3 py-1 border border-border rounded hover:bg-hover hover:border-primary text-text-primary disabled:opacity-50"
                      >
                        {busyId === d.id ? '...' : 'Unblock'}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {pageData && pageData.totalPages > 1 && (
          <div className="flex items-center justify-between p-3 border-t border-border font-body-sm text-body-sm text-on-surface-variant">
            <span>{`Page ${pageData.number + 1}/${pageData.totalPages} · ${pageData.totalElements} total`}</span>
            <div className="flex gap-2">
              <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={pageData.first} className="px-2 py-1 border border-border rounded hover:bg-hover disabled:opacity-50">
                Prev
              </button>
              <button onClick={() => setPage((p) => p + 1)} disabled={pageData.last} className="px-2 py-1 border border-border rounded hover:bg-hover disabled:opacity-50">
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {dialogOpen && <BlockIpDialog onClose={() => setDialogOpen(false)} onCreated={refresh} />}
    </div>
  );
}
