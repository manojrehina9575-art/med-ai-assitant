import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { authService } from '@/services/authService';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Building2, Mail, Lock, Loader2, AlertCircle, ArrowRight, CheckCircle2 } from 'lucide-react';
import { getBaseDomain, getTenantFromHostname, tenantUrl } from '@/utils/tenantHost';

export function LoginPage() {
  const hostSubdomain = getTenantFromHostname();
  // Set by the redirect that follows registration, so the admin lands on a page that explains why
  // they are being asked to sign in on a hostname they have not seen before.
  const justRegistered = new URLSearchParams(window.location.search).has('welcome');
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
      // Confirm the workspace exists before sending the browser anywhere, so a typo produces a
      // clear message here rather than a bare 404 on a hostname that means nothing to the user.
      const tenant = await authService.findTenant(activeSubdomain);

      // On the workspace chooser there is no tenant in the hostname, so credentials must not be
      // spent here: the session cookie is host-only and would be set on app.<base>, which belongs
      // to no hospital. Hand the browser to the workspace's own host and sign in there.
      if (!hostSubdomain) {
        window.location.assign(tenantUrl(activeSubdomain, '/login'));
        return;
      }

      const res = await authService.login(email.trim(), password, tenant.id);
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
          {hostSubdomain ? 'Welcome back' : 'Find your workspace'}
        </h1>
        <p style={{ color: 'var(--clr-text-3, #64748b)', fontSize: '14px' }}>
          {hostSubdomain
            ? <>Signing into workspace <span className="text-blue-400 font-semibold">'{hostSubdomain}'</span></>
            : 'Enter your hospital workspace to continue to its sign-in page'}
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
        {justRegistered && !error && (
          <div
            className="flex items-start gap-3 rounded-xl p-3.5 mb-5 text-sm"
            style={{ background: 'rgba(34,197,94,0.08)', border: '1px solid rgba(34,197,94,0.25)', color: '#86efac' }}
          >
            <CheckCircle2 className="h-4 w-4 mt-0.5 shrink-0" style={{ color: '#22c55e' }} />
            <span>Workspace created. This is your hospital's address — bookmark it and sign in with the admin account you just set up.</span>
          </div>
        )}

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
          {/* Workspace. Present only on the chooser; on a tenant host the hostname supplies it. */}
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
                You will continue to {subdomain ? `${subdomain}.${getBaseDomain()}` : `your-workspace.${getBaseDomain()}`}
              </p>
            </div>
          )}

          {hostSubdomain && (
          <>
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
          </>
          )}

          <div className="pt-2">
            <Button type="submit" size="lg" className="w-full" disabled={loading}>
              {loading ? (
                <><Loader2 className="h-4 w-4 animate-spin" /> {hostSubdomain ? 'Signing in…' : 'Finding workspace…'}</>
              ) : (
                <>{hostSubdomain ? 'Sign In' : 'Continue'} <ArrowRight className="h-4 w-4 ml-1" /></>
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
