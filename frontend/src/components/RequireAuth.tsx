import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { refreshAccessToken } from '../api/client';
import { getToken } from '../lib/auth';

/**
 * Geçerli access token yoksa önce cookie'deki refresh token ile yenilemeyi dener
 * (ör. sayfa ertesi gün açıldığında). O da olmazsa login'e yönlendirir.
 */
export function RequireAuth() {
  const location = useLocation();
  const [status, setStatus] = useState<'ok' | 'checking' | 'denied'>(() => (getToken() ? 'ok' : 'checking'));

  useEffect(() => {
    if (status !== 'checking') return;
    let cancelled = false;
    refreshAccessToken().then((token) => {
      if (!cancelled) setStatus(token ? 'ok' : 'denied');
    });
    return () => {
      cancelled = true;
    };
  }, [status]);

  if (status === 'checking') {
    return <div className="min-h-screen bg-background" />;
  }
  if (status === 'denied') {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <Outlet />;
}
