import { NavLink, useNavigate } from 'react-router-dom';
import { clearToken, getUsername } from '../lib/auth';
import { navItems } from '../lib/nav';

export function Topbar() {
  const navigate = useNavigate();
  const username = getUsername();

  const logout = () => {
    clearToken();
    navigate('/login', { replace: true });
  };

  return (
    <header className="fixed top-0 right-0 w-full md:w-[calc(100%-240px)] h-topbar-height bg-background border-b border-border flex items-center justify-between px-container-padding z-30">
      {/* Mobilde sidebar gizli: sayfa geçişleri buradan */}
      <nav className="flex items-center gap-1 md:hidden">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            title={item.label}
            className={({ isActive }) =>
              `p-2 rounded ${isActive ? 'text-primary bg-hover' : 'text-on-surface-variant hover:text-primary'}`
            }
          >
            <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
          </NavLink>
        ))}
      </nav>
      <div className="hidden md:flex items-center gap-2 font-body-sm text-body-sm text-on-surface-variant">
        <span className="w-2 h-2 rounded-full bg-success animate-pulse" />
        Live · refreshes every 10s
      </div>

      <div className="flex items-center gap-3">
        {username && (
          <span className="hidden sm:flex items-center gap-1 font-body-sm text-body-sm text-on-surface-variant">
            <span className="material-symbols-outlined text-[18px]">account_circle</span>
            {username}
          </span>
        )}
        <button
          onClick={logout}
          title="Log out"
          className="flex items-center gap-1 px-2 py-1.5 rounded text-on-surface-variant hover:text-primary hover:bg-hover transition-colors font-body-sm text-body-sm"
        >
          <span className="material-symbols-outlined text-[18px]">logout</span>
          <span className="hidden sm:inline">Log out</span>
        </button>
      </div>
    </header>
  );
}
