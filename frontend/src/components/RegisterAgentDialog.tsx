import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { registerAgent, type RegisterAgentResult } from '../api/agents';
import { errorMessage } from '../api/client';

const HOSTNAME_PATTERN = /^[A-Za-z0-9._-]{1,100}$/; // Manager'daki DtoRegisterRequest ile aynı kural

interface Props {
  onClose: () => void;
  onRegistered: () => void;
}

export function RegisterAgentDialog({ onClose, onRegistered }: Props) {
  const [hostName, setHostName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RegisterAgentResult | null>(null);
  const [copied, setCopied] = useState(false);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const name = hostName.trim();
    if (!HOSTNAME_PATTERN.test(name)) {
      setError('Yalnız harf, rakam, nokta, tire ve alt çizgi (en fazla 100 karakter)');
      return;
    }
    setSubmitting(true);
    setError(null);
    registerAgent(name)
      .then((r) => {
        setResult(r);
        onRegistered();
      })
      .catch((err) => setError(errorMessage(err, 'Agent kaydedilemedi')))
      .finally(() => setSubmitting(false));
  };

  const config = result
    ? `manager.base-url=https://<manager-adresi>:8443\nmanager.api-key=${result.apiKey}`
    : '';

  const copy = () => {
    navigator.clipboard
      ?.writeText(config)
      .then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      })
      .catch(() => {});
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center px-4" onClick={result ? undefined : onClose}>
      <div onClick={(e) => e.stopPropagation()} className="w-full max-w-lg bg-surface border border-border rounded p-6 flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h3 className="font-headline-sm text-headline-sm font-bold text-text-primary">Add agent</h3>
          <button type="button" onClick={onClose} className="text-on-surface-variant hover:text-text-primary">
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        {!result && (
          <form onSubmit={onSubmit} className="flex flex-col gap-4">
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Registers a new agent and generates its API key. Use the server's hostname so it is easy to recognise.
            </p>
            <label className="flex flex-col gap-1">
              <span className="font-label-caps text-label-caps text-on-surface-variant">Hostname</span>
              <input
                value={hostName}
                onChange={(e) => setHostName(e.target.value)}
                placeholder="web-server-01"
                autoFocus
                className="bg-background border border-border rounded px-3 py-2 font-data-mono text-data-mono focus:outline-none focus:border-primary-container"
              />
            </label>
            {error && <p className="font-body-sm text-body-sm text-error">{error}</p>}
            <div className="flex justify-end gap-2">
              <button type="button" onClick={onClose} className="px-4 py-2 border border-border rounded hover:bg-hover font-body-sm text-body-sm">
                Cancel
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="px-4 py-2 bg-primary-container text-on-primary-container rounded font-semibold font-body-sm text-body-sm hover:bg-primary disabled:opacity-60"
              >
                {submitting ? 'Registering...' : 'Register'}
              </button>
            </div>
          </form>
        )}

        {result && (
          <div className="flex flex-col gap-4">
            <div className="flex items-start gap-2 bg-warning/10 border border-warning/30 rounded p-3 font-body-sm text-body-sm text-warning">
              <span className="material-symbols-outlined text-[18px]">warning</span>
              <span>
                The API key is shown <b>only once</b>. Manager stores just its hash, so it cannot be displayed again. Copy it now.
              </span>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-end">
                <span className="font-label-caps text-label-caps text-on-surface-variant">
                  Add to the agent's application.properties
                </span>
                <button onClick={copy} className="text-primary font-body-sm text-[11px] hover:underline flex items-center gap-1">
                  <span className="material-symbols-outlined text-[14px]">{copied ? 'check' : 'content_copy'}</span>
                  {copied ? 'Copied' : 'Copy'}
                </button>
              </div>
              <pre className="bg-background border border-border rounded p-3 font-data-mono text-data-mono text-text-primary whitespace-pre-wrap break-all">
                {config}
              </pre>
            </div>
            <div className="flex justify-end gap-2">
              <Link
                to={`/agents/${result.agentId}`}
                className="px-4 py-2 border border-border rounded hover:bg-hover font-body-sm text-body-sm"
              >
                Open agent
              </Link>
              <button
                onClick={onClose}
                className="px-4 py-2 bg-primary-container text-on-primary-container rounded font-semibold font-body-sm text-body-sm hover:bg-primary"
              >
                Done
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
