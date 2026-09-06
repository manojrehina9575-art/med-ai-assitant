import { useEffect, useState, useCallback } from 'react';
import { patientService } from '@/services/patientService';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import {
  Plus, Search, ChevronLeft, ChevronRight, User2, Loader2,
  X, AlertCircle, Phone, Mail, Droplets, Calendar, Hash,
  Edit2, Trash2, CheckCircle2,
  Power, MapPin, ShieldCheck,
} from 'lucide-react';
import type { Patient, PagedResponse, Gender } from '@/types';

const GENDER_OPTIONS: Gender[] = ['MALE', 'FEMALE', 'OTHER'];
const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

const genderColor: Record<string, string> = {
  MALE: '#3b82f6', FEMALE: '#ec4899', OTHER: '#8b5cf6',
};

type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

export function PatientsPage() {
  const [patients, setPatients] = useState<PagedResponse<Patient> | null>(null);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  // Create state
  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState({
    medicalRecordNumber: '',
    firstName: '',
    lastName: '',
    dateOfBirth: '',
    gender: 'MALE' as Gender,
    bloodGroup: '',
    phone: '',
    email: '',
    address: '',
    emergencyContactName: '',
    emergencyContactPhone: '',
    medicalHistory: '',
    allergies: '',
  });
  const [createError, setCreateError] = useState('');
  const [creating, setCreating] = useState(false);

  // Edit state
  const [editingPatient, setEditingPatient] = useState<Patient | null>(null);
  const [editForm, setEditForm] = useState({
    medicalRecordNumber: '',
    firstName: '',
    lastName: '',
    dateOfBirth: '',
    gender: 'MALE' as Gender,
    bloodGroup: '',
    phone: '',
    email: '',
    address: '',
    emergencyContactName: '',
    emergencyContactPhone: '',
    medicalHistory: '',
    allergies: '',
    isActive: true,
  });
  const [editError, setEditError] = useState('');
  const [saving, setSaving] = useState(false);

  // Delete/Deactivate state
  const [deletingPatient, setDeletingPatient] = useState<Patient | null>(null);
  const [permanentDelete, setPermanentDelete] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);

  const loadPatients = useCallback(async () => {
    setLoading(true);
    try {
      const activeParam = statusFilter === 'ACTIVE' ? true : statusFilter === 'INACTIVE' ? false : undefined;
      const data = await patientService.list(page, 20, search || undefined, activeParam);
      setPatients(data);
    } catch {
      /* handle error */
    } finally {
      setLoading(false);
    }
  }, [page, search, statusFilter]);

  useEffect(() => {
    loadPatients();
  }, [loadPatients]);

  const showNotification = (msg: string) => {
    setActionSuccess(msg);
    setTimeout(() => setActionSuccess(null), 4000);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError('');
    setCreating(true);
    try {
      const medHistory = createForm.medicalHistory
        ? createForm.medicalHistory.split(',').map((s) => s.trim()).filter(Boolean)
        : [];
      const allergiesList = createForm.allergies
        ? createForm.allergies.split(',').map((s) => s.trim()).filter(Boolean)
        : [];

      await patientService.create({
        medicalRecordNumber: createForm.medicalRecordNumber,
        firstName: createForm.firstName,
        lastName: createForm.lastName,
        dateOfBirth: createForm.dateOfBirth,
        gender: createForm.gender,
        bloodGroup: createForm.bloodGroup || undefined,
        phone: createForm.phone || undefined,
        email: createForm.email || undefined,
        address: createForm.address || undefined,
        emergencyContactName: createForm.emergencyContactName || undefined,
        emergencyContactPhone: createForm.emergencyContactPhone || undefined,
        medicalHistory: medHistory.length ? medHistory : undefined,
        allergies: allergiesList.length ? allergiesList : undefined,
      });

      setShowCreate(false);
      setCreateForm({
        medicalRecordNumber: '', firstName: '', lastName: '', dateOfBirth: '',
        gender: 'MALE', bloodGroup: '', phone: '', email: '', address: '',
        emergencyContactName: '', emergencyContactPhone: '', medicalHistory: '', allergies: '',
      });
      showNotification('Patient record created successfully');
      loadPatients();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setCreateError(error.response?.data?.message || 'Failed to create patient');
    } finally {
      setCreating(false);
    }
  };

  const openEditModal = (patient: Patient) => {
    setEditingPatient(patient);
    setEditError('');
    setEditForm({
      medicalRecordNumber: patient.medicalRecordNumber || '',
      firstName: patient.firstName || '',
      lastName: patient.lastName || '',
      dateOfBirth: patient.dateOfBirth || '',
      gender: patient.gender || 'MALE',
      bloodGroup: patient.bloodGroup || '',
      phone: patient.phone || '',
      email: patient.email || '',
      address: patient.address || '',
      emergencyContactName: patient.emergencyContactName || '',
      emergencyContactPhone: patient.emergencyContactPhone || '',
      medicalHistory: (patient.medicalHistory || []).join(', '),
      allergies: (patient.allergies || []).join(', '),
      isActive: patient.isActive ?? true,
    });
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingPatient) return;
    setEditError('');
    setSaving(true);
    try {
      const medHistory = editForm.medicalHistory
        ? editForm.medicalHistory.split(',').map((s) => s.trim()).filter(Boolean)
        : [];
      const allergiesList = editForm.allergies
        ? editForm.allergies.split(',').map((s) => s.trim()).filter(Boolean)
        : [];

      await patientService.update(editingPatient.id, {
        medicalRecordNumber: editForm.medicalRecordNumber,
        firstName: editForm.firstName,
        lastName: editForm.lastName,
        dateOfBirth: editForm.dateOfBirth,
        gender: editForm.gender,
        bloodGroup: editForm.bloodGroup || undefined,
        phone: editForm.phone || undefined,
        email: editForm.email || undefined,
        address: editForm.address || undefined,
        emergencyContactName: editForm.emergencyContactName || undefined,
        emergencyContactPhone: editForm.emergencyContactPhone || undefined,
        medicalHistory: medHistory,
        allergies: allergiesList,
        isActive: editForm.isActive,
      });

      setEditingPatient(null);
      showNotification(`Updated patient record for ${editForm.firstName} ${editForm.lastName}`);
      loadPatients();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setEditError(error.response?.data?.message || 'Failed to update patient');
    } finally {
      setSaving(false);
    }
  };

  const openDeleteModal = (patient: Patient) => {
    setDeletingPatient(patient);
    setPermanentDelete(false);
    setDeleteError('');
  };

  const handleDelete = async () => {
    if (!deletingPatient) return;
    setDeleteError('');
    setDeleting(true);
    try {
      await patientService.delete(deletingPatient.id, permanentDelete);
      showNotification(
        permanentDelete
          ? `Patient ${deletingPatient.fullName} permanently deleted`
          : `Patient ${deletingPatient.fullName} deactivated`
      );
      setDeletingPatient(null);
      loadPatients();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setDeleteError(error.response?.data?.message || 'Failed to delete or deactivate patient');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-[1200px]">
      {/* ── Success Toast ── */}
      {actionSuccess && (
        <div
          className="flex items-center gap-3 rounded-xl p-4 text-sm font-medium animate-in transition-all"
          style={{
            background: 'rgba(16, 185, 129, 0.12)',
            border: '1px solid rgba(16, 185, 129, 0.3)',
            color: '#6ee7b7',
          }}
        >
          <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-400" />
          <span>{actionSuccess}</span>
        </div>
      )}

      {/* ── Header ── */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>
            Patient Registry
          </h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
            {patients?.totalElements ?? 0} total patient records
          </p>
        </div>
        <Button onClick={() => setShowCreate(!showCreate)} size="sm">
          {showCreate ? (
            <>
              <X className="h-4 w-4 mr-1.5" /> Cancel
            </>
          ) : (
            <>
              <Plus className="h-4 w-4 mr-1.5" /> Add Patient
            </>
          )}
        </Button>
      </div>

      {/* ── Create Patient Panel ── */}
      {showCreate && (
        <div
          className="rounded-2xl p-6 animate-in shadow-xl"
          style={{
            background: 'var(--surface, #111827)',
            border: '1px solid var(--clr-border, #1e2d45)',
          }}
        >
          <div className="flex items-center justify-between mb-5">
            <h3 className="text-sm font-bold text-white flex items-center gap-2" style={{ fontFamily: 'Plus Jakarta Sans' }}>
              <Plus className="h-4 w-4 text-blue-400" /> New Patient Record
            </h3>
            <button
              onClick={() => setShowCreate(false)}
              className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800/60"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {createError && (
            <div
              className="flex items-start gap-3 rounded-xl p-3.5 mb-4 text-sm"
              style={{
                background: 'rgba(239,68,68,0.08)',
                border: '1px solid rgba(239,68,68,0.25)',
                color: '#fca5a5',
              }}
            >
              <AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-red-500" />
              {createError}
            </div>
          )}

          <form onSubmit={handleCreate} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <Label>Medical Record No. (MRN)</Label>
              <Input
                placeholder="e.g. MRN-1002"
                value={createForm.medicalRecordNumber}
                onChange={(e) => setCreateForm({ ...createForm, medicalRecordNumber: e.target.value })}
                required
                prefix={<Hash className="h-3.5 w-3.5" />}
              />
            </div>
            <div>
              <Label>First Name</Label>
              <Input
                placeholder="Jane"
                value={createForm.firstName}
                onChange={(e) => setCreateForm({ ...createForm, firstName: e.target.value })}
                required
                prefix={<User2 className="h-3.5 w-3.5" />}
              />
            </div>
            <div>
              <Label>Last Name</Label>
              <Input
                placeholder="Smith"
                value={createForm.lastName}
                onChange={(e) => setCreateForm({ ...createForm, lastName: e.target.value })}
                required
              />
            </div>
            <div>
              <Label>Date of Birth</Label>
              <Input
                type="date"
                value={createForm.dateOfBirth}
                onChange={(e) => setCreateForm({ ...createForm, dateOfBirth: e.target.value })}
                required
                prefix={<Calendar className="h-3.5 w-3.5" />}
              />
            </div>
            <div>
              <Label>Gender</Label>
              <select
                className="input-field w-full"
                value={createForm.gender}
                onChange={(e) => setCreateForm({ ...createForm, gender: e.target.value as Gender })}
                style={{
                  height: 40,
                  background: 'var(--surface-2, #1a2235)',
                  border: '1px solid var(--clr-border)',
                  color: 'var(--clr-text)',
                  borderRadius: 8,
                  paddingLeft: 12,
                }}
              >
                {GENDER_OPTIONS.map((g) => (
                  <option key={g} value={g}>
                    {g.charAt(0) + g.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label>Blood Group</Label>
              <select
                className="input-field w-full"
                value={createForm.bloodGroup}
                onChange={(e) => setCreateForm({ ...createForm, bloodGroup: e.target.value })}
                style={{
                  height: 40,
                  background: 'var(--surface-2, #1a2235)',
                  border: '1px solid var(--clr-border)',
                  color: 'var(--clr-text)',
                  borderRadius: 8,
                  paddingLeft: 12,
                }}
              >
                <option value="">Select blood group</option>
                {BLOOD_GROUPS.map((bg) => (
                  <option key={bg} value={bg}>
                    {bg}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label>Phone Number</Label>
              <Input
                placeholder="+1 555 000 0000"
                value={createForm.phone}
                onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })}
                prefix={<Phone className="h-3.5 w-3.5" />}
              />
            </div>
            <div>
              <Label>Email Address</Label>
              <Input
                type="email"
                placeholder="patient@email.com"
                value={createForm.email}
                onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
                prefix={<Mail className="h-3.5 w-3.5" />}
              />
            </div>
            <div>
              <Label>Residential Address</Label>
              <Input
                placeholder="Street, City, State"
                value={createForm.address}
                onChange={(e) => setCreateForm({ ...createForm, address: e.target.value })}
                prefix={<MapPin className="h-3.5 w-3.5" />}
              />
            </div>
            <div>
              <Label>Emergency Contact Name</Label>
              <Input
                placeholder="Next of Kin Name"
                value={createForm.emergencyContactName}
                onChange={(e) => setCreateForm({ ...createForm, emergencyContactName: e.target.value })}
              />
            </div>
            <div>
              <Label>Emergency Contact Phone</Label>
              <Input
                placeholder="+1 555 999 8888"
                value={createForm.emergencyContactPhone}
                onChange={(e) => setCreateForm({ ...createForm, emergencyContactPhone: e.target.value })}
              />
            </div>
            <div>
              <Label>Known Allergies (comma separated)</Label>
              <Input
                placeholder="Penicillin, Peanuts"
                value={createForm.allergies}
                onChange={(e) => setCreateForm({ ...createForm, allergies: e.target.value })}
              />
            </div>
            <div className="sm:col-span-2 lg:col-span-3">
              <Label>Medical History (comma separated conditions)</Label>
              <Input
                placeholder="Hypertension, Asthma, Type 2 Diabetes"
                value={createForm.medicalHistory}
                onChange={(e) => setCreateForm({ ...createForm, medicalHistory: e.target.value })}
              />
            </div>

            <div className="sm:col-span-2 lg:col-span-3 flex gap-3 pt-2">
              <Button type="submit" disabled={creating}>
                {creating ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin mr-1.5" /> Creating…
                  </>
                ) : (
                  'Create Patient'
                )}
              </Button>
              <Button type="button" variant="secondary" onClick={() => setShowCreate(false)}>
                Cancel
              </Button>
            </div>
          </form>
        </div>
      )}

      {/* ── Filters & Search ── */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        {/* Status Filter Tabs */}
        <div
          className="flex items-center rounded-xl p-1"
          style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}
        >
          {(
            [
              { id: 'ALL', label: 'All Patients' },
              { id: 'ACTIVE', label: 'Active' },
              { id: 'INACTIVE', label: 'Deactivated' },
            ] as const
          ).map((tab) => {
            const isSelected = statusFilter === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setStatusFilter(tab.id);
                  setPage(0);
                }}
                className="px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all"
                style={{
                  background: isSelected ? 'rgba(59, 130, 246, 0.15)' : 'transparent',
                  color: isSelected ? '#60a5fa' : 'var(--clr-text-3, #94a3b8)',
                  border: isSelected ? '1px solid rgba(59, 130, 246, 0.3)' : '1px solid transparent',
                }}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Search bar */}
        <div className="flex items-center gap-2 max-w-md w-full sm:w-auto">
          <Input
            placeholder="Search by name or MRN…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            prefix={<Search className="h-4 w-4 text-slate-400" />}
            className="w-full sm:w-80"
          />
          {search && (
            <button
              onClick={() => {
                setSearch('');
                setPage(0);
              }}
              className="text-xs px-2.5 py-2 rounded-lg transition-colors text-slate-400 hover:text-white"
              style={{ background: 'var(--surface-2, #1a2235)' }}
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* ── Patient List Table ── */}
      {loading ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
          <p className="text-xs text-slate-400">Loading patient records…</p>
        </div>
      ) : patients?.content.length === 0 ? (
        <div
          className="flex flex-col items-center justify-center rounded-2xl py-20"
          style={{
            background: 'var(--surface, #111827)',
            border: '2px dashed var(--clr-border, #1e2d45)',
          }}
        >
          <div
            className="flex h-14 w-14 items-center justify-center rounded-2xl mb-4"
            style={{ background: 'rgba(59,130,246,0.1)' }}
          >
            <User2 className="h-7 w-7 text-blue-400" />
          </div>
          <p className="text-base font-semibold text-white mb-1">No patients found</p>
          <p className="text-sm" style={{ color: 'var(--clr-text-3)' }}>
            {search || statusFilter !== 'ALL'
              ? 'Try adjusting your search query or status filter'
              : 'Add your first patient to get started'}
          </p>
        </div>
      ) : (
        <div
          className="rounded-2xl overflow-hidden shadow-lg"
          style={{
            background: 'var(--surface, #111827)',
            border: '1px solid var(--clr-border, #1e2d45)',
          }}
        >
          {/* Table header */}
          <div
            className="grid grid-cols-[40px_2.2fr_1.1fr_1fr_1fr_1.3fr_1fr_90px] gap-4 px-4 py-3"
            style={{
              background: 'var(--surface-2, #1a2235)',
              borderBottom: '1px solid var(--clr-border, #1e2d45)',
            }}
          >
            {['', 'Patient', 'MRN', 'Gender', 'DOB', 'Contact', 'Status', 'Actions'].map((col) => (
              <span
                key={col}
                className="text-[11px] font-bold uppercase tracking-wider"
                style={{ color: 'var(--clr-text-3, #94a3b8)' }}
              >
                {col}
              </span>
            ))}
          </div>

          {/* Rows */}
          {patients?.content.map((patient, i) => (
            <div
              key={patient.id}
              className="grid grid-cols-[40px_2.2fr_1.1fr_1fr_1fr_1.3fr_1fr_90px] gap-4 px-4 py-3.5 items-center transition-colors group"
              style={{
                borderBottom:
                  i < patients.content.length - 1 ? '1px solid var(--clr-border, #1e2d45)' : 'none',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.02)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              {/* Avatar */}
              <div
                className="flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold"
                style={{
                  background: `${genderColor[patient.gender] ?? '#3b82f6'}20`,
                  color: genderColor[patient.gender] ?? '#3b82f6',
                }}
              >
                {patient.firstName?.[0] || 'P'}
                {patient.lastName?.[0] || ''}
              </div>

              {/* Name & Contact */}
              <div className="min-w-0">
                <p className="text-sm font-semibold text-white truncate">{patient.fullName}</p>
                {patient.email ? (
                  <p className="text-xs truncate" style={{ color: 'var(--clr-text-3)' }}>
                    {patient.email}
                  </p>
                ) : (
                  <p className="text-xs text-slate-500 italic">No email</p>
                )}
              </div>

              {/* MRN */}
              <span className="badge badge-slate font-mono text-[10px] truncate">
                {patient.medicalRecordNumber}
              </span>

              {/* Gender */}
              <span className="text-xs font-medium" style={{ color: genderColor[patient.gender] ?? '#94a3b8' }}>
                {patient.gender ? patient.gender.charAt(0) + patient.gender.slice(1).toLowerCase() : '—'}
              </span>

              {/* DOB */}
              <span className="text-xs flex items-center gap-1" style={{ color: 'var(--clr-text-2, #cbd5e1)' }}>
                <Calendar className="h-3 w-3 shrink-0 text-slate-400" />
                {patient.dateOfBirth || '—'}
              </span>

              {/* Contact Info */}
              <div className="text-xs" style={{ color: 'var(--clr-text-2, #cbd5e1)' }}>
                {patient.phone && (
                  <p className="flex items-center gap-1 truncate">
                    <Phone className="h-3 w-3 shrink-0 text-slate-400" /> {patient.phone}
                  </p>
                )}
                {patient.bloodGroup && (
                  <p className="flex items-center gap-1 mt-0.5 text-rose-400 font-medium">
                    <Droplets className="h-3 w-3 shrink-0" /> {patient.bloodGroup}
                  </p>
                )}
                {!patient.phone && !patient.bloodGroup && <span className="text-slate-500">—</span>}
              </div>

              {/* Active / Inactive Status */}
              <div>
                {patient.isActive ? (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    <ShieldCheck className="w-3 h-3" /> Active
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-slate-500/10 text-slate-400 border border-slate-500/20">
                    <Power className="w-3 h-3" /> Deactivated
                  </span>
                )}
              </div>

              {/* Row Action Buttons */}
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => openEditModal(patient)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-blue-400 hover:bg-blue-500/10 transition-colors"
                  title="Edit Patient"
                >
                  <Edit2 className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => openDeleteModal(patient)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                  title="Deactivate or Delete Patient"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}

          {/* Pagination */}
          {patients && patients.totalPages > 1 && (
            <div
              className="flex items-center justify-between px-4 py-3"
              style={{
                borderTop: '1px solid var(--clr-border, #1e2d45)',
                background: 'var(--surface-2, #1a2235)',
              }}
            >
              <p className="text-xs" style={{ color: 'var(--clr-text-3)' }}>
                Page <strong className="text-white">{page + 1}</strong> of{' '}
                <strong className="text-white">{patients.totalPages}</strong> ·{' '}
                {patients.totalElements} total records
              </p>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={patients.last}
                  onClick={() => setPage(page + 1)}
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Edit Patient Modal ── */}
      {editingPatient && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in">
          <div
            className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl p-6 shadow-2xl space-y-5"
            style={{
              background: 'var(--surface, #111827)',
              border: '1px solid var(--clr-border, #1e2d45)',
            }}
          >
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <Edit2 className="h-4 w-4 text-blue-400" /> Edit Patient Record
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Updating record for <strong className="text-slate-200">{editingPatient.fullName}</strong> ({editingPatient.medicalRecordNumber})
                </p>
              </div>
              <button
                type="button"
                onClick={() => setEditingPatient(null)}
                className="text-slate-400 hover:text-white p-1.5 rounded-lg hover:bg-slate-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {editError && (
              <div
                className="flex items-start gap-3 rounded-xl p-3.5 text-sm"
                style={{
                  background: 'rgba(239,68,68,0.08)',
                  border: '1px solid rgba(239,68,68,0.25)',
                  color: '#fca5a5',
                }}
              >
                <AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-red-500" />
                {editError}
              </div>
            )}

            <form onSubmit={handleUpdate} className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <Label>Medical Record Number (MRN)</Label>
                  <Input
                    value={editForm.medicalRecordNumber}
                    onChange={(e) => setEditForm({ ...editForm, medicalRecordNumber: e.target.value })}
                    required
                    prefix={<Hash className="h-3.5 w-3.5" />}
                  />
                </div>
                <div>
                  <Label>Record Status</Label>
                  <div className="flex items-center gap-3 mt-2">
                    <label className="flex items-center gap-2 cursor-pointer text-sm text-slate-200">
                      <input
                        type="checkbox"
                        checked={editForm.isActive}
                        onChange={(e) => setEditForm({ ...editForm, isActive: e.target.checked })}
                        className="rounded border-slate-700 bg-slate-900 text-blue-500 focus:ring-blue-500 w-4 h-4"
                      />
                      <span>Active Patient in System</span>
                    </label>
                  </div>
                </div>
                <div>
                  <Label>First Name</Label>
                  <Input
                    value={editForm.firstName}
                    onChange={(e) => setEditForm({ ...editForm, firstName: e.target.value })}
                    required
                    prefix={<User2 className="h-3.5 w-3.5" />}
                  />
                </div>
                <div>
                  <Label>Last Name</Label>
                  <Input
                    value={editForm.lastName}
                    onChange={(e) => setEditForm({ ...editForm, lastName: e.target.value })}
                    required
                  />
                </div>
                <div>
                  <Label>Date of Birth</Label>
                  <Input
                    type="date"
                    value={editForm.dateOfBirth}
                    onChange={(e) => setEditForm({ ...editForm, dateOfBirth: e.target.value })}
                    required
                    prefix={<Calendar className="h-3.5 w-3.5" />}
                  />
                </div>
                <div>
                  <Label>Gender</Label>
                  <select
                    className="input-field w-full"
                    value={editForm.gender}
                    onChange={(e) => setEditForm({ ...editForm, gender: e.target.value as Gender })}
                    style={{
                      height: 40,
                      background: 'var(--surface-2, #1a2235)',
                      border: '1px solid var(--clr-border)',
                      color: 'var(--clr-text)',
                      borderRadius: 8,
                      paddingLeft: 12,
                    }}
                  >
                    {GENDER_OPTIONS.map((g) => (
                      <option key={g} value={g}>
                        {g.charAt(0) + g.slice(1).toLowerCase()}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label>Blood Group</Label>
                  <select
                    className="input-field w-full"
                    value={editForm.bloodGroup}
                    onChange={(e) => setEditForm({ ...editForm, bloodGroup: e.target.value })}
                    style={{
                      height: 40,
                      background: 'var(--surface-2, #1a2235)',
                      border: '1px solid var(--clr-border)',
                      color: 'var(--clr-text)',
                      borderRadius: 8,
                      paddingLeft: 12,
                    }}
                  >
                    <option value="">Select blood group</option>
                    {BLOOD_GROUPS.map((bg) => (
                      <option key={bg} value={bg}>
                        {bg}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label>Phone Number</Label>
                  <Input
                    placeholder="+1 555 000 0000"
                    value={editForm.phone}
                    onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                    prefix={<Phone className="h-3.5 w-3.5" />}
                  />
                </div>
                <div>
                  <Label>Email Address</Label>
                  <Input
                    type="email"
                    placeholder="patient@email.com"
                    value={editForm.email}
                    onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                    prefix={<Mail className="h-3.5 w-3.5" />}
                  />
                </div>
                <div>
                  <Label>Address</Label>
                  <Input
                    placeholder="Street, City, State"
                    value={editForm.address}
                    onChange={(e) => setEditForm({ ...editForm, address: e.target.value })}
                    prefix={<MapPin className="h-3.5 w-3.5" />}
                  />
                </div>
                <div>
                  <Label>Emergency Contact Name</Label>
                  <Input
                    placeholder="Next of Kin Name"
                    value={editForm.emergencyContactName}
                    onChange={(e) => setEditForm({ ...editForm, emergencyContactName: e.target.value })}
                  />
                </div>
                <div>
                  <Label>Emergency Contact Phone</Label>
                  <Input
                    placeholder="+1 555 999 8888"
                    value={editForm.emergencyContactPhone}
                    onChange={(e) => setEditForm({ ...editForm, emergencyContactPhone: e.target.value })}
                  />
                </div>
              </div>

              <div>
                <Label>Allergies (comma separated)</Label>
                <Input
                  placeholder="e.g. Penicillin, Peanuts, Latex"
                  value={editForm.allergies}
                  onChange={(e) => setEditForm({ ...editForm, allergies: e.target.value })}
                />
              </div>

              <div>
                <Label>Medical History (comma separated conditions)</Label>
                <Input
                  placeholder="e.g. Hypertension, Asthma, Type 2 Diabetes"
                  value={editForm.medicalHistory}
                  onChange={(e) => setEditForm({ ...editForm, medicalHistory: e.target.value })}
                />
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
                <Button type="button" variant="secondary" onClick={() => setEditingPatient(null)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={saving}>
                  {saving ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin mr-1.5" /> Saving Changes…
                    </>
                  ) : (
                    'Save Changes'
                  )}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Delete / Deactivate Confirmation Modal ── */}
      {deletingPatient && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in">
          <div
            className="w-full max-w-md rounded-2xl p-6 shadow-2xl space-y-4"
            style={{
              background: 'var(--surface, #111827)',
              border: '1px solid var(--clr-border, #1e2d45)',
            }}
          >
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-red-500/10 text-red-400 border border-red-500/20">
                <Trash2 className="h-5 w-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">Delete Patient Record</h3>
                <p className="text-xs text-slate-400">{deletingPatient.fullName} ({deletingPatient.medicalRecordNumber})</p>
              </div>
            </div>

            {deleteError && (
              <div
                className="flex items-start gap-2.5 rounded-xl p-3 text-xs"
                style={{
                  background: 'rgba(239,68,68,0.08)',
                  border: '1px solid rgba(239,68,68,0.25)',
                  color: '#fca5a5',
                }}
              >
                <AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-red-500" />
                <span>{deleteError}</span>
              </div>
            )}

            <div className="space-y-3 py-1">
              <p className="text-xs text-slate-300">
                Choose the deletion mode for this patient record:
              </p>

              {/* Option 1: Soft Deactivation */}
              <label
                className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
                  !permanentDelete
                    ? 'bg-blue-500/10 border-blue-500/40 text-white'
                    : 'bg-slate-900/40 border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
                onClick={() => setPermanentDelete(false)}
              >
                <input
                  type="radio"
                  name="deleteType"
                  checked={!permanentDelete}
                  onChange={() => setPermanentDelete(false)}
                  className="mt-1 text-blue-500 focus:ring-blue-500"
                />
                <div className="text-xs">
                  <span className="font-semibold block text-slate-100">
                    Deactivate (Recommended)
                  </span>
                  <span className="text-slate-400 text-[11px] leading-relaxed">
                    Hides the patient from active worklists while preserving all analysis, diagnostic reports, and audit trails.
                  </span>
                </div>
              </label>

              {/* Option 2: Permanent Deletion */}
              <label
                className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
                  permanentDelete
                    ? 'bg-red-500/10 border-red-500/40 text-white'
                    : 'bg-slate-900/40 border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
                onClick={() => setPermanentDelete(true)}
              >
                <input
                  type="radio"
                  name="deleteType"
                  checked={permanentDelete}
                  onChange={() => setPermanentDelete(true)}
                  className="mt-1 text-red-500 focus:ring-red-500"
                />
                <div className="text-xs">
                  <span className="font-semibold block text-red-400">
                    Permanent Delete
                  </span>
                  <span className="text-slate-400 text-[11px] leading-relaxed">
                    Completely removes the patient row from database. Will fail if dependent study reports exist.
                  </span>
                </div>
              </label>
            </div>

            <div className="flex justify-end gap-2.5 pt-3 border-t border-slate-800">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setDeletingPatient(null)}
                disabled={deleting}
              >
                Cancel
              </Button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className={`inline-flex items-center justify-center px-4 py-2 rounded-xl text-xs font-semibold text-white shadow-sm transition-colors ${
                  permanentDelete
                    ? 'bg-red-600 hover:bg-red-700 disabled:bg-red-800'
                    : 'bg-amber-600 hover:bg-amber-700 disabled:bg-amber-800'
                }`}
              >
                {deleting ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" /> Processing…
                  </>
                ) : permanentDelete ? (
                  'Permanently Delete'
                ) : (
                  'Deactivate Patient'
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
