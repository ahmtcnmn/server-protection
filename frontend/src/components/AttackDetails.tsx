import { findMatches, requestOf } from '../lib/accessLog';
import type { EventResponse } from '../types';
import { HighlightedText } from './HighlightedText';

const label = 'font-label-caps text-label-caps text-on-surface-variant mb-1';
const mono = 'font-data-mono text-data-mono text-on-surface break-all';

/** Event'i üreten kural ve (access log ise) isteğin ayrıştırılmış hali; yakalanan kısım işaretlenir. */
export function AttackDetails({ event }: { event: EventResponse }) {
  const req = requestOf(event);
  const hasRule = event.rule != null;
  if (!req && !hasRule) return null;

  const highlight = (text: string) => findMatches(text, event.matched, event.rule);

  return (
    <div className="flex flex-col gap-4">
      {hasRule ? (
        <div className="flex flex-col gap-2">
          <p className={label}>Detection</p>
          <div className="flex flex-wrap items-center gap-2 font-body-sm text-body-sm">
            <span className="px-2 py-0.5 rounded-sm border border-error/30 bg-error/15 text-error font-data-mono text-[12px]">
              {event.rule}
            </span>
            <span className="text-on-surface-variant">matched</span>
            <code className="px-1.5 py-0.5 rounded-sm bg-background border border-border font-data-mono text-[12px] text-error break-all">
              {event.matched}
            </code>
            <span className="text-on-surface-variant">in</span>
            <span className="font-data-mono text-[12px] text-on-surface">{event.matchField ?? 'log line'}</span>
          </div>
          {event.queued && (
            <p className="font-body-sm text-body-sm text-warning">
              Delivered late from the agent's offline queue; not counted toward the ban threshold.
            </p>
          )}
        </div>
      ) : (
        event.eventType === 'SQL_INJECTION' && (
          <p className="font-body-sm text-body-sm text-on-surface-variant">
            Older event: recorded before YAML rules, so the matching rule is unknown.
          </p>
        )
      )}

      {req ? (
        <>
          <div className="flex flex-col gap-2">
            <p className={label}>Request (URL-decoded)</p>
            <div className="bg-background border border-border rounded p-3">
              <p className="font-data-mono text-data-mono text-text-primary break-all whitespace-pre-wrap">
                <span className="text-primary font-semibold">{req.method}</span>{' '}
                <HighlightedText text={req.target} matches={highlight(req.target)} />
              </p>
            </div>
          </div>

          {req.params.length > 0 && (
            <div className="flex flex-col gap-2">
              <p className={label}>Query parameters</p>
              <table className="w-full text-left border border-border rounded font-body-sm text-body-sm">
                <tbody className="divide-y divide-border">
                  {req.params.map((p, i) => {
                    const pm = highlight(p.value);
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
                {req.status ?? '—'}
                {req.bytes != null ? ` · ${req.bytes} bytes` : ''}
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
        </>
      ) : (
        event.matchField == null && (
          <div className="flex flex-col gap-2">
            <p className={label}>Matched in log line</p>
            <div className="bg-background border border-border rounded p-3">
              <p className="font-data-mono text-data-mono text-text-primary break-all whitespace-pre-wrap">
                <HighlightedText text={event.rawData} matches={highlight(event.rawData)} />
              </p>
            </div>
          </div>
        )
      )}
    </div>
  );
}
