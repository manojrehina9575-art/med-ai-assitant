import { Outlet, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Sidebar } from './Sidebar';
import { Shield, Clock, Search, X } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { NotificationCenter } from '@/components/notification/NotificationCenter';
import { patientService } from '@/services/patientService';
import type { Patient } from '@/types';

const pageMeta: Record<string, { title: string; sub: string }> = {
  dashboard:    { title: 'Dashboard',                  sub: 'Clinical overview & quick actions' },
  patients:     { title: 'Patient Registry',           sub: 'Medical records management' },
  upload:       { title: 'Upload Studies',             sub: 'Diagnostic file ingestion' },
  workflows:    { title: 'Clinical Agent & LangGraph4j Workflows', sub: 'Autonomous multi-step actions & HITL approval' },
  analysis:     { title: 'AI Radiology & PACS',        sub: 'Multimodal image analysis' },
  'blood-reports': { title: 'Blood & Lab Reports',     sub: 'Laboratory analytics' },
  knowledge:    { title: 'Hospital Protocols',         sub: 'RAG knowledge base' },
  chat:         { title: 'Clinical AI Chat',           sub: 'AI-assisted decision support' },
  compliance:   { title: 'Compliance & Consent',       sub: 'HIPAA Safe Harbor, GDPR consent & data retention' },
  finetuning:   { title: 'Fine-Tuning & Model Registry', sub: 'LoRA adapters, training pipelines & A/B testing' },
  observability: { title: 'System Observability',       sub: 'Prometheus metrics, latency percentiles & JVM telemetry' },
  settings:     { title: 'Settings',                   sub: 'Security & system configuration' },
};

/** Debounce hook */
function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

export function DashboardLayout() {
  const { isAuthenticated, isBootstrapped } = useAuthStore();
  const location   = useLocation();
  const navigate   = useNavigate();
  const searchRef  = useRef<HTMLDivElement>(null);

  const [time, setTime] = useState(
    new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  );

  // ── Global search state ────────────────────────────────
  const [query, setQuery]           = useState('');
  const [searchResults, setResults] = useState<Patient[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searching, setSearching]   = useState(false);
  const debouncedQuery = useDebounce(query, 300);

  useEffect(() => {
    const t = setInterval(() => {
      setTime(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
    }, 30_000);
    return () => clearInterval(t);
  }, []);

  // ── Search logic ────────────────────────────────────────
  useEffect(() => {
    if (!debouncedQuery || debouncedQuery.length < 2) {
      setResults([]);
      setSearchOpen(false);
      return;
    }
    setSearching(true);
    patientService.list(0, 8, debouncedQuery)
      .then((r) => { setResults(r.content); setSearchOpen(true); })
      .catch(() => setResults([]))
      .finally(() => setSearching(false));
  }, [debouncedQuery]);

  // ── Close search dropdown on outside click ──────────────
  useEffect(() => {
    if (!searchOpen) return;
    const handler = (e: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setSearchOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [searchOpen]);

  // ── ⌘K shortcut ────────────────────────────────────────
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        document.getElementById('global-search-input')?.focus();
      }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  // Wait for the refresh-cookie exchange before deciding. Without this a page reload bounces
  // a signed-in user to /login for the duration of the round trip.
  if (!isBootstrapped) return null;
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
            {/* Global Search */}
            <div ref={searchRef} style={{ position: 'relative' }} className="hidden md:block">
              <div
                className="flex items-center gap-2 h-8 px-3 rounded-lg text-xs"
                style={{
                  background: searchOpen ? 'rgba(59,130,246,0.08)' : 'var(--surface-2, #1a2235)',
                  border: `1px solid ${searchOpen ? 'rgba(59,130,246,0.4)' : 'var(--clr-border, #1e2d45)'}`,
                  color: 'var(--clr-text-3, #64748b)',
                  transition: 'all 0.15s',
                  minWidth: 200,
                }}
              >
                <Search className="h-3.5 w-3.5 shrink-0" />
                <input
                  id="global-search-input"
                  type="text"
                  placeholder="Search patients…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  style={{
                    background: 'none', border: 'none', outline: 'none',
                    color: 'var(--clr-text, #f1f5f9)', fontSize: 12,
                    width: '100%', minWidth: 120,
                  }}
                />
                {query ? (
                  <button
                    onClick={() => { setQuery(''); setResults([]); setSearchOpen(false); }}
                    style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', lineHeight: 0 }}
                  >
                    <X size={11} />
                  </button>
                ) : (
                  <kbd
                    className="hidden lg:inline text-[10px] px-1.5 py-0.5 rounded"
                    style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--clr-text-3)' }}
                  >⌘K</kbd>
                )}
              </div>

              {/* Search results dropdown */}
              {searchOpen && (
                <div style={{
                  position: 'absolute', top: 'calc(100% + 6px)', left: 0, right: 0,
                  background: 'var(--surface-1, #111827)',
                  border: '1px solid var(--clr-border, #1e2d45)',
                  borderRadius: 10, overflow: 'hidden',
                  boxShadow: '0 16px 48px rgba(0,0,0,0.5)', zIndex: 999,
                  minWidth: 280,
                }}>
                  {searching && (
                    <div style={{ padding: '10px 14px', fontSize: 12, color: '#64748b' }}>Searching…</div>
                  )}
                  {!searching && searchResults.length === 0 && (
                    <div style={{ padding: '10px 14px', fontSize: 12, color: '#64748b' }}>No patients found</div>
                  )}
                  {searchResults.map((p) => (
                    <button
                      key={p.id}
                      onClick={() => {
                        navigate(`/patients`);
                        setQuery('');
                        setSearchOpen(false);
                      }}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 10, width: '100%',
                        padding: '9px 14px', background: 'none', border: 'none',
                        borderBottom: '1px solid var(--clr-border, #1e2d45)',
                        cursor: 'pointer', textAlign: 'left',
                        transition: 'background 0.1s',
                      }}
                      onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(59,130,246,0.08)'; }}
                      onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.background = 'none'; }}
                    >
                      <div
                        style={{
                          width: 28, height: 28, borderRadius: 8, flexShrink: 0,
                          background: 'rgba(59,130,246,0.15)',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontSize: 11, fontWeight: 700, color: '#60a5fa',
                        }}
                      >
                        {p.firstName[0]}{p.lastName[0]}
                      </div>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 500, color: '#f1f5f9' }}>
                          {p.firstName} {p.lastName}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748b' }}>
                          MRN: {p.medicalRecordNumber}
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              )}
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

            {/* Notification Bell */}
            <NotificationCenter />
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
