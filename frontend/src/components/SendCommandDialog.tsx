import { useState, type FormEvent } from 'react';
import { errorMessage } from '../api/client';
import { sendCommand } from '../api/commands';
import { COMMAND_TYPES } from '../types';

interface Props {
  agentId: number;
  hostname: string;
  onClose: () => void;
  onSent: () => void;
}

export function SendCommandDialog({ agentId, hostname, onClose, onSent }: Props) {
  const [commandType, setCommandType] = useState<string>('PING');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    sendCommand(agentId, commandType)
      .then(() => {
        onSent();
        onClose();
      })
      .catch((err) => setError(errorMessage(err, 'Komut gönderilemedi')))
      .finally(() => setSubmitting(false));
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center px-4" onClick={onClose}>
      <form
        onSubmit={onSubmit}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md bg-surface border border-border rounded p-6 flex flex-col gap-4"
      >
        <div className="flex items-center justify-between">
          <h3 className="font-headline-sm text-headline-sm font-bold text-text-primary">Send command</h3>
          <button type="button" onClick={onClose} className="text-on-surface-variant hover:text-text-primary">
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Target: <span className="font-data-mono text-primary">{hostname}</span>. The agent picks up commands every 10 seconds.
          To block an IP, use the <b>Blocks</b> page.
        </p>

        <label className="flex flex-col gap-1">
          <span className="font-label-caps text-label-caps text-on-surface-variant">Command</span>
          <select
            value={commandType}
            onChange={(e) => setCommandType(e.target.value)}
            className="bg-background border border-border rounded px-3 py-2 font-body-md text-body-md focus:outline-none focus:border-primary-container"
          >
            {COMMAND_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>


        {error && <p className="font-body-sm text-body-sm text-error">{error}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-4 py-2 border border-border rounded hover:bg-hover font-body-sm text-body-sm">
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-container text-on-primary-container rounded font-semibold font-body-sm text-body-sm hover:bg-primary disabled:opacity-60"
          >
            {submitting ? 'Sending...' : 'Send'}
          </button>
        </div>
      </form>
    </div>
  );
}
