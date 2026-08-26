import { useState } from 'react';
import { Link } from 'react-router-dom';
import { authService } from '@/services/authService';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Building2, Mail, Lock, Phone, User, Globe2, Loader2, AlertCircle, ArrowRight, ArrowLeft, CheckCircle2 } from 'lucide-react';
import { tenantUrl } from '@/utils/tenantHost';

const STEPS = ['Hospital Info', 'Admin Account', 'Review'];

export function RegisterPage() {
  const [step, setStep] = useState(0);
  const [form, setForm] = useState({
    hospitalName: '', subdomain: '', contactEmail: '', phone: '',
    adminFirstName: '', adminLastName: '', adminEmail: '', adminPassword: '',
  });
  const [error, setError]   = useState('');
  const [loading, setLoading] = useState(false);

  const update = (field: string, value: string) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (step < 1) { setStep((s) => s + 1); return; }
    if (step === 1) { setStep(2); return; }
    setError('');
    setLoading(true);
    try {
      await authService.registerTenant(form);

      // The workspace now has its own hostname, and that is where it has to be used. Registration
      // runs on the chooser (app.<base>), so the session this call established is bound to a host
      // that belongs to no hospital — the refresh cookie is host-only and would not travel to the
      // new tenant host anyway. Send the browser there to sign in once, on the right origin.
      window.location.assign(tenantUrl(form.subdomain, '/login?welcome=1'));
      return;
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || 'Registration failed.');
      setStep(0);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div className="mb-7">
        <h1 className="text-3xl font-extrabold text-white mb-2" style={{ fontFamily: 'Plus Jakarta Sans, Inter, sans-serif' }}>
          Register Hospital
        </h1>
        <p style={{ color: 'var(--clr-text-3, #64748b)', fontSize: '14px' }}>
          Set up your AI-powered clinical workspace
        </p>
      </div>

      {/* Step indicator */}
      <div className="flex items-center gap-1 mb-6">
        {STEPS.map((label, i) => (
          <div key={i} className="flex items-center gap-1">
            <div className={`flex items-center justify-center h-6 w-6 rounded-full text-[11px] font-bold transition-all ${
              i < step ? 'bg-emerald-500 text-white' : i === step ? 'bg-blue-500 text-white' : 'text-slate-400'
            }`}
              style={{ background: i < step ? '#10b981' : i === step ? '#3b82f6' : 'rgba(255,255,255,0.07)' }}
            >
              {i < step ? <CheckCircle2 className="h-3.5 w-3.5" /> : i + 1}
            </div>
            <span className={`text-xs font-medium ${i === step ? 'text-white' : 'text-slate-500'}`}>{label}</span>
            {i < STEPS.length - 1 && <div className="flex-1 h-px w-6 mx-1" style={{ background: i < step ? '#10b981' : 'var(--clr-border, #1e2d45)' }} />}
          </div>
        ))}
      </div>

      {/* Form card */}
      <div className="rounded-2xl p-7" style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}>
        {error && (
          <div className="flex items-start gap-3 rounded-xl p-3.5 mb-5 text-sm"
            style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)', color: '#fca5a5' }}>
            <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" style={{ color: '#ef4444' }} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {step === 0 && (
            <div className="space-y-4 animate-in">
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <Label>Hospital Name</Label>
                  <Input placeholder="City General Hospital" value={form.hospitalName}
                    onChange={(e) => update('hospitalName', e.target.value)} required prefix={<Building2 className="h-4 w-4" />} />
                </div>
                <div className="col-span-2">
                  <Label>Subdomain / Workspace</Label>
                  <Input placeholder="city-general" value={form.subdomain}
                    onChange={(e) => update('subdomain', e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
                    required prefix={<Globe2 className="h-4 w-4" />}
                    suffix={<span className="text-xs font-mono" style={{ color: 'var(--clr-text-3)' }}>.medai.app</span>} />
                  <p className="mt-1.5 text-xs" style={{ color: 'var(--clr-text-3)' }}>Lowercase letters, numbers and hyphens only</p>
                </div>
                <div>
                  <Label>Contact Email</Label>
                  <Input type="email" placeholder="info@hospital.com" value={form.contactEmail}
                    onChange={(e) => update('contactEmail', e.target.value)} required prefix={<Mail className="h-4 w-4" />} />
                </div>
                <div>
                  <Label>Phone <span className="font-normal opacity-50">(optional)</span></Label>
                  <Input placeholder="+1 555 000 0000" value={form.phone}
                    onChange={(e) => update('phone', e.target.value)} prefix={<Phone className="h-4 w-4" />} />
                </div>
              </div>
            </div>
          )}

          {step === 1 && (
            <div className="space-y-4 animate-in">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>First Name</Label>
                  <Input placeholder="Jane" value={form.adminFirstName}
                    onChange={(e) => update('adminFirstName', e.target.value)} required prefix={<User className="h-4 w-4" />} />
                </div>
                <div>
                  <Label>Last Name</Label>
                  <Input placeholder="Smith" value={form.adminLastName}
                    onChange={(e) => update('adminLastName', e.target.value)} required />
                </div>
                <div className="col-span-2">
                  <Label>Admin Email</Label>
                  <Input type="email" placeholder="admin@hospital.com" value={form.adminEmail}
                    onChange={(e) => update('adminEmail', e.target.value)} required prefix={<Mail className="h-4 w-4" />} />
                </div>
                <div className="col-span-2">
                  <Label>Password</Label>
                  <Input type="password" placeholder="Minimum 8 characters" value={form.adminPassword}
                    onChange={(e) => update('adminPassword', e.target.value)} required minLength={8}
                    prefix={<Lock className="h-4 w-4" />} />
                </div>
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-3 animate-in">
              <p className="text-xs font-semibold uppercase tracking-widest" style={{ color: 'var(--clr-text-3)' }}>Review your details</p>
              {[
                ['Hospital', form.hospitalName],
                ['Workspace', form.subdomain],
                ['Contact Email', form.contactEmail],
                ['Admin', `${form.adminFirstName} ${form.adminLastName}`],
                ['Admin Email', form.adminEmail],
              ].map(([label, value]) => (
                <div key={label} className="flex justify-between items-center py-2.5 px-3 rounded-lg"
                  style={{ background: 'var(--surface-2, #1a2235)', border: '1px solid var(--clr-border, #1e2d45)' }}>
                  <span className="text-xs" style={{ color: 'var(--clr-text-3)' }}>{label}</span>
                  <span className="text-xs font-semibold text-white">{value}</span>
                </div>
              ))}
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-2 pt-2">
            {step > 0 && (
              <Button type="button" variant="secondary" onClick={() => setStep((s) => s - 1)} className="flex-1">
                <ArrowLeft className="h-4 w-4" /> Back
              </Button>
            )}
            <Button type="submit" size="default" className="flex-1" disabled={loading}>
              {loading ? <><Loader2 className="h-4 w-4 animate-spin" /> Registering…</> :
                step < 2 ? <>Next <ArrowRight className="h-4 w-4" /></> : <>Create Workspace <ArrowRight className="h-4 w-4" /></>}
            </Button>
          </div>
        </form>
      </div>

      <p className="text-center text-sm mt-5" style={{ color: 'var(--clr-text-3)' }}>
        Already registered?{' '}
        <Link to="/login" className="text-blue-400 font-semibold hover:text-blue-300 transition-colors">Sign in →</Link>
      </p>
    </div>
  );
}
