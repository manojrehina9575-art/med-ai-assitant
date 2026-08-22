import { useState, useEffect, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { patientService } from '@/services/patientService';
import { fileService } from '@/services/fileService';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
import {
  Upload, FileImage, Loader2, CheckCircle2, XCircle,
  X, FileText, Microscope, Scan, Stethoscope, FileWarning,
} from 'lucide-react';
import type { Patient, FileType, MedicalFile } from '@/types';

const FILE_TYPES: { value: FileType; label: string; icon: React.ElementType; color: string }[] = [
  { value: 'XRAY',             label: 'X-Ray',             icon: Scan,         color: '#06b6d4' },
  { value: 'CT_SCAN',          label: 'CT Scan',           icon: Scan,         color: '#8b5cf6' },
  { value: 'MRI',              label: 'MRI',               icon: Scan,         color: '#ec4899' },
  { value: 'ULTRASOUND',       label: 'Ultrasound',        icon: Stethoscope,  color: '#3b82f6' },
  { value: 'BLOOD_REPORT',     label: 'Blood Report',      icon: Microscope,   color: '#ef4444' },
  { value: 'LAB_REPORT',       label: 'Lab Report',        icon: FileText,     color: '#f59e0b' },
  { value: 'PRESCRIPTION',     label: 'Prescription',      icon: FileText,     color: '#10b981' },
  { value: 'DISCHARGE_SUMMARY',label: 'Discharge Summary', icon: FileWarning,  color: '#f97316' },
  { value: 'OTHER',            label: 'Other',             icon: FileImage,    color: '#64748b' },
];

const statusBadge: Record<string, string> = {
  COMPLETED:  'badge-green',
  PROCESSING: 'badge-amber',
  FAILED:     'badge-red',
  UPLOADED:   'badge-blue',
  UPLOADING:  'badge-blue',
};

const selectStyle: React.CSSProperties = {
  height: 40, width: '100%',
  background: 'var(--surface-2, #1a2235)',
  border: '1px solid var(--clr-border, #1e2d45)',
  color: 'var(--clr-text, #f1f5f9)',
  borderRadius: 8,
  paddingLeft: 12,
  fontSize: 14,
  outline: 'none',
};

export function UploadPage() {
  const [patients, setPatients]     = useState<Patient[]>([]);
  const [selectedPatient, setSP]    = useState('');
  const [fileType, setFileType]     = useState<FileType>('XRAY');
  const [description, setDesc]      = useState('');
  const [selectedFile, setSF]       = useState<File | null>(null);
  const [uploading, setUploading]   = useState(false);
  const [uploadResult, setResult]   = useState<{ success: boolean; error?: string } | null>(null);
  const [recentFiles, setRecent]    = useState<MedicalFile[]>([]);

  useEffect(() => {
    patientService.list(0, 100).then((r) => setPatients(r.content)).catch(() => {});
  }, []);

  const loadRecent = useCallback(async () => {
    if (!selectedPatient) return;
    try { const r = await fileService.list(selectedPatient, 0, 10); setRecent(r.content); }
    catch { /* ignore */ }
  }, [selectedPatient]);

  useEffect(() => { loadRecent(); }, [loadRecent]);

  const onDrop = useCallback((files: File[]) => {
    if (files.length > 0) { setSF(files[0]); setResult(null); }
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop, maxFiles: 1, maxSize: 100 * 1024 * 1024,
    accept: {
      'image/*': ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.tiff', '.dcm'],
      'application/pdf': ['.pdf'],
      'application/dicom': ['.dcm'],
    },
  });

  const handleUpload = async () => {
    if (!selectedFile || !selectedPatient) return;
    setUploading(true); setResult(null);
    try {
      await fileService.upload(selectedPatient, selectedFile, fileType, description || undefined);
      setResult({ success: true }); setSF(null); setDesc(''); loadRecent();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setResult({ success: false, error: e.response?.data?.message || 'Upload failed' });
    } finally { setUploading(false); }
  };

  const fmt = (b: number) => {
    if (b < 1024) return b + ' B';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
    return (b / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const selectedFT = FILE_TYPES.find((f) => f.value === fileType);

  return (
    <div className="space-y-6 max-w-[1100px]">
      <div>
        <h1 className="text-2xl font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Upload Studies</h1>
        <p className="text-sm mt-0.5" style={{ color: 'var(--clr-text-3)' }}>Upload medical files for AI-assisted analysis</p>
      </div>

      <div className="grid lg:grid-cols-5 gap-5">
        {/* ── Upload form — 3 cols ── */}
        <div className="lg:col-span-3 rounded-2xl p-6 space-y-5"
          style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}>
          <h3 className="text-sm font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Upload Configuration</h3>

          {/* Patient select */}
          <div>
            <Label>Select Patient</Label>
            <select style={selectStyle} value={selectedPatient} onChange={(e) => setSP(e.target.value)} required>
              <option value="">Choose patient…</option>
              {patients.map((p) => (
                <option key={p.id} value={p.id}>{p.fullName} — {p.medicalRecordNumber}</option>
              ))}
            </select>
          </div>

          {/* File type chips */}
          <div>
            <Label>File Type</Label>
            <div className="grid grid-cols-3 gap-2 mt-1">
              {FILE_TYPES.map(({ value, label, icon: Icon, color }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setFileType(value)}
                  className="flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold transition-all"
                  style={{
                    background: fileType === value ? `${color}20` : 'var(--surface-2, #1a2235)',
                    border: `1px solid ${fileType === value ? color + '60' : 'var(--clr-border, #1e2d45)'}`,
                    color: fileType === value ? color : 'var(--clr-text-2, #94a3b8)',
                  }}
                >
                  <Icon className="h-3.5 w-3.5 shrink-0" />
                  {label}
                </button>
              ))}
            </div>
          </div>

          {/* Description */}
          <div>
            <Label>Clinical Notes <span className="opacity-50 font-normal">(optional)</span></Label>
            <textarea
              className="w-full rounded-lg px-3 py-2 text-sm resize-none outline-none transition-all"
              style={{
                background: 'var(--surface-2, #1a2235)',
                border: '1px solid var(--clr-border, #1e2d45)',
                color: 'var(--clr-text, #f1f5f9)',
                minHeight: 72,
              }}
              placeholder="Clinical notes, context, or relevant findings…"
              value={description}
              onChange={(e) => setDesc(e.target.value)}
              onFocus={(e) => { e.currentTarget.style.borderColor = '#3b82f6'; e.currentTarget.style.boxShadow = '0 0 0 3px rgba(59,130,246,0.15)'; }}
              onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--clr-border, #1e2d45)'; e.currentTarget.style.boxShadow = 'none'; }}
            />
          </div>

          {/* Drop zone */}
          <div
            {...getRootProps()}
            className="relative flex cursor-pointer flex-col items-center justify-center rounded-xl p-8 text-center transition-all"
            style={{
              background: isDragActive ? 'rgba(59,130,246,0.08)' : selectedFile ? 'rgba(16,185,129,0.05)' : 'var(--surface-2, #1a2235)',
              border: `2px dashed ${isDragActive ? '#3b82f6' : selectedFile ? '#10b981' : 'var(--clr-border-2, #243250)'}`,
            }}
          >
            <input {...getInputProps()} />
            {selectedFile ? (
              <>
                <div className="flex h-12 w-12 items-center justify-center rounded-xl mb-3"
                  style={{ background: `${selectedFT?.color ?? '#10b981'}20` }}>
                  {selectedFT ? <selectedFT.icon className="h-6 w-6" style={{ color: selectedFT.color }} /> : <FileImage className="h-6 w-6 text-emerald-400" />}
                </div>
                <p className="font-semibold text-white text-sm">{selectedFile.name}</p>
                <p className="text-xs mt-1" style={{ color: 'var(--clr-text-3)' }}>{fmt(selectedFile.size)}</p>
                <button
                  type="button"
                  className="mt-3 text-xs flex items-center gap-1 hover:text-red-400 transition-colors"
                  style={{ color: 'var(--clr-text-3)' }}
                  onClick={(e) => { e.stopPropagation(); setSF(null); }}
                >
                  <X className="h-3 w-3" /> Remove
                </button>
              </>
            ) : (
              <>
                <div className="flex h-12 w-12 items-center justify-center rounded-xl mb-3"
                  style={{ background: 'rgba(59,130,246,0.1)' }}>
                  <Upload className="h-6 w-6" style={{ color: '#3b82f6' }} />
                </div>
                <p className="font-semibold text-white text-sm">
                  {isDragActive ? 'Drop the file here…' : 'Drag & drop or click to select'}
                </p>
                <p className="text-xs mt-1" style={{ color: 'var(--clr-text-3)' }}>
                  DICOM, JPEG, PNG, PDF · Max 100 MB
                </p>
              </>
            )}
          </div>

          {/* Result feedback */}
          {uploadResult && (
            <div className={`flex items-center gap-3 rounded-xl px-4 py-3 text-sm ${uploadResult.success ? '' : ''}`}
              style={{
                background: uploadResult.success ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
                border: `1px solid ${uploadResult.success ? 'rgba(16,185,129,0.3)' : 'rgba(239,68,68,0.3)'}`,
                color: uploadResult.success ? '#34d399' : '#fca5a5',
              }}>
              {uploadResult.success
                ? <CheckCircle2 className="h-4 w-4 shrink-0" />
                : <XCircle className="h-4 w-4 shrink-0" style={{ color: '#ef4444' }} />}
              {uploadResult.success ? 'File uploaded successfully!' : uploadResult.error}
            </div>
          )}

          <Button
            size="lg"
            className="w-full"
            onClick={handleUpload}
            disabled={!selectedFile || !selectedPatient || uploading}
          >
            {uploading
              ? <><Loader2 className="h-4 w-4 animate-spin" /> Uploading…</>
              : <><Upload className="h-4 w-4" /> Upload File</>}
          </Button>
        </div>

        {/* ── Recent uploads — 2 cols ── */}
        <div className="lg:col-span-2 rounded-2xl p-5"
          style={{ background: 'var(--surface, #111827)', border: '1px solid var(--clr-border, #1e2d45)' }}>
          <h3 className="text-sm font-bold text-white mb-4" style={{ fontFamily: 'Plus Jakarta Sans' }}>Recent Uploads</h3>

          {!selectedPatient ? (
            <div className="flex flex-col items-center justify-center py-12">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl mb-3"
                style={{ background: 'rgba(100,116,139,0.1)' }}>
                <FileImage className="h-5 w-5" style={{ color: 'var(--clr-text-3)' }} />
              </div>
              <p className="text-xs text-center" style={{ color: 'var(--clr-text-3)' }}>
                Select a patient above<br />to see their uploaded files
              </p>
            </div>
          ) : recentFiles.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl mb-3"
                style={{ background: 'rgba(100,116,139,0.1)' }}>
                <Upload className="h-5 w-5" style={{ color: 'var(--clr-text-3)' }} />
              </div>
              <p className="text-xs" style={{ color: 'var(--clr-text-3)' }}>No files uploaded yet</p>
            </div>
          ) : (
            <div className="space-y-2">
              {recentFiles.map((f) => {
                const ft = FILE_TYPES.find((t) => t.value === f.fileType);
                return (
                  <div key={f.id} className="flex items-start gap-3 rounded-xl p-3 transition-colors"
                    style={{ background: 'var(--surface-2, #1a2235)', border: '1px solid var(--clr-border, #1e2d45)' }}>
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
                      style={{ background: `${ft?.color ?? '#3b82f6'}18` }}>
                      {ft ? <ft.icon className="h-4 w-4" style={{ color: ft.color }} /> : <FileImage className="h-4 w-4 text-blue-400" />}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-semibold text-white truncate">{f.originalFileName}</p>
                      <p className="text-[11px] mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
                        {f.fileType.replace(/_/g, ' ')} · {fmt(f.fileSizeBytes)}
                      </p>
                      <p className="text-[10px] mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
                        {new Date(f.createdAt).toLocaleDateString()}
                      </p>
                    </div>
                    <span className={`badge ${statusBadge[f.uploadStatus] ?? 'badge-slate'} text-[10px] shrink-0`}>
                      {f.uploadStatus}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
