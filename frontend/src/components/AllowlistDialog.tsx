import { useState, type FormEvent } from 'react';
import { createAllowlistEntry } from '../api/allowlist';
import { errorMessage } from '../api/client';
import type { AllowlistCreated } from '../types';

// Manager'daki IpValues ile aynı kaba kontrol; asıl doğrulama Manager'da
const VALUE_PATTERN = /^[0-9a-fA-F:.]{2,45}(\/\d{1,3})?$/;

const durations = [
  { label: 'Permanent', minutes: 0 },
  { label: '1 hour', minutes: 60 },
  { label: '24 hours', minutes: 1440 },
  { label: '7 days', minutes: 10080 },
  { label: '30 days', minutes: 43200 },
];

const fieldClass =
  'bg-background border border-border rounded px-3 py-2 font-body-md text-body-md focus:outline-none focus:border-primary-container';

interface Props {
  onClose: () => void;
  onCreated: (result: AllowlistCreated) => void;
}

export function AllowlistDialog({ onClose, onCreated }: Props) {
  const [value, setValue] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(0);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const v = value.trim();
    if (!VALUE_PATTERN.test(v)) {
      setError('Geçerli bir IP ya da CIDR girin (ör. 203.0.113.7 veya 203.0.113.0/24)');
      return;
    }
    setSubmitting(true);
    setError(null);
    createAllowlistEntry({
      value: v,
      durationMinutes: durationMinutes > 0 ? durationMinutes : undefined,
      reason: reason.trim() || undefined,
    })
      .then((result) => {
        onCreated(result);
        onClose();
      })
      .catch((err) => setError(errorMessage(err, 'Allowlist girişi eklenemedi')))
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
          <h3 className="font-headline-sm text-headline-sm font-bold text-text-primary">Add to allowlist</h3>
          <button type="button" onClick={onClose} className="text-on-surface-variant hover:text-text-primary">
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        <label className="flex flex-col gap-1">
          <span className="font-label-caps text-label-caps text-on-surface-variant">IP or CIDR</span>
          <input
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="203.0.113.7"
            autoFocus
            className={`${fieldClass} font-data-mono`}
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="font-label-caps text-label-caps text-on-surface-variant">Duration</span>
          <select value={durationMinutes} onChange={(e) => setDurationMinutes(Number(e.target.value))} className={fieldClass}>
            {durations.map((d) => (
              <option key={d.minutes} value={d.minutes}>
                {d.label}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1">
          <span className="font-label-caps text-label-caps text-on-surface-variant">Reason (optional)</span>
          <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} className={fieldClass} />
        </label>

        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Allowlisted addresses are never banned. Active bans that overlap this address are removed immediately.
        </p>
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
            {submitting ? 'Adding...' : 'Add'}
          </button>
        </div>
      </form>
    </div>
  );
}
