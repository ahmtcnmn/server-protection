import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { login } from '../api/auth';
import { errorMessage } from '../api/client';
import { getToken, setToken } from '../lib/auth';

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Yalnız uygulama içi yollara dön (açık yönlendirmeye karşı)
  const nextParam = params.get('next');
  const next = nextParam && nextParam.startsWith('/') && !nextParam.startsWith('//') ? nextParam : '/';

  if (getToken()) {
    return <Navigate to={next} replace />;
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    login(username.trim(), password)
      .then((token) => {
        setToken(token);
        navigate(next, { replace: true });
      })
      .catch((err) => setError(errorMessage(err, 'Giriş başarısız')))
      .finally(() => setSubmitting(false));
  };

  return (
    <div className="min-h-screen bg-background text-text-primary flex items-center justify-center px-4">
      <form onSubmit={onSubmit} className="w-full max-w-sm bg-surface border border-border rounded p-8 flex flex-col gap-5">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-10 h-10 rounded bg-primary/10 flex items-center justify-center border border-primary/20">
            <span className="material-symbols-outlined text-primary text-xl">security</span>
          </div>
          <div>
            <h1 className="font-headline-sm text-headline-sm font-bold text-primary leading-tight">Obsidian Sentinel</h1>
            <p className="font-body-sm text-body-sm text-on-surface-variant">Manager Panel</p>
          </div>
        </div>

        <label className="flex flex-col gap-1">
          <span className="font-label-caps text-label-caps text-on-surface-variant">Username</span>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
            className="bg-background border border-border rounded px-3 py-2 font-body-md text-body-md focus:outline-none focus:border-primary-container"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="font-label-caps text-label-caps text-on-surface-variant">Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
            className="bg-background border border-border rounded px-3 py-2 font-body-md text-body-md focus:outline-none focus:border-primary-container"
          />
        </label>

        {error && (
          <p className="font-body-sm text-body-sm text-error flex items-center gap-2">
            <span className="material-symbols-outlined text-[16px]">error</span>
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="bg-primary-container text-on-primary-container font-headline-sm text-headline-sm font-semibold py-2 rounded hover:bg-primary transition-colors disabled:opacity-60"
        >
          {submitting ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
