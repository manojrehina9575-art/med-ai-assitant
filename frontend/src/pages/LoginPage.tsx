import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { authService } from '@/services/authService';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Building2, Mail, Lock, Loader2, AlertCircle, ArrowRight } from 'lucide-react';

function getSubdomainFromHostname(): string {
  const hostname = window.location.hostname;
  if (hostname === 'localhost' || /^\d{1,3}(\.\d{1,3}){3}$/.test(hostname)) return '';
  const parts = hostname.split('.');
  if (parts.length >= 2) return parts[0];
  return '';
}

export function LoginPage() {
  const hostSubdomain = getSubdomainFromHostname();
  const [subdomain, setSubdomain] = useState(hostSubdomain);
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);
  const navigate  = useNavigate();
  const { setAuth } = useAuthStore();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const activeSubdomain = (hostSubdomain || subdomain).trim().toLowerCase();
      if (!activeSubdomain) {
        setError('Hospital workspace is required.');
        setLoading(false);
        return;
      }
      const tenant = await authService.findTenant(activeSubdomain);
      const res    = await authService.login(email.trim(), password, tenant.id);
      setAuth(res);
      navigate('/dashboard');
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } } };
      setError(
        e.response?.status === 404
          ? 'Hospital workspace not found. Check the name with your administrator.'
          : e.response?.data?.message || 'Login failed. Please verify your credentials.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div className="mb-8">
        <h1
          className="text-3xl font-extrabold text-white mb-2"
          style={{ fontFamily: 'Plus Jakarta Sans, Inter, sans-serif' }}
        >
          Welcome back
        </h1>
        <p style={{ color: 'var(--clr-text-3, #64748b)', fontSize: '14px' }}>
          {hostSubdomain
            ? <>Signing into workspace <span className="text-blue-400 font-semibold">'{hostSubdomain}'</span></>
            : 'Sign in to your clinical dashboard'}
        </p>
      </div>

      {/* Form card */}
      <div
        className="rounded-2xl p-7"
        style={{
          background: 'var(--surface, #111827)',
          border: '1px solid var(--clr-border, #1e2d45)',
        }}
      >
        {/* Error */}
        {error && (
          <div
            className="flex items-start gap-3 rounded-xl p-3.5 mb-5 text-sm"
            style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)', color: '#fca5a5' }}
          >
            <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" style={{ color: '#ef4444' }} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Workspace (only if not from subdomain) */}
          {!hostSubdomain && (
            <div>
              <Label htmlFor="subdomain">Hospital workspace</Label>
              <Input
                id="subdomain"
                type="text"
                placeholder="e.g. lifeline"
                autoComplete="organization"
                value={subdomain}
                onChange={(e) => setSubdomain(e.target.value)}
                required
                prefix={<Building2 className="h-4 w-4" />}
              />
              <p className="mt-1.5 text-xs" style={{ color: 'var(--clr-text-3, #64748b)' }}>
                Your hospital's unique workspace name
              </p>
            </div>
          )}

          <div>
            <Label htmlFor="email">Email address</Label>
            <Input
              id="email"
              type="email"
              placeholder="doctor@hospital.com"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              prefix={<Mail className="h-4 w-4" />}
            />
          </div>

          <div>
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              placeholder="Enter your password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              prefix={<Lock className="h-4 w-4" />}
            />
          </div>

          <div className="pt-2">
            <Button type="submit" size="lg" className="w-full" disabled={loading}>
              {loading ? (
                <><Loader2 className="h-4 w-4 animate-spin" /> Signing in…</>
              ) : (
                <>Sign In <ArrowRight className="h-4 w-4 ml-1" /></>
              )}
            </Button>
          </div>
        </form>
      </div>

      {/* Register link */}
      <p className="text-center text-sm mt-5" style={{ color: 'var(--clr-text-3, #64748b)' }}>
        New hospital?{' '}
        <Link to="/register" className="text-blue-400 font-semibold hover:text-blue-300 transition-colors">
          Register your workspace →
        </Link>
      </p>
    </div>
  );
}
