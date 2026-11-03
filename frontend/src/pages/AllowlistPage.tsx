import { useState } from 'react';
import { deleteAllowlistEntry, listAllowlist } from '../api/allowlist';
import { errorMessage } from '../api/client';
import { AllowlistDialog } from '../components/AllowlistDialog';
import { usePolling } from '../hooks/usePolling';
import type { AllowlistCreated, AllowlistEntry } from '../types';

function expiresText(e: AllowlistEntry) {
  if (e.builtIn) return 'Built-in';
  if (!e.expiresAt) return 'Permanent';
  return new Date(e.expiresAt).toLocaleString();
}

export function AllowlistPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Süresi dolan girişler Manager'da dakikada bir silinir; liste de ara ara yenilenir
  const { data: entries, loading, error, refresh } = usePolling(() => listAllowlist(), [], 30000);

  const onCreated = (result: AllowlistCreated) => {
    setActionError(null);
    setNotice(
      result.revokedDecisions > 0
        ? `${result.entry.value} added. ${result.revokedDecisions} active block(s) removed.`
        : `${result.entry.value} added.`,
    );
    refresh();
  };

  const remove = (e: AllowlistEntry) => {
    if (e.id == null) return;
    if (!window.confirm(`Remove ${e.value} from the allowlist? It can be banned again afterwards.`)) return;
    setBusyId(e.id);
    setActionError(null);
    setNotice(null);
    deleteAllowlistEntry(e.id)
      .then(refresh)
      .catch((err) => setActionError(errorMessage(err, 'Giriş silinemedi')))
      .finally(() => setBusyId(null));
  };

  return (
    <div className="p-container-padding flex-1 flex flex-col gap-4">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="font-headline-lg text-headline-lg text-text-primary tracking-tight">Allowlist</h2>
          <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
            Addresses that are never banned, neither by detection rules nor manually.
          </p>
        </div>
        <button
          onClick={() => setDialogOpen(true)}
          className="self-start md:self-auto bg-primary-container text-on-primary-container font-body-sm font-semibold px-4 py-1.5 rounded hover:bg-primary transition-colors flex items-center gap-1"
        >
          <span className="material-symbols-outlined text-[18px]">add</span>
          Add address
        </button>
      </div>

      {notice && <p className="font-body-sm text-body-sm text-primary">{notice}</p>}
      {actionError && <p className="font-body-sm text-body-sm text-error">{actionError}</p>}

      <div className="bg-surface border border-border rounded overflow-x-auto">
        {loading && !entries && <div className="p-6 text-on-surface-variant font-body-sm text-body-sm">Loading...</div>}
        {error && !entries && <div className="p-6 text-error font-body-sm text-body-sm">Failed to load data.</div>}
        {entries && (
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-border font-label-caps text-label-caps text-on-surface-variant">
                <th className="py-3 px-4">IP / CIDR</th>
                <th className="py-3 px-4">Reason</th>
                <th className="py-3 px-4">Added by</th>
                <th className="py-3 px-4">Added</th>
                <th className="py-3 px-4">Expires</th>
                <th className="py-3 px-4 w-28" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border font-body-sm text-body-sm">
              {entries.map((e) => (
                <tr key={e.id ?? `builtin-${e.value}`} className="hover:bg-hover">
                  <td className="py-2 px-4 font-data-mono text-data-mono text-text-primary">{e.value}</td>
                  <td className="py-2 px-4 text-on-surface truncate max-w-[260px]" title={e.reason ?? ''}>
                    {e.builtIn ? <span className="text-on-surface-variant">Manager config</span> : e.reason ?? '—'}
                  </td>
                  <td className="py-2 px-4 text-on-surface">{e.createdBy ?? '—'}</td>
                  <td className="py-2 px-4 text-on-surface-variant whitespace-nowrap">
                    {e.createdAt ? new Date(e.createdAt).toLocaleString() : '—'}
                  </td>
                  <td className="py-2 px-4 whitespace-nowrap">
                    <span className={e.builtIn || !e.expiresAt ? 'text-on-surface-variant' : 'text-warning'}>{expiresText(e)}</span>
                  </td>
                  <td className="py-2 px-4 text-right">
                    {!e.builtIn && (
                      <button
                        onClick={() => remove(e)}
                        disabled={busyId === e.id}
                        className="px-3 py-1 border border-border rounded hover:bg-hover hover:border-error text-text-primary disabled:opacity-50"
                      >
                        {busyId === e.id ? '...' : 'Remove'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {dialogOpen && <AllowlistDialog onClose={() => setDialogOpen(false)} onCreated={onCreated} />}
    </div>
  );
}
