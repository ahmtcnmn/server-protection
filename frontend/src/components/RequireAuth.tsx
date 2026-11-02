import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { getToken } from '../lib/auth';

/** Token yoksa login'e yönlendirir; giriş sonrası kaldığı sayfaya döner. */
export function RequireAuth() {
  const location = useLocation();
  if (!getToken()) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <Outlet />;
}
