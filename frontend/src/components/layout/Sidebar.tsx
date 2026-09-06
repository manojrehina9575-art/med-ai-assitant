import { NavLink } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { logout } from '@/services/api';
import {
  LayoutDashboard, Users, Settings, LogOut,
  Brain, FileText, Zap, Upload,
  Shield, Cpu, ClipboardCheck, Scan, BarChart2
} from 'lucide-react';
import { cn } from '@/utils/cn';

interface NavItem {
  to: string;
  label: string;
  icon: React.ElementType;
  badge?: string | null;
}

interface NavGroup {
  label?: string;
  items: NavItem[];
}

const navGroups: NavGroup[] = [
  {
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    ],
  },
  {
    label: 'Work',
    items: [
      { to: '/worklist', label: 'Worklist', icon: ClipboardCheck },
      { to: '/upload', label: 'Upload Studies', icon: Upload },
      { to: '/clinical-workspace', label: 'Clinical Workspace', icon: FileText },
      { to: '/patients', label: 'Patients', icon: Users },
    ],
  },
  {
    label: 'Intelligence',
    items: [
      { to: '/qa-analytics', label: 'QA Analytics', icon: BarChart2 },
      { to: '/anatomy', label: 'Anatomy', icon: Scan },
    ],
  },
  {
    label: 'Admin',
    items: [
      { to: '/integrations', label: 'Integrations', icon: Cpu },
      { to: '/compliance', label: 'Compliance', icon: Shield },
      { to: '/settings', label: 'Settings', icon: Settings },
    ],
  },
];

export function Sidebar() {
  const { fullName, role, tenantName } = useAuthStore();

  // Revokes the refresh token server-side and expires the cookie, then redirects. Clearing local
  // state alone used to leave the token valid for the rest of its seven days.
  const handleLogout = () => {
    void logout();
  };

  const initials = fullName
    ? fullName.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)
    : 'DR';

  return (
    <aside
      className="flex h-screen w-60 shrink-0 flex-col select-none"
      style={{
        background: 'var(--surface, #111827)',
        borderRight: '1px solid var(--clr-border, #1e2d45)',
      }}
    >
      {/* ── Brand ── */}
      <div
        className="flex items-center gap-3 px-4 py-5"
        style={{ borderBottom: '1px solid var(--clr-border, #1e2d45)' }}
      >
        <div
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl"
          style={{
            background: 'linear-gradient(135deg, #3b82f6, #06b6d4)',
            boxShadow: '0 0 20px rgba(59,130,246,0.35)',
          }}
        >
          <Brain className="h-5 w-5 text-white" />
        </div>
        <div className="overflow-hidden min-w-0">
          <p className="text-sm font-bold text-white truncate" style={{ fontFamily: 'Plus Jakarta Sans' }}>
            Med-AI
          </p>
          <p className="text-xs truncate" style={{ color: 'var(--clr-text-3, #64748b)' }}>
            Clinical Intelligence
          </p>
        </div>
      </div>

      {/* ── Navigation ── */}
      <nav className="flex-1 overflow-y-auto px-2 py-4 space-y-5">
        {navGroups.map((group) => (
          <div key={group.label ?? 'primary'}>
            {group.label && <p className="section-label mb-2">{group.label}</p>}
            <div className="space-y-0.5">
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/dashboard'}
                  className={({ isActive }) =>
                    cn('nav-item group', isActive && 'active')
                  }
                >
                  <item.icon className="h-4 w-4 shrink-0" />
                  <span className="flex-1 truncate">{item.label}</span>
                  {item.badge && (
                    <span className="badge badge-blue text-[10px] px-1.5 py-0">{item.badge}</span>
                  )}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>

      {/* ── AI Status ── */}
      <div className="px-3 pb-2">
        <div
          className="rounded-xl px-3 py-2.5 flex items-center justify-between"
          style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid var(--clr-border, #1e2d45)' }}
        >
          <div className="flex items-center gap-2">
            <span className="dot-online" />
            <span className="text-xs font-medium" style={{ color: 'var(--clr-text-2, #94a3b8)' }}>AI Pipeline</span>
          </div>
          <div className="flex items-center gap-1 text-amber-400">
            <Zap className="h-3 w-3" />
            <span className="text-[10px] font-mono font-semibold">Online</span>
          </div>
        </div>
      </div>

      {/* ── User Card ── */}
      <div
        className="p-3"
        style={{ borderTop: '1px solid var(--clr-border, #1e2d45)' }}
      >
        <div
          className="flex items-center gap-3 rounded-xl px-3 py-2.5 cursor-pointer group transition-colors"
          style={{ transition: 'background 150ms ease' }}
          onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.04)')}
          onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
        >
          {/* Avatar */}
          <div
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-bold text-blue-300"
            style={{
              background: 'rgba(59,130,246,0.18)',
              border: '1.5px solid rgba(59,130,246,0.35)',
            }}
          >
            {initials}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-semibold text-white truncate">{fullName || 'Clinical User'}</p>
            <p className="text-[10px] capitalize truncate" style={{ color: 'var(--clr-text-3, #64748b)' }}>
              {tenantName ? `${tenantName} · ` : ''}{role ? role.replace(/_/g, ' ').toLowerCase() : 'Practitioner'}
            </p>
          </div>
          <button
            onClick={handleLogout}
            className="ml-auto p-1.5 rounded-lg opacity-0 group-hover:opacity-100 transition-all hover:text-red-400"
            style={{ color: 'var(--clr-text-3, #64748b)', transition: 'all 150ms ease' }}
            title="Sign out"
          >
            <LogOut className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </aside>
  );
}
