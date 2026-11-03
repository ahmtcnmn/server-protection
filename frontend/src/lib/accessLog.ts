// Event'in istek bilgisi. Yeni event'lerde alanlar Agent'ın parser'ından gelir (event.fields);
// fields'ı olmayan eski event'ler için ham satır burada "combined" formatına göre ayrıştırılır.
// Tespit desenleri artık burada tutulmuyor: eşleşen kural ve yakalanan metin event'le birlikte gelir.

import type { EventResponse } from '../types';

export interface ParsedRequest {
  method: string;
  target: string; // URL-decode edilmiş istek hedefi (kuralın incelediği metin)
  path: string;
  params: { key: string; value: string }[];
  status: string | null;
  bytes: string | null;
  referer: string | null;
  userAgent: string | null;
}

export interface Match {
  start: number;
  end: number;
  rule: string;
}

const COMBINED = /^(\S+) \S+ \S+ \[([^\]]+)\] "([A-Z]+) ([^"]*?)(?: (HTTP\/[\d.]+))?" (\d{3}) (\S+)(?: "([^"]*)" "([^"]*)")?/;

// Agent'taki Java URLDecoder ile aynı: '+' boşluk olur, %xx çözülür
export function decode(s: string) {
  try {
    return decodeURIComponent(s.replace(/\+/g, ' '));
  } catch {
    return s;
  }
}

function orNull(v: string | undefined) {
  return v && v !== '-' ? v : null;
}

function build(method: string, rawTarget: string, rest: Omit<ParsedRequest, 'method' | 'target' | 'path' | 'params'>): ParsedRequest {
  const q = rawTarget.indexOf('?');
  const rawPath = q >= 0 ? rawTarget.slice(0, q) : rawTarget;
  const rawQuery = q >= 0 ? rawTarget.slice(q + 1) : '';
  const params = rawQuery
    ? rawQuery.split('&').filter(Boolean).map((pair) => {
        const eq = pair.indexOf('=');
        return eq >= 0
          ? { key: decode(pair.slice(0, eq)), value: decode(pair.slice(eq + 1)) }
          : { key: decode(pair), value: '' };
      })
    : [];
  return { method, target: decode(rawTarget), path: decode(rawPath), params, ...rest };
}

/** Event'in HTTP isteği: önce Agent'ın alanları, yoksa (eski event) ham satır. İstek değilse null. */
export function requestOf(event: EventResponse): ParsedRequest | null {
  const f = event.fields;
  if (f && f.method && f.url != null) {
    return build(f.method, f.url, {
      status: f.status ?? null,
      bytes: f.bytes ?? null,
      referer: orNull(f.referer),
      userAgent: orNull(f.userAgent),
    });
  }
  if (event.parser) return null; // ayrıştırılmış ama HTTP isteği değil (ör. raw-line)
  const m = COMBINED.exec(event.rawData);
  if (!m) return null;
  return build(m[3], m[4], { status: m[6], bytes: m[7], referer: orNull(m[8]), userAgent: orNull(m[9]) });
}

/** Kuralın yakaladığı metnin metindeki geçtiği yerler (yakalanan metin dönüşümden sonraki haliyle gelir). */
export function findMatches(text: string, matched: string | null, rule: string | null): Match[] {
  if (!matched) return [];
  const matches: Match[] = [];
  let from = 0;
  let i: number;
  while ((i = text.indexOf(matched, from)) >= 0) {
    matches.push({ start: i, end: i + matched.length, rule: rule ?? '' });
    from = i + Math.max(1, matched.length);
  }
  return matches;
}
