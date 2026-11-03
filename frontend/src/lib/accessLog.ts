// nginx/apache "combined" log satırını ayrıştırır ve SQLi desenlerinin satırın neresinde eşleştiğini bulur.
// Desenler Agent'taki SqliDetector ile aynı tutulmalı (Madde 4'te kurallar YAML'a taşınınca eşleşen kural Agent'tan gelecek).

export interface ParsedRequest {
  clientIp: string;
  time: string;
  method: string;
  target: string; // URL-decode edilmiş istek hedefi (Agent'ın incelediği metin)
  path: string;
  params: { key: string; value: string }[];
  protocol: string;
  status: number;
  bytes: string;
  referer: string | null;
  userAgent: string | null;
}

export interface SqliRule {
  label: string;
  pattern: RegExp;
}

export interface Match {
  start: number;
  end: number;
  rule: string;
}

const COMBINED = /^(\S+) \S+ \S+ \[([^\]]+)\] "([A-Z]+) (\S+) (HTTP\/[\d.]+)" (\d{3}) (\S+)(?: "([^"]*)" "([^"]*)")?/;

export const SQLI_RULES: SqliRule[] = [
  { label: "Quote + OR/AND comparison (' or 1=1)", pattern: /('|")\s*(or|and)\s*('|")?\d+('|")?\s*=/gi },
  { label: 'UNION SELECT', pattern: /union\s+(all\s+)?select/gi },
  { label: 'SQL comment (--, #, /*)', pattern: /(--|#|\/\*)/g },
  { label: 'Dangerous keyword (DROP TABLE, xp_cmdshell, sleep())', pattern: /\b(drop\s+table|xp_cmdshell|sleep\s*\()/gi },
];

// Agent'taki Java URLDecoder ile aynı: '+' boşluk olur, %xx çözülür
function decode(s: string) {
  try {
    return decodeURIComponent(s.replace(/\+/g, ' '));
  } catch {
    return s;
  }
}

export function parseAccessLog(line: string): ParsedRequest | null {
  const m = COMBINED.exec(line);
  if (!m) return null;
  const rawTarget = m[4];
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
  return {
    clientIp: m[1],
    time: m[2],
    method: m[3],
    target: decode(rawTarget),
    path: decode(rawPath),
    params,
    protocol: m[5],
    status: Number(m[6]),
    bytes: m[7],
    referer: m[8] && m[8] !== '-' ? m[8] : null,
    userAgent: m[9] && m[9] !== '-' ? m[9] : null,
  };
}

/** Metindeki tüm SQLi eşleşmeleri (başlangıca göre sıralı, çakışanlar birleştirilmez). */
export function findSqliMatches(text: string): Match[] {
  const matches: Match[] = [];
  for (const rule of SQLI_RULES) {
    rule.pattern.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = rule.pattern.exec(text)) !== null) {
      matches.push({ start: m.index, end: m.index + m[0].length, rule: rule.label });
      if (m[0].length === 0) rule.pattern.lastIndex++;
    }
  }
  return matches.sort((a, b) => a.start - b.start);
}
