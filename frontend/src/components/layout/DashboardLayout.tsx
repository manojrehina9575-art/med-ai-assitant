import { Outlet, Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Sidebar } from './Sidebar';
import { Shield, Clock, Sparkles } from 'lucide-react';
import { useState, useEffect } from 'react';

export function DashboardLayout() {
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();
  const [time, setTime] = useState(
    new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  );

  useEffect(() => {
    const timer = setInterval(() => {
      setTime(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const getPageTitle = () => {
    const path = location.pathname;
    if (path.includes('analysis')) return 'AI Radiology & Multimodal PACS';
    if (path.includes('patients')) return 'Patient Registry & Medical Records';
    if (path.includes('upload')) return 'Diagnostic Study Ingestion';
    if (path.includes('blood-reports')) return 'Laboratory & Blood Report Analytics';
    if (path.includes('settings')) return 'Enterprise Security & System Settings';
    return 'Clinical Intelligence Command Center';
  };

  return (
    <div className="flex h-screen overflow-hidden bg-slate-950 text-slate-100 font-sans antialiased">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top Clinical Header Bar */}
        <header className="flex h-14 items-center justify-between border-b border-slate-800/80 bg-slate-950/90 px-6 backdrop-blur-md z-10">
          <div className="flex items-center gap-3">
            <h2 className="text-sm font-bold tracking-tight text-slate-200">{getPageTitle()}</h2>
            <span className="hidden items-center gap-1 rounded-full bg-blue-500/10 px-2.5 py-0.5 text-[11px] font-semibold text-blue-400 border border-blue-500/20 md:inline-flex">
              <Sparkles className="h-3 w-3" />
              AI Assisted
            </span>
          </div>

          <div className="flex items-center gap-4 text-xs text-slate-400">
            <div className="hidden items-center gap-1.5 rounded-lg bg-slate-900 border border-slate-800 px-3 py-1.5 font-mono text-[11px] text-slate-300 sm:flex">
              <Clock className="h-3.5 w-3.5 text-blue-400" />
              <span>{time}</span>
            </div>

            <div className="flex items-center gap-1.5 text-emerald-400 font-medium bg-emerald-950/40 border border-emerald-900/60 rounded-lg px-2.5 py-1 text-[11px]">
              <Shield className="h-3.5 w-3.5" />
              <span>HIPAA Compliant RLS</span>
            </div>
          </div>
        </header>

        {/* Main Content Area */}
        <main className="flex-1 overflow-y-auto bg-slate-900/40 p-6 scrollbar-thin scrollbar-thumb-slate-800">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
