import { findSqliMatches, parseAccessLog } from '../lib/accessLog';
import type { EventResponse } from '../types';
import { HighlightedText } from './HighlightedText';

const label = 'font-label-caps text-label-caps text-on-surface-variant mb-1';
const mono = 'font-data-mono text-data-mono text-on-surface break-all';

/** Access log satırından üretilen event'te isteğin ayrıştırılmış hali ve yakalanan SQLi desenleri. */
export function AttackDetails({ event }: { event: EventResponse }) {
  const req = parseAccessLog(event.rawData);
  if (!req) return null;

  const isSqli = event.eventType === 'SQL_INJECTION';
  const matches = isSqli ? findSqliMatches(req.target) : [];
  const rules = [...new Set(matches.map((m) => m.rule))];

  return (
    <div className="flex flex-col gap-4">
      {isSqli && (
        <div className="flex flex-col gap-2">
          <p className={label}>Detected patterns</p>
          {rules.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {rules.map((r) => (
                <span key={r} className="px-2 py-0.5 rounded-sm border border-error/30 bg-error/15 text-error font-body-sm text-[12px]">
                  {r}
                </span>
              ))}
            </div>
          ) : (
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              No pattern matched in the panel. The Agent may be using a newer rule.
            </p>
          )}
        </div>
      )}

      <div className="flex flex-col gap-2">
        <p className={label}>Request (URL-decoded)</p>
        <div className="bg-background border border-border rounded p-3">
          <p className="font-data-mono text-data-mono text-text-primary break-all whitespace-pre-wrap">
            <span className="text-primary font-semibold">{req.method}</span>{' '}
            <HighlightedText text={req.target} matches={matches} />
          </p>
        </div>
      </div>

      {req.params.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className={label}>Query parameters</p>
          <table className="w-full text-left border border-border rounded font-body-sm text-body-sm">
            <tbody className="divide-y divide-border">
              {req.params.map((p, i) => {
                const pm = isSqli ? findSqliMatches(p.value) : [];
                return (
                  <tr key={i} className={pm.length > 0 ? 'bg-error/5' : ''}>
                    <td className="py-1.5 px-3 font-data-mono text-data-mono text-on-surface-variant align-top w-1/3 break-all">{p.key}</td>
                    <td className="py-1.5 px-3 font-data-mono text-data-mono text-text-primary break-all whitespace-pre-wrap">
                      <HighlightedText text={p.value} matches={pm} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4">
        <div>
          <p className={label}>Response</p>
          <p className={mono}>
            {req.status} · {req.bytes} bytes
          </p>
        </div>
        <div>
          <p className={label}>Path</p>
          <p className={mono}>{req.path}</p>
        </div>
        <div className="col-span-2">
          <p className={label}>User-Agent</p>
          <p className={mono}>{req.userAgent ?? '—'}</p>
        </div>
        {req.referer && (
          <div className="col-span-2">
            <p className={label}>Referer</p>
            <p className={mono}>{req.referer}</p>
          </div>
        )}
      </div>
    </div>
  );
}
