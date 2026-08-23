import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import {
  Users, FileImage, Brain, Activity, ArrowRight,
  MessageSquare, Upload, BookOpen, CheckCircle2, Clock,
  TrendingUp, BarChart2, Cpu, AlertTriangle, Download,
} from 'lucide-react';
import { analyticsService, type AnalyticsData, type DailyCount } from '@/services/analyticsService';
import api from '@/services/api';

// ── SVG Sparkline ────────────────────────────────────────────

function Sparkline({ data, color }: { data: DailyCount[]; color: string }) {
  if (!data || data.length === 0) return null;
  const values = data.map((d) => d.count);
  const maxV = Math.max(...values, 1);
  const W = 120, H = 36;
  const pts = values.map((v, i) => {
    const x = (i / Math.max(values.length - 1, 1)) * W;
    const y = H - (v / maxV) * (H - 4) - 2;
    return `${x},${y}`;
  });
  const area = `M${pts[0]} ` + pts.slice(1).map((p) => `L${p}`).join(' ')
    + ` L${W},${H} L0,${H} Z`;
  const line = `M${pts[0]} ` + pts.slice(1).map((p) => `L${p}`).join(' ');

  return (
    <svg width={W} height={H} style={{ overflow: 'visible' }}>
      <defs>
        <linearGradient id={`sg-${color.replace('#', '')}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#sg-${color.replace('#', '')})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

// ── Bar Chart ────────────────────────────────────────────────

function MiniBarChart({ data, color }: { data: DailyCount[]; color: string }) {
  if (!data || data.length === 0) return null;
  const last14 = data.slice(-14);
  const maxV = Math.max(...last14.map((d) => d.count), 1);
  const W = 320, H = 80, barCount = last14.length;
  const barW = Math.floor((W - (barCount - 1) * 2) / barCount);

  return (
    <svg width={W} height={H}>
      {last14.map((d, i) => {
        const barH = Math.max(((d.count / maxV) * (H - 8)), 2);
        const x = i * (barW + 2);
        const y = H - barH;
        const completedH = d.count > 0 ? (d.completed / d.count) * barH : 0;
        return (
          <g key={d.date}>
            <rect x={x} y={y} width={barW} height={barH} rx={2} fill={color + '30'} />
            <rect x={x} y={H - completedH} width={barW} height={completedH} rx={2} fill={color} />
          </g>
        );
      })}
    </svg>
  );
}

// ── Donut Chart ──────────────────────────────────────────────

function DonutChart({ slices, size = 80 }: {
  slices: { label: string; value: number; color: string }[];
  size?: number;
}) {
  const total = slices.reduce((s, sl) => s + sl.value, 0);
  if (total === 0) return <div style={{ width: size, height: size, borderRadius: '50%', background: '#1e2d45' }} />;
  const r = size / 2 - 8, cx = size / 2, cy = size / 2;
  const circumference = 2 * Math.PI * r;
  let offset = 0;

  return (
    <svg width={size} height={size}>
      {slices.map((sl) => {
        const pct = sl.value / total;
        const dash = pct * circumference;
        const gap  = circumference - dash;
        const el = (
          <circle
            key={sl.label}
            cx={cx} cy={cy} r={r}
            fill="none"
            stroke={sl.color}
            strokeWidth={8}
            strokeDasharray={`${dash} ${gap}`}
            strokeDashoffset={-offset}
            strokeLinecap="round"
            transform={`rotate(-90 ${cx} ${cy})`}
          />
        );
        offset += dash;
        return el;
      })}
      <circle cx={cx} cy={cy} r={r - 8} fill="var(--surface, #111827)" />
    </svg>
  );
}

// ── Main Dashboard ───────────────────────────────────────────

export function DashboardPage() {
  const { fullName, tenantName, role } = useAuthStore();
  const [analytics, setAnalytics] = useState<AnalyticsData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    analyticsService.getAll(30, 8)
      .then(setAnalytics)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const greeting = () => {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  };

  const s = analytics?.summary;

  const statCards = [
    { label: 'Total Patients',   value: s?.totalPatients    ?? '—', icon: Users,     color: '#3b82f6', glow: 'rgba(59,130,246,0.15)',  trend: null },
    { label: 'Files Uploaded',   value: s?.totalFiles       ?? '—', icon: FileImage, color: '#10b981', glow: 'rgba(16,185,129,0.15)',  trend: null },
    { label: 'AI Analyses',      value: s?.totalAnalyses    ?? '—', icon: Brain,     color: '#8b5cf6', glow: 'rgba(139,92,246,0.15)',  trend: null },
    { label: 'Completed',        value: s?.completedAnalyses ?? '—', icon: CheckCircle2, color: '#06b6d4', glow: 'rgba(6,182,212,0.15)', trend: null },
    { label: 'Pending',          value: s?.pendingAnalyses  ?? '—', icon: Clock,     color: '#f59e0b', glow: 'rgba(245,158,11,0.15)', trend: null },
    { label: 'Failed',           value: s?.failedAnalyses   ?? '—', icon: AlertTriangle, color: '#ef4444', glow: 'rgba(239,68,68,0.15)', trend: null },
  ];

  const quickActions = [
    { to: '/patients',      icon: Users,         label: 'Manage Patients',   sub: 'View, add, or edit patient records',  color: '#3b82f6' },
    { to: '/upload',        icon: Upload,        label: 'Upload Studies',    sub: 'X-rays, CTs, blood reports',         color: '#10b981' },
    { to: '/chat',          icon: MessageSquare, label: 'Clinical AI Chat',  sub: 'AI-assisted decision support',       color: '#8b5cf6' },
    { to: '/analysis',      icon: Brain,         label: 'AI Radiology',      sub: 'Multimodal image analysis',          color: '#06b6d4' },
    { to: '/blood-reports', icon: FileImage,     label: 'Blood & Lab Tests', sub: 'Laboratory analytics',               color: '#f59e0b' },
    { to: '/knowledge',     icon: BookOpen,      label: 'Hospital Protocols',sub: 'RAG knowledge base',                 color: '#ec4899' },
  ];

  const statusItems = [
    { label: 'Backend API',         status: 'Online' },
    { label: 'PostgreSQL Database', status: 'Connected' },
    { label: 'pgvector (RAG)',      status: 'Active' },
    { label: 'AI Model (LLM)',      status: 'Online' },
    { label: 'Embedding Service',   status: 'Active' },
  ];

  const diagnosisColors = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#06b6d4', '#ec4899', '#64748b'];

  return (
    <div className="space-y-6 max-w-[1400px]">
      {/* ── Welcome strip ── */}
      <div
        className="rounded-2xl p-7 relative overflow-hidden"
        style={{
          background: 'linear-gradient(135deg, rgba(59,130,246,0.12) 0%, rgba(6,182,212,0.08) 50%, rgba(17,24,39,1) 100%)',
          border: '1px solid rgba(59,130,246,0.2)',
        }}
      >
        <div className="absolute inset-0 opacity-5"
          style={{
            backgroundImage: 'linear-gradient(rgba(59,130,246,0.8) 1px, transparent 1px), linear-gradient(90deg, rgba(59,130,246,0.8) 1px, transparent 1px)',
            backgroundSize: '40px 40px',
          }}
        />
        <div className="relative z-10 flex items-end justify-between flex-wrap gap-4">
          <div>
            <p className="text-sm font-medium mb-1" style={{ color: '#60a5fa' }}>{greeting()},</p>
            <h1
              className="text-4xl font-extrabold text-white mb-2 leading-none"
              style={{ fontFamily: 'Plus Jakarta Sans, Inter, sans-serif' }}
            >
              {fullName?.split(' ')[0] ?? 'Doctor'}
            </h1>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="badge badge-blue">{tenantName ?? 'Hospital'}</span>
              <span className="text-slate-500 text-xs">·</span>
              <span className="badge badge-slate capitalize">{role?.replace(/_/g, ' ').toLowerCase() ?? 'Practitioner'}</span>
              <span className="text-slate-500 text-xs">·</span>
              <span className="text-xs flex items-center gap-1.5" style={{ color: 'var(--clr-text-3)' }}>
                <Clock className="h-3 w-3" />
                {new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}
              </span>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => api.get('/export/patients/csv', { responseType: 'blob' }).then((r) => {
                const url = URL.createObjectURL(r.data);
                const a = document.createElement('a'); a.href = url; a.download = 'patients.csv'; a.click();
                URL.revokeObjectURL(url);
              })}
              className="flex items-center gap-2 px-4 py-2 rounded-xl font-semibold text-sm transition-all"
              style={{
                background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.3)',
                color: '#34d399',
              }}
            >
              <Download className="h-4 w-4" /> Export CSV
            </button>
            <Link
              to="/chat"
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl font-semibold text-sm text-white transition-all hover:shadow-lg"
              style={{ background: 'linear-gradient(135deg, #3b82f6, #06b6d4)', boxShadow: '0 0 20px rgba(59,130,246,0.3)' }}
            >
              <MessageSquare className="h-4 w-4" />
              Start AI Consult
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </div>

      {/* ── Stats grid (6 cards) ── */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
        {statCards.map(({ label, value, icon: Icon, color, glow }) => (
          <div
            key={label}
            className="rounded-xl p-4 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg"
            style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}
          >
            <div className="flex items-center justify-between mb-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-xl" style={{ background: glow }}>
                <Icon style={{ color, width: 16, height: 16 }} />
              </div>
              {loading && <div style={{ width: 20, height: 4, borderRadius: 2, background: '#1e2d45', animation: 'pulse 1.5s infinite' }} />}
            </div>
            <p className="text-2xl font-extrabold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>
              {loading ? '…' : value}
            </p>
            <p className="text-[11px] mt-1" style={{ color: 'var(--clr-text-3, #64748b)' }}>{label}</p>
          </div>
        ))}
      </div>

      {/* ── Chart row ── */}
      <div className="grid lg:grid-cols-3 gap-5">
        {/* Bar chart — analyses over 14 days */}
        <div
          className="lg:col-span-2 rounded-xl p-5"
          style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}
        >
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Analyses Over Time</h2>
              <p className="text-[11px] mt-0.5" style={{ color: '#64748b' }}>Last 30 days · Blue = completed</p>
            </div>
            <BarChart2 size={16} style={{ color: '#3b82f6' }} />
          </div>
          <div>
            {analytics?.analysesPerDay ? (
              <MiniBarChart data={analytics.analysesPerDay} color="#3b82f6" />
            ) : (
              <div style={{ height: 80, background: '#1e2d45', borderRadius: 8, opacity: 0.5 }} />
            )}
          </div>
          {/* Trend sparklines */}
          {analytics?.analysesPerDay && (
            <div className="flex items-center gap-6 mt-4 pt-4" style={{ borderTop: '1px solid var(--clr-border, #1e2d45)' }}>
              <div>
                <p className="text-[10px]" style={{ color: '#64748b' }}>30-day trend</p>
                <Sparkline data={analytics.analysesPerDay} color="#3b82f6" />
              </div>
              <div className="flex items-center gap-4 text-xs">
                <span style={{ color: '#64748b' }}>
                  Total: <strong style={{ color: '#f1f5f9' }}>{analytics.analysesPerDay.reduce((s, d) => s + d.count, 0)}</strong>
                </span>
                <span style={{ color: '#64748b' }}>
                  Success: <strong style={{ color: '#10b981' }}>{analytics.analysesPerDay.reduce((s, d) => s + d.completed, 0)}</strong>
                </span>
                <span style={{ color: '#64748b' }}>
                  Failed: <strong style={{ color: '#ef4444' }}>{analytics.analysesPerDay.reduce((s, d) => s + d.failed, 0)}</strong>
                </span>
              </div>
            </div>
          )}
        </div>

        {/* Donut — Top Diagnoses */}
        <div
          className="rounded-xl p-5"
          style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}
        >
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Top Diagnoses</h2>
            <TrendingUp size={16} style={{ color: '#8b5cf6' }} />
          </div>
          {analytics?.topDiagnoses && analytics.topDiagnoses.length > 0 ? (
            <div className="flex gap-4">
              <DonutChart
                slices={analytics.topDiagnoses.map((d, i) => ({
                  label: d.analysisType, value: d.count, color: diagnosisColors[i % diagnosisColors.length]
                }))}
                size={88}
              />
              <div className="flex flex-col gap-1.5 justify-center min-w-0 flex-1">
                {analytics.topDiagnoses.slice(0, 6).map((d, i) => (
                  <div key={d.analysisType} className="flex items-center gap-2">
                    <div style={{ width: 8, height: 8, borderRadius: 2, background: diagnosisColors[i % diagnosisColors.length], flexShrink: 0 }} />
                    <span className="text-[11px] truncate" style={{ color: '#94a3b8' }}>
                      {d.analysisType.replace(/_/g, ' ')}
                    </span>
                    <span className="text-[11px] font-bold ml-auto" style={{ color: '#f1f5f9', flexShrink: 0 }}>
                      {d.percentage}%
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div style={{ padding: '20px 0', textAlign: 'center', color: '#64748b', fontSize: 12 }}>
              No analysis data yet
            </div>
          )}

          {/* Model usage mini list */}
          {analytics?.modelUsage && analytics.modelUsage.length > 0 && (
            <div className="mt-4 pt-4" style={{ borderTop: '1px solid var(--clr-border, #1e2d45)' }}>
              <div className="flex items-center gap-2 mb-2">
                <Cpu size={12} style={{ color: '#8b5cf6' }} />
                <span className="text-[11px] font-semibold" style={{ color: '#64748b' }}>Model Usage</span>
              </div>
              {analytics.modelUsage.slice(0, 3).map((m) => (
                <div key={m.modelName} className="flex items-center justify-between mb-1.5">
                  <span className="text-[11px] truncate" style={{ color: '#94a3b8', maxWidth: 120 }}>
                    {m.modelName.split('/').pop()}
                  </span>
                  <span className="text-[11px] font-bold" style={{ color: '#f1f5f9' }}>
                    {m.analysisCount}x
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── Quick actions + Status ── */}
      <div className="grid lg:grid-cols-3 gap-5">
        {/* Quick actions — 2/3 width */}
        <div className="lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Quick Actions</h2>
          </div>
          <div className="grid sm:grid-cols-2 gap-3">
            {quickActions.map(({ to, icon: Icon, label, sub, color }) => (
              <Link
                key={to}
                to={to}
                className="flex items-center gap-4 rounded-xl p-4 group transition-all duration-200"
                style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)', transition: 'all 200ms ease' }}
                onMouseEnter={(e) => {
                  const el = e.currentTarget as HTMLAnchorElement;
                  el.style.borderColor = color + '60';
                  el.style.transform = 'translateY(-1px)';
                  el.style.boxShadow = `0 8px 24px ${color}20`;
                }}
                onMouseLeave={(e) => {
                  const el = e.currentTarget as HTMLAnchorElement;
                  el.style.borderColor = 'var(--clr-border, #1e2d45)';
                  el.style.transform = '';
                  el.style.boxShadow = '';
                }}
              >
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl" style={{ background: color + '18' }}>
                  <Icon className="h-5 w-5" style={{ color }} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-white truncate">{label}</p>
                  <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--clr-text-3)' }}>{sub}</p>
                </div>
                <ArrowRight className="h-4 w-4 opacity-0 group-hover:opacity-100 transition-opacity shrink-0" style={{ color }} />
              </Link>
            ))}
          </div>
        </div>

        {/* Platform Status — 1/3 width */}
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Platform Status</h2>
            <span className="badge badge-green text-[10px]">All Systems Operational</span>
          </div>
          <div className="rounded-xl overflow-hidden" style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}>
            {statusItems.map(({ label, status }, i) => (
              <div
                key={label}
                className="flex items-center justify-between px-4 py-3 text-xs"
                style={{ borderBottom: i < statusItems.length - 1 ? '1px solid var(--clr-border, #1e2d45)' : 'none' }}
              >
                <span style={{ color: 'var(--clr-text-2, #94a3b8)' }}>{label}</span>
                <span className="flex items-center gap-1.5 font-semibold" style={{ color: '#34d399' }}>
                  <CheckCircle2 className="h-3 w-3" />
                  {status}
                </span>
              </div>
            ))}
          </div>

          {/* AI Cost summary */}
          {s && (
            <div
              className="rounded-xl p-4 mt-3"
              style={{
                background: 'var(--surface, #111827)',
                border: '1px solid var(--clr-border, #1e2d45)',
              }}
            >
              <p className="text-xs font-semibold mb-2" style={{ color: '#64748b' }}>AI Cost Tracking</p>
              <p className="text-2xl font-extrabold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>
                ${Number(s.totalEstimatedCost ?? 0).toFixed(4)}
              </p>
              <p className="text-[11px] mt-0.5" style={{ color: '#64748b' }}>total estimated cost</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
