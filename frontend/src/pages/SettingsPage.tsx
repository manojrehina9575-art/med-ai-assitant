import { useAuthStore } from '@/stores/authStore';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import {
  Shield, Bell, Database, User, Building2,
  Moon, Sun, Key, Globe2, Mail, Zap,
} from 'lucide-react';
import { useState } from 'react';

export function SettingsPage() {
  const { tenantName, role, email, fullName } = useAuthStore();
  const [darkMode, setDarkMode] = useState(true);

  const toggleDarkMode = () => {
    setDarkMode((prev) => {
      const next = !prev;
      document.documentElement.classList.toggle('light', !next);
      return next;
    });
  };

  const infoRows = [
    { icon: User,      label: 'Full Name', value: fullName ?? '—' },
    { icon: Mail,      label: 'Email',     value: email ?? '—' },
    { icon: Shield,    label: 'Role',      value: role?.replace(/_/g, ' ') ?? '—' },
    { icon: Building2, label: 'Hospital',  value: tenantName ?? '—' },
  ];

  const comingSoon = [
    { icon: Key,      title: 'Change Password',       sub: 'Update your login credentials securely' },
    { icon: Globe2,   title: '2-Factor Auth',          sub: 'Add an extra layer of account security' },
    { icon: Bell,     title: 'Email Notifications',    sub: 'Alerts for critical AI findings & reports' },
    { icon: Zap,      title: 'AI Model Config',        sub: 'Model selection, temperature, cost limits' },
    { icon: Database, title: 'Knowledge Base Manage',  sub: 'Upload, update, and purge hospital protocols' },
  ];

  return (
    <div className="space-y-7 max-w-[900px]">
      <div>
        <h1 className="text-2xl font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Settings</h1>
        <p className="text-sm mt-0.5" style={{ color: 'var(--clr-text-3)' }}>Manage your account and workspace preferences</p>
      </div>

      <div className="grid lg:grid-cols-2 gap-5">
        {/* Account Info */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <User className="h-4 w-4" style={{ color: '#3b82f6' }} />
              Account Info
            </CardTitle>
            <CardDescription>Your profile and role details</CardDescription>
          </CardHeader>
          <CardContent className="space-y-1 pt-0">
            {infoRows.map(({ icon: Icon, label, value }) => (
              <div key={label} className="flex items-center justify-between py-3"
                style={{ borderBottom: '1px solid var(--clr-border, #1e2d45)' }}>
                <span className="flex items-center gap-2 text-xs" style={{ color: 'var(--clr-text-3)' }}>
                  <Icon className="h-3.5 w-3.5" />
                  {label}
                </span>
                <span className="text-xs font-semibold text-white">{value}</span>
              </div>
            ))}
          </CardContent>
        </Card>

        {/* Appearance */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              {darkMode ? <Moon className="h-4 w-4" style={{ color: '#8b5cf6' }} /> : <Sun className="h-4 w-4" style={{ color: '#f59e0b' }} />}
              Appearance
            </CardTitle>
            <CardDescription>Choose how Med-AI looks to you</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between py-3 mb-3"
              style={{ borderBottom: '1px solid var(--clr-border)' }}>
              <div>
                <p className="text-sm font-medium text-white">Dark mode</p>
                <p className="text-xs mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
                  {darkMode ? 'Navy dark theme (default)' : 'Light clinical theme'}
                </p>
              </div>
              <button
                onClick={toggleDarkMode}
                className="relative inline-flex h-6 w-11 items-center rounded-full transition-colors"
                style={{ background: darkMode ? '#3b82f6' : '#475569' }}
              >
                <span
                  className="inline-block h-4 w-4 rounded-full bg-white shadow transition-transform"
                  style={{ transform: darkMode ? 'translateX(24px)' : 'translateX(4px)' }}
                />
              </button>
            </div>
            <p className="text-xs" style={{ color: 'var(--clr-text-3)' }}>
              Your preference is saved in your browser.
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Coming Soon */}
      <div>
        <h2 className="text-sm font-bold text-white mb-4" style={{ fontFamily: 'Plus Jakarta Sans' }}>Coming Soon</h2>
        <div className="rounded-2xl overflow-hidden" style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}>
          {comingSoon.map(({ icon: Icon, title, sub }, i) => (
            <div
              key={title}
              className="flex items-center gap-4 px-5 py-4 transition-colors"
              style={{ borderBottom: i < comingSoon.length - 1 ? '1px solid var(--clr-border, #1e2d45)' : 'none', opacity: 0.6, cursor: 'not-allowed' }}
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                style={{ background: 'rgba(255,255,255,0.05)' }}>
                <Icon className="h-4 w-4" style={{ color: 'var(--clr-text-2)' }} />
              </div>
              <div className="flex-1">
                <p className="text-sm font-medium text-white">{title}</p>
                <p className="text-xs mt-0.5" style={{ color: 'var(--clr-text-3)' }}>{sub}</p>
              </div>
              <span className="badge badge-slate text-[10px]">Coming soon</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
