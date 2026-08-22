import { useEffect, useState, useCallback } from 'react';
import { patientService } from '@/services/patientService';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import {
  Plus, Search, ChevronLeft, ChevronRight, User2, Loader2,
  X, AlertCircle, Phone, Mail, Droplets, Calendar, Hash,
} from 'lucide-react';
import type { Patient, PagedResponse } from '@/types';

const GENDER_OPTIONS = ['MALE', 'FEMALE', 'OTHER'];
const BLOOD_GROUPS   = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

const genderColor: Record<string, string> = {
  MALE: '#3b82f6', FEMALE: '#ec4899', OTHER: '#8b5cf6',
};

export function PatientsPage() {
  const [patients, setPatients] = useState<PagedResponse<Patient> | null>(null);
  const [search, setSearch]     = useState('');
  const [page, setPage]         = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [loading, setLoading]   = useState(false);

  const [form, setForm] = useState({
    medicalRecordNumber: '', firstName: '', lastName: '', dateOfBirth: '',
    gender: 'MALE', bloodGroup: '', phone: '', email: '',
  });
  const [createError, setCreateError] = useState('');
  const [creating, setCreating] = useState(false);

  const loadPatients = useCallback(async () => {
    setLoading(true);
    try {
      const data = await patientService.list(page, 20, search || undefined);
      setPatients(data);
    } catch { /* handle */ } finally { setLoading(false); }
  }, [page, search]);

  useEffect(() => { loadPatients(); }, [loadPatients]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError('');
    setCreating(true);
    try {
      await patientService.create(form);
      setShowCreate(false);
      setForm({ medicalRecordNumber: '', firstName: '', lastName: '', dateOfBirth: '', gender: 'MALE', bloodGroup: '', phone: '', email: '' });
      loadPatients();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setCreateError(error.response?.data?.message || 'Failed to create patient');
    } finally { setCreating(false); }
  };

  return (
    <div className="space-y-6 max-w-[1200px]">
      {/* ── Header ── */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Patient Registry</h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
            {patients?.totalElements ?? 0} patients registered
          </p>
        </div>
        <Button onClick={() => setShowCreate(!showCreate)} size="sm">
          {showCreate ? <><X className="h-4 w-4" /> Cancel</> : <><Plus className="h-4 w-4" /> Add Patient</>}
        </Button>
      </div>

      {/* ── Create Patient Panel ── */}
      {showCreate && (
        <div className="rounded-2xl p-6 animate-in"
          style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}>
          <h3 className="text-sm font-bold text-white mb-5" style={{ fontFamily: 'Plus Jakarta Sans' }}>New Patient Record</h3>
          {createError && (
            <div className="flex items-start gap-3 rounded-xl p-3.5 mb-4 text-sm"
              style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)', color: '#fca5a5' }}>
              <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" style={{ color: '#ef4444' }} />
              {createError}
            </div>
          )}
          <form onSubmit={handleCreate} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <Label>Medical Record No.</Label>
              <Input placeholder="MRN-001" value={form.medicalRecordNumber} onChange={(e) => setForm({ ...form, medicalRecordNumber: e.target.value })} required prefix={<Hash className="h-3.5 w-3.5" />} />
            </div>
            <div>
              <Label>First Name</Label>
              <Input placeholder="Jane" value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} required prefix={<User2 className="h-3.5 w-3.5" />} />
            </div>
            <div>
              <Label>Last Name</Label>
              <Input placeholder="Smith" value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} required />
            </div>
            <div>
              <Label>Date of Birth</Label>
              <Input type="date" value={form.dateOfBirth} onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })} required prefix={<Calendar className="h-3.5 w-3.5" />} />
            </div>
            <div>
              <Label>Gender</Label>
              <select
                className="input-field"
                value={form.gender}
                onChange={(e) => setForm({ ...form, gender: e.target.value })}
                style={{ height: 40, background: 'var(--surface-2, #1a2235)', border: '1px solid var(--clr-border)', color: 'var(--clr-text)', borderRadius: 8, paddingLeft: 12 }}
              >
                {GENDER_OPTIONS.map((g) => <option key={g} value={g}>{g.charAt(0) + g.slice(1).toLowerCase()}</option>)}
              </select>
            </div>
            <div>
              <Label>Blood Group</Label>
              <select
                className="input-field"
                value={form.bloodGroup}
                onChange={(e) => setForm({ ...form, bloodGroup: e.target.value })}
                style={{ height: 40, background: 'var(--surface-2, #1a2235)', border: '1px solid var(--clr-border)', color: 'var(--clr-text)', borderRadius: 8, paddingLeft: 12 }}
              >
                <option value="">Select blood group</option>
                {BLOOD_GROUPS.map((bg) => <option key={bg} value={bg}>{bg}</option>)}
              </select>
            </div>
            <div>
              <Label>Phone</Label>
              <Input placeholder="+1 555 000 0000" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} prefix={<Phone className="h-3.5 w-3.5" />} />
            </div>
            <div>
              <Label>Email</Label>
              <Input type="email" placeholder="patient@email.com" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} prefix={<Mail className="h-3.5 w-3.5" />} />
            </div>
            <div className="sm:col-span-2 lg:col-span-3 flex gap-3 pt-1">
              <Button type="submit" disabled={creating}>
                {creating ? <><Loader2 className="h-4 w-4 animate-spin" /> Creating…</> : 'Create Patient'}
              </Button>
              <Button type="button" variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
            </div>
          </form>
        </div>
      )}

      {/* ── Search bar ── */}
      <div className="flex items-center gap-3">
        <Input
          placeholder="Search by name or MRN…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          prefix={<Search className="h-4 w-4" />}
          className="max-w-md"
        />
        {search && (
          <button onClick={() => { setSearch(''); setPage(0); }} className="text-xs px-3 py-1.5 rounded-lg transition-colors"
            style={{ color: 'var(--clr-text-3)', background: 'var(--surface-2)' }}>
            Clear
          </button>
        )}
      </div>

      {/* ── Patient list ── */}
      {loading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="h-8 w-8 animate-spin" style={{ color: '#3b82f6' }} />
        </div>
      ) : patients?.content.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-2xl py-20"
          style={{ background: 'var(--surface, #111827)', border: '2px dashed var(--clr-border, #1e2d45)' }}>
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl mb-4" style={{ background: 'rgba(59,130,246,0.1)' }}>
            <User2 className="h-7 w-7" style={{ color: '#3b82f6' }} />
          </div>
          <p className="text-base font-semibold text-white mb-1">No patients found</p>
          <p className="text-sm" style={{ color: 'var(--clr-text-3)' }}>Add your first patient to get started</p>
        </div>
      ) : (
        <div className="rounded-2xl overflow-hidden" style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}>
          {/* Table header */}
          <div className="grid grid-cols-[40px_2fr_1fr_1fr_1fr_1fr] gap-4 px-4 py-3"
            style={{ background: 'var(--surface-2, #1a2235)', borderBottom: '1px solid var(--clr-border)' }}>
            {['', 'Patient', 'MRN', 'Gender', 'DOB', 'Contact'].map((col) => (
              <span key={col} className="text-[11px] font-bold uppercase tracking-wider" style={{ color: 'var(--clr-text-3)' }}>{col}</span>
            ))}
          </div>

          {/* Rows */}
          {patients?.content.map((patient, i) => (
            <div
              key={patient.id}
              className="grid grid-cols-[40px_2fr_1fr_1fr_1fr_1fr] gap-4 px-4 py-3.5 items-center transition-colors cursor-pointer"
              style={{ borderBottom: i < (patients.content.length - 1) ? '1px solid var(--clr-border, #1e2d45)' : 'none' }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.02)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              {/* Avatar */}
              <div className="flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold"
                style={{
                  background: `${genderColor[patient.gender] ?? '#3b82f6'}20`,
                  color: genderColor[patient.gender] ?? '#3b82f6',
                }}>
                {patient.firstName[0]}{patient.lastName[0]}
              </div>
              {/* Name */}
              <div>
                <p className="text-sm font-semibold text-white">{patient.fullName}</p>
                {patient.email && <p className="text-xs mt-0.5" style={{ color: 'var(--clr-text-3)' }}>{patient.email}</p>}
              </div>
              {/* MRN */}
              <span className="badge badge-slate font-mono text-[10px]">{patient.medicalRecordNumber}</span>
              {/* Gender */}
              <span className="text-xs font-medium" style={{ color: genderColor[patient.gender] ?? '#94a3b8' }}>
                {patient.gender.charAt(0) + patient.gender.slice(1).toLowerCase()}
              </span>
              {/* DOB */}
              <span className="text-xs flex items-center gap-1" style={{ color: 'var(--clr-text-2)' }}>
                <Calendar className="h-3 w-3" />
                {patient.dateOfBirth}
              </span>
              {/* Contact */}
              <div className="text-xs" style={{ color: 'var(--clr-text-2)' }}>
                {patient.phone && (
                  <p className="flex items-center gap-1"><Phone className="h-3 w-3" /> {patient.phone}</p>
                )}
                {patient.bloodGroup && (
                  <p className="flex items-center gap-1 mt-0.5" style={{ color: '#f87171' }}>
                    <Droplets className="h-3 w-3" /> {patient.bloodGroup}
                  </p>
                )}
              </div>
            </div>
          ))}

          {/* Pagination */}
          {patients && patients.totalPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3"
              style={{ borderTop: '1px solid var(--clr-border, #1e2d45)', background: 'var(--surface-2, #1a2235)' }}>
              <p className="text-xs" style={{ color: 'var(--clr-text-3)' }}>
                Page <strong className="text-white">{page + 1}</strong> of <strong className="text-white">{patients.totalPages}</strong>
                {' '}· {patients.totalElements} total
              </p>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button variant="secondary" size="sm" disabled={patients.last} onClick={() => setPage(page + 1)}>
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
