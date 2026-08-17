import { NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import {
  LayoutDashboard,
  Users,
  Upload,
  Settings,
  LogOut,
  Brain,
  FileText,
  BookOpen,
  ShieldCheck,
  Zap,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/utils/cn';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, badge: null },
  { to: '/patients', label: 'Patients', icon: Users, badge: null },
  { to: '/upload', label: 'Upload Studies', icon: Upload, badge: null },
  { to: '/analysis', label: 'AI Radiology & PACS', icon: Brain, badge: 'AI Vision' },
  { to: '/blood-reports', label: 'Blood & Lab Tests', icon: FileText, badge: null },
  { to: '/knowledge', label: 'Hospital Protocols', icon: BookOpen, badge: 'RAG AI' },
  { to: '/settings', label: 'Settings', icon: Settings, badge: null },
];

export function Sidebar() {
  const { fullName, role, tenantName, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <aside className="flex h-screen w-64 flex-col border-r border-slate-800 bg-slate-950 text-slate-100 select-none shadow-2xl">
      {/* Brand Header */}
      <div className="flex items-center gap-3 border-b border-slate-800/80 p-4 bg-slate-900/40 backdrop-blur-md">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 text-white shadow-lg shadow-blue-500/20">
          <Brain className="h-5 w-5" />
        </div>
        <div className="overflow-hidden">
          <div className="flex items-center gap-1.5">
            <h1 className="text-base font-bold tracking-tight text-white">Med-AI</h1>
            <span className="rounded-md bg-blue-500/10 px-1.5 py-0.5 text-[10px] font-bold text-blue-400 border border-blue-500/20">
              PACS
            </span>
          </div>
          <p className="truncate text-xs text-slate-400 font-medium">{tenantName || 'Enterprise Hospital'}</p>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 space-y-1.5 p-3 overflow-y-auto">
        <div className="px-3 pb-1 pt-2">
          <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Diagnostic Suite</p>
        </div>

        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              cn(
                'group flex items-center justify-between rounded-lg px-3 py-2.5 text-sm font-medium transition-all',
                isActive
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-100'
              )
            }
          >
            <div className="flex items-center gap-3">
              <item.icon className="h-4 w-4 shrink-0 transition-transform group-hover:scale-110" />
              <span>{item.label}</span>
            </div>
            {item.badge && (
              <span className="rounded bg-blue-500/20 px-1.5 py-0.5 text-[10px] font-bold text-blue-300">
                {item.badge}
              </span>
            )}
          </NavLink>
        ))}
      </nav>

      {/* System Status HUD */}
      <div className="p-3 border-t border-slate-800/80 bg-slate-900/20">
        <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-2.5 space-y-1.5">
          <div className="flex items-center justify-between text-[11px]">
            <span className="flex items-center gap-1.5 text-slate-400">
              <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
              AI Pipeline
            </span>
            <span className="font-mono text-[10px] font-semibold text-emerald-400">ONLINE</span>
          </div>
          <div className="flex items-center justify-between text-[10px] text-slate-500">
            <span>Provider: Groq Llama 3.3</span>
            <span className="flex items-center gap-1 text-slate-400">
              <Zap className="h-3 w-3 text-amber-400" />
              30ms
            </span>
          </div>
        </div>
      </div>

      {/* User Profile & Logout */}
      <div className="border-t border-slate-800 p-3 bg-slate-900/60">
        <div className="flex items-center gap-3 mb-3 px-1">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-800 text-sm font-bold text-blue-400 border border-slate-700">
            {fullName ? fullName.charAt(0).toUpperCase() : 'D'}
          </div>
          <div className="overflow-hidden">
            <p className="truncate text-xs font-semibold text-slate-200">{fullName || 'Clinical Doctor'}</p>
            <div className="flex items-center gap-1 text-[10px] text-slate-400">
              <ShieldCheck className="h-3 w-3 text-blue-400" />
              <span className="capitalize">{role ? role.replace('_', ' ').toLowerCase() : 'Practitioner'}</span>
            </div>
          </div>
        </div>

        <Button
          variant="outline"
          size="sm"
          className="w-full border-slate-800 bg-slate-900 text-slate-300 hover:bg-slate-800 hover:text-white"
          onClick={handleLogout}
        >
          <LogOut className="mr-2 h-3.5 w-3.5" />
          Sign Out
        </Button>
      </div>
    </aside>
  );
}
