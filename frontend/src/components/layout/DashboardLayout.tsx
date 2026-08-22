import { Outlet, Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Sidebar } from './Sidebar';
import { Shield, Clock, Search, Bell } from 'lucide-react';
import { useState, useEffect } from 'react';

const pageMeta: Record<string, { title: string; sub: string }> = {
  dashboard:    { title: 'Dashboard',                  sub: 'Clinical overview & quick actions' },
  patients:     { title: 'Patient Registry',           sub: 'Medical records management' },
  upload:       { title: 'Upload Studies',             sub: 'Diagnostic file ingestion' },
  workflows:    { title: 'Clinical Agent & LangGraph4j Workflows', sub: 'Autonomous multi-step actions & HITL approval' },
  analysis:     { title: 'AI Radiology & PACS',        sub: 'Multimodal image analysis' },
  'blood-reports': { title: 'Blood & Lab Reports',     sub: 'Laboratory analytics' },
  knowledge:    { title: 'Hospital Protocols',         sub: 'RAG knowledge base' },
  chat:         { title: 'Clinical AI Chat',           sub: 'AI-assisted decision support' },
  settings:     { title: 'Settings',                   sub: 'Security & system configuration' },
};

export function DashboardLayout() {
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();
  const [time, setTime] = useState(
    new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  );

  useEffect(() => {
    const t = setInterval(() => {
      setTime(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
    }, 30_000);
    return () => clearInterval(t);
  }, []);

  if (!isAuthenticated) return <Navigate to="/login" replace />;

  const segment = location.pathname.split('/').filter(Boolean)[0] || 'dashboard';
  const meta = pageMeta[segment] || { title: 'Med-AI', sub: 'Clinical Intelligence Platform' };

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: 'var(--bg, #0a0f1e)' }}>
      <Sidebar />

      <div className="flex flex-1 flex-col overflow-hidden">
        {/* ── Top Header ── */}
        <header
          className="flex h-14 shrink-0 items-center justify-between px-6 gap-4"
          style={{
            background: 'rgba(17,24,39,0.9)',
            borderBottom: '1px solid var(--clr-border, #1e2d45)',
            backdropFilter: 'blur(12px)',
          }}
        >
          {/* Left: Page title */}
          <div className="flex items-center gap-3 min-w-0">
            <div className="hidden sm:block">
              <h2 className="text-sm font-bold text-white leading-none">{meta.title}</h2>
              <p className="text-[11px] mt-0.5" style={{ color: 'var(--clr-text-3, #64748b)' }}>{meta.sub}</p>
            </div>
          </div>

          {/* Right: Search + Clock + Warning + Bell */}
          <div className="flex items-center gap-2 ml-auto">
            {/* Search */}
            <div
              className="hidden md:flex items-center gap-2 h-8 px-3 rounded-lg text-xs"
              style={{
                background: 'var(--surface-2, #1a2235)',
                border: '1px solid var(--clr-border, #1e2d45)',
                color: 'var(--clr-text-3, #64748b)',
              }}
            >
              <Search className="h-3.5 w-3.5" />
              <span>Search…</span>
              <kbd
                className="ml-2 hidden lg:inline text-[10px] px-1.5 py-0.5 rounded"
                style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--clr-text-3)' }}
              >
                ⌘K
              </kbd>
            </div>

            {/* Clock */}
            <div
              className="hidden sm:flex items-center gap-1.5 h-8 px-3 rounded-lg text-xs font-mono"
              style={{
                background: 'var(--surface-2, #1a2235)',
                border: '1px solid var(--clr-border, #1e2d45)',
                color: 'var(--clr-text-2, #94a3b8)',
              }}
            >
              <Clock className="h-3.5 w-3.5" style={{ color: '#3b82f6' }} />
              <span>{time}</span>
            </div>

            {/* Clinician disclaimer */}
            <div
              className="hidden sm:flex items-center gap-1.5 h-8 px-3 rounded-lg text-xs font-semibold"
              style={{
                background: 'rgba(245,158,11,0.08)',
                border: '1px solid rgba(245,158,11,0.2)',
                color: '#fbbf24',
              }}
            >
              <Shield className="h-3.5 w-3.5" />
              <span className="hidden lg:inline">Clinician review required</span>
            </div>

            {/* Bell */}
            <button
              className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors"
              style={{
                background: 'var(--surface-2, #1a2235)',
                border: '1px solid var(--clr-border, #1e2d45)',
                color: 'var(--clr-text-2, #94a3b8)',
              }}
              onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.color = 'white'; }}
              onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.color = 'var(--clr-text-2, #94a3b8)'; }}
            >
              <Bell className="h-3.5 w-3.5" />
            </button>
          </div>
        </header>

        {/* ── Main Content ── */}
        <main
          className="flex-1 overflow-y-auto p-6"
          style={{ background: 'var(--bg, #0a0f1e)' }}
        >
          <div className="animate-in">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
