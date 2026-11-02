import { useCallback, useEffect, useRef, useState } from 'react';

interface PollingState<T> {
  data: T | null;
  loading: boolean; // yalnız ilk yüklemede true; arka plan yenilemelerinde ekran titremesin
  error: string | null;
  refresh: () => void;
}

/**
 * fetcher'ı hemen ve sonra her intervalMs'de bir çağırır. deps değişince baştan yükler.
 * intervalMs = 0 → yalnız bir kez yükler. Sekme arka plandayken istek atmaz.
 */
export function usePolling<T>(fetcher: () => Promise<T>, deps: unknown[], intervalMs = 10000): PollingState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  const runId = useRef(0);

  const load = useCallback(() => {
    const id = ++runId.current;
    fetcherRef
      .current()
      .then((result) => {
        if (id !== runId.current) return; // daha yeni bir istek başladı, eskisini yok say
        setData(result);
        setError(null);
      })
      .catch(() => {
        if (id === runId.current) setError('Veri yüklenemedi');
      })
      .finally(() => {
        if (id === runId.current) setLoading(false);
      });
  }, []);

  useEffect(() => {
    setLoading(true);
    load();
    if (!intervalMs) return () => { runId.current++; };
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') load();
    }, intervalMs);
    return () => {
      clearInterval(timer);
      runId.current++;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, intervalMs, load]);

  return { data, loading, error, refresh: load };
}
