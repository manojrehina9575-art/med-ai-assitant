import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { patientService } from '@/services/patientService';
import {
  Users, FileImage, Brain, Activity, ArrowRight,
  MessageSquare, Upload, BookOpen, CheckCircle2, Clock,
} from 'lucide-react';

interface StatCard {
  label: string;
  value: string | number;
  icon: React.ElementType;
  color: string;
  glow: string;
}

export function DashboardPage() {
  const { fullName, tenantName, role } = useAuthStore();
  const [patientCount, setPatientCount] = useState<number | null>(null);

  useEffect(() => {
    patientService.list(0, 1)
      .then((res) => setPatientCount(res.totalElements))
      .catch(() => setPatientCount(0));
  }, []);

  const greeting = () => {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  };

  const stats: StatCard[] = [
    { label: 'Total Patients',  value: patientCount ?? '—', icon: Users,     color: '#3b82f6', glow: 'rgba(59,130,246,0.15)' },
    { label: 'Files Uploaded',  value: '—',                 icon: FileImage, color: '#10b981', glow: 'rgba(16,185,129,0.15)' },
    { label: 'AI Analyses',     value: '—',                 icon: Brain,     color: '#8b5cf6', glow: 'rgba(139,92,246,0.15)' },
    { label: 'Active Sessions', value: '—',                 icon: Activity,  color: '#f59e0b', glow: 'rgba(245,158,11,0.15)' },
  ];

  const quickActions = [
    { to: '/patients',     icon: Users,          label: 'Manage Patients',    sub: 'View, add, or edit patient records',     color: '#3b82f6' },
    { to: '/upload',       icon: Upload,         label: 'Upload Studies',     sub: 'X-rays, CTs, blood reports',            color: '#10b981' },
    { to: '/chat',         icon: MessageSquare,  label: 'Clinical AI Chat',   sub: 'AI-assisted decision support',          color: '#8b5cf6' },
    { to: '/analysis',     icon: Brain,          label: 'AI Radiology',       sub: 'Multimodal image analysis',             color: '#06b6d4' },
    { to: '/blood-reports',icon: FileImage,      label: 'Blood & Lab Tests',  sub: 'Laboratory analytics',                  color: '#f59e0b' },
    { to: '/knowledge',    icon: BookOpen,       label: 'Hospital Protocols', sub: 'RAG knowledge base',                    color: '#ec4899' },
  ];

  const statusItems = [
    { label: 'Backend API',         status: 'Online',    variant: 'green' },
    { label: 'PostgreSQL Database', status: 'Connected', variant: 'green' },
    { label: 'pgvector (RAG)',      status: 'Active',    variant: 'green' },
    { label: 'AI Model (LLM)',      status: 'Online',    variant: 'green' },
    { label: 'Embedding Service',   status: 'Active',    variant: 'green' },
  ];

  return (
    <div className="space-y-7 max-w-[1400px]">
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
            <p className="text-sm font-medium mb-1" style={{ color: '#60a5fa' }}>
              {greeting()},
            </p>
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

      {/* ── Stats row ── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map(({ label, value, icon: Icon, color, glow }) => (
          <div
            key={label}
            className="rounded-xl p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg"
            style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}
          >
            <div className="flex items-start justify-between mb-3">
              <p className="text-xs font-medium" style={{ color: 'var(--clr-text-3, #64748b)' }}>{label}</p>
              <div className="flex h-9 w-9 items-center justify-center rounded-xl" style={{ background: glow }}>
                <Icon className="h-4.5 w-4.5" style={{ color, width: 18, height: 18 }} />
              </div>
            </div>
            <p className="text-3xl font-extrabold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>{value}</p>
          </div>
        ))}
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
                style={{
                  background: 'var(--surface, #111827)',
                  border: '1px solid var(--clr-border, #1e2d45)',
                  transition: 'all 200ms ease',
                }}
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
                <div
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
                  style={{ background: color + '18' }}
                >
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
          <div
            className="rounded-xl overflow-hidden"
            style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}
          >
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
        </div>
      </div>
    </div>
  );
}
