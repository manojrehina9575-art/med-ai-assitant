import { Outlet, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Brain, Shield, Activity, Zap } from 'lucide-react';

export function AuthLayout() {
  const { isAuthenticated } = useAuthStore();

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="flex min-h-screen bg-background">
      {/* Left Panel — Branding */}
      <div
        className="hidden lg:flex lg:flex-col lg:w-[52%] relative overflow-hidden"
        style={{
          background: 'linear-gradient(145deg, #0a0f1e 0%, #0d1a3a 40%, #0a1628 70%, #0d0f1e 100%)',
        }}
      >
        {/* Animated grid overlay */}
        <div
          className="absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage: `linear-gradient(rgba(59,130,246,0.6) 1px, transparent 1px),
                              linear-gradient(90deg, rgba(59,130,246,0.6) 1px, transparent 1px)`,
            backgroundSize: '48px 48px',
          }}
        />

        {/* Glow blobs */}
        <div
          className="absolute top-[-10%] left-[-5%] w-[500px] h-[500px] rounded-full opacity-20 pointer-events-none"
          style={{ background: 'radial-gradient(circle, #3b82f6 0%, transparent 70%)' }}
        />
        <div
          className="absolute bottom-[-5%] right-[-10%] w-[400px] h-[400px] rounded-full opacity-10 pointer-events-none"
          style={{ background: 'radial-gradient(circle, #06b6d4 0%, transparent 70%)' }}
        />

        <div className="relative z-10 flex flex-col h-full p-12">
          {/* Logo */}
          <div className="flex items-center gap-3 mb-auto">
            <div
              className="flex h-11 w-11 items-center justify-center rounded-xl"
              style={{ background: 'linear-gradient(135deg, #3b82f6, #06b6d4)', boxShadow: '0 0 24px rgba(59,130,246,0.4)' }}
            >
              <Brain className="h-6 w-6 text-white" />
            </div>
            <div>
              <p className="text-white font-bold text-lg leading-none" style={{ fontFamily: 'Plus Jakarta Sans' }}>Med-AI</p>
              <p className="text-slate-400 text-xs">Clinical Intelligence</p>
            </div>
          </div>

          {/* Hero text */}
          <div className="py-12">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full mb-6 text-xs font-semibold text-blue-300"
              style={{ background: 'rgba(59,130,246,0.12)', border: '1px solid rgba(59,130,246,0.25)' }}>
              <span className="dot-online" />
              AI-Powered Clinical Intelligence Platform
            </div>
            <h1 className="text-5xl font-extrabold text-white leading-[1.1] mb-5" style={{ fontFamily: 'Plus Jakarta Sans' }}>
              Diagnose faster.<br />
              <span className="gradient-text">Trust the data.</span>
            </h1>
            <p className="text-slate-400 text-lg leading-relaxed max-w-sm">
              Multimodal AI for radiology, lab analytics, and clinical decision support — trusted by medical teams worldwide.
            </p>
          </div>

          {/* Feature pills */}
          <div className="grid grid-cols-3 gap-3 mb-12">
            {[
              { icon: Shield, label: 'HIPAA Compliant', sub: 'Row-level security' },
              { icon: Activity, label: 'Real-time AI', sub: 'Sub-second analysis' },
              { icon: Zap, label: 'Multi-tenant', sub: 'Hospital isolation' },
            ].map(({ icon: Icon, label, sub }) => (
              <div
                key={label}
                className="rounded-xl p-4"
                style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)' }}
              >
                <Icon className="h-5 w-5 text-blue-400 mb-2" />
                <p className="text-white text-xs font-semibold">{label}</p>
                <p className="text-slate-500 text-[11px] mt-0.5">{sub}</p>
              </div>
            ))}
          </div>

          {/* Stats row */}
          <div className="flex gap-8">
            {[
              { value: '95%+', label: 'Diagnostic Accuracy' },
              { value: '<3s', label: 'Analysis Time' },
              { value: '24/7', label: 'AI Availability' },
            ].map(({ value, label }) => (
              <div key={label}>
                <p className="text-2xl font-extrabold gradient-text" style={{ fontFamily: 'Plus Jakarta Sans' }}>{value}</p>
                <p className="text-slate-400 text-xs mt-0.5">{label}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right Panel — Form */}
      <div className="flex flex-1 items-center justify-center p-8">
        <div className="w-full max-w-md animate-in">
          {/* Mobile logo */}
          <div className="flex items-center gap-3 mb-8 lg:hidden">
            <div
              className="flex h-10 w-10 items-center justify-center rounded-xl"
              style={{ background: 'linear-gradient(135deg, #3b82f6, #06b6d4)' }}
            >
              <Brain className="h-5 w-5 text-white" />
            </div>
            <div>
              <p className="text-white font-bold text-base leading-none" style={{ fontFamily: 'Plus Jakarta Sans' }}>Med-AI</p>
              <p className="text-slate-400 text-xs">Clinical Intelligence</p>
            </div>
          </div>
          <Outlet />
        </div>
      </div>
    </div>
  );
}
