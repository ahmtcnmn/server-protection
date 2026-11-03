import type { ReactNode } from 'react';
import type { Match } from '../lib/accessLog';

// Eşleşen bölümleri kırmızı işaretler; çakışan eşleşmeler tek parça olarak birleştirilir
export function HighlightedText({ text, matches }: { text: string; matches: Match[] }) {
  const ranges: { start: number; end: number; rules: string[] }[] = [];
  for (const m of matches) {
    const last = ranges[ranges.length - 1];
    if (last && m.start <= last.end) {
      last.end = Math.max(last.end, m.end);
      if (!last.rules.includes(m.rule)) last.rules.push(m.rule);
    } else {
      ranges.push({ start: m.start, end: m.end, rules: [m.rule] });
    }
  }
  const parts: ReactNode[] = [];
  let pos = 0;
  ranges.forEach((r, i) => {
    if (r.start > pos) parts.push(text.slice(pos, r.start));
    parts.push(
      <mark key={i} title={r.rules.join(', ')} className="bg-error/25 text-error rounded-sm px-0.5">
        {text.slice(r.start, r.end)}
      </mark>,
    );
    pos = r.end;
  });
  if (pos < text.length) parts.push(text.slice(pos));
  return <>{parts}</>;
}
