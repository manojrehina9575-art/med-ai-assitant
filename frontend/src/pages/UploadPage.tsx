import { useState, useEffect, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { patientService } from '@/services/patientService';
import { fileService } from '@/services/fileService';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Upload, FileImage, Loader2, CheckCircle2, XCircle } from 'lucide-react';
import type { Patient, FileType, MedicalFile } from '@/types';

const FILE_TYPES: { value: FileType; label: string }[] = [
  { value: 'XRAY', label: 'X-Ray' },
  { value: 'CT_SCAN', label: 'CT Scan' },
  { value: 'ULTRASOUND', label: 'Ultrasound' },
  { value: 'MRI', label: 'MRI' },
  { value: 'BLOOD_REPORT', label: 'Blood Report' },
  { value: 'LAB_REPORT', label: 'Lab Report' },
  { value: 'PRESCRIPTION', label: 'Prescription' },
  { value: 'DISCHARGE_SUMMARY', label: 'Discharge Summary' },
  { value: 'OTHER', label: 'Other' },
];

export function UploadPage() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = useState('');
  const [fileType, setFileType] = useState<FileType>('XRAY');
  const [description, setDescription] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState<{ success: boolean; file?: MedicalFile; error?: string } | null>(null);
  const [recentFiles, setRecentFiles] = useState<MedicalFile[]>([]);

  useEffect(() => {
    patientService.list(0, 100).then((res) => setPatients(res.content)).catch(() => {});
  }, []);

  const loadRecentFiles = useCallback(async () => {
    if (!selectedPatient) return;
    try {
      const res = await fileService.list(selectedPatient, 0, 10);
      setRecentFiles(res.content);
    } catch {
      // ignore
    }
  }, [selectedPatient]);

  useEffect(() => {
    loadRecentFiles();
  }, [loadRecentFiles]);

  const onDrop = useCallback((acceptedFiles: File[]) => {
    if (acceptedFiles.length > 0) {
      setSelectedFile(acceptedFiles[0]);
      setUploadResult(null);
    }
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    maxFiles: 1,
    maxSize: 100 * 1024 * 1024,
    accept: {
      'image/*': ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.tiff', '.dcm'],
      'application/pdf': ['.pdf'],
      'application/dicom': ['.dcm'],
    },
  });

  const handleUpload = async () => {
    if (!selectedFile || !selectedPatient) return;
    setUploading(true);
    setUploadResult(null);
    try {
      const file = await fileService.upload(selectedPatient, selectedFile, fileType, description || undefined);
      setUploadResult({ success: true, file });
      setSelectedFile(null);
      setDescription('');
      loadRecentFiles();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setUploadResult({ success: false, error: error.response?.data?.message || 'Upload failed' });
    } finally {
      setUploading(false);
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Upload Medical Files</h1>
        <p className="text-muted-foreground">Upload X-rays, CT scans, blood reports, and other medical files</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Upload File</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Patient</Label>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={selectedPatient}
                onChange={(e) => setSelectedPatient(e.target.value)}
                required
              >
                <option value="">Select patient...</option>
                {patients.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fullName} (MRN: {p.medicalRecordNumber})
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-2">
              <Label>File Type</Label>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={fileType}
                onChange={(e) => setFileType(e.target.value as FileType)}
              >
                {FILE_TYPES.map((ft) => (
                  <option key={ft.value} value={ft.value}>{ft.label}</option>
                ))}
              </select>
            </div>

            <div className="space-y-2">
              <Label>Description (optional)</Label>
              <textarea
                className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                placeholder="Clinical notes or description..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>

            <div
              {...getRootProps()}
              className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 transition-colors ${
                isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/25 hover:border-primary/50'
              }`}
            >
              <input {...getInputProps()} />
              <Upload className="mb-3 h-10 w-10 text-muted-foreground" />
              {selectedFile ? (
                <div className="text-center">
                  <p className="font-medium">{selectedFile.name}</p>
                  <p className="text-sm text-muted-foreground">{formatSize(selectedFile.size)}</p>
                </div>
              ) : (
                <div className="text-center">
                  <p className="font-medium">Drag & drop or click to select</p>
                  <p className="text-sm text-muted-foreground">Images (JPEG, PNG, DICOM) or PDF up to 100MB</p>
                </div>
              )}
            </div>

            {uploadResult && (
              <div className={`flex items-center gap-2 rounded-md p-3 text-sm ${
                uploadResult.success ? 'bg-green-50 text-green-700' : 'bg-destructive/10 text-destructive'
              }`}>
                {uploadResult.success ? <CheckCircle2 className="h-4 w-4" /> : <XCircle className="h-4 w-4" />}
                {uploadResult.success ? 'File uploaded successfully!' : uploadResult.error}
              </div>
            )}

            <Button
              className="w-full"
              onClick={handleUpload}
              disabled={!selectedFile || !selectedPatient || uploading}
            >
              {uploading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Upload File
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Recent Uploads</CardTitle>
          </CardHeader>
          <CardContent>
            {!selectedPatient ? (
              <p className="py-8 text-center text-sm text-muted-foreground">Select a patient to see their files</p>
            ) : recentFiles.length === 0 ? (
              <div className="flex flex-col items-center py-8">
                <FileImage className="mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No files uploaded yet</p>
              </div>
            ) : (
              <div className="space-y-3">
                {recentFiles.map((f) => (
                  <div key={f.id} className="flex items-center gap-3 rounded-lg border p-3">
                    <FileImage className="h-8 w-8 text-primary" />
                    <div className="flex-1 overflow-hidden">
                      <p className="truncate text-sm font-medium">{f.originalFileName}</p>
                      <p className="text-xs text-muted-foreground">
                        {f.fileType.replace('_', ' ')} &middot; {formatSize(f.fileSizeBytes)} &middot; {new Date(f.createdAt).toLocaleDateString()}
                      </p>
                    </div>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      f.uploadStatus === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                      f.uploadStatus === 'PROCESSING' ? 'bg-yellow-100 text-yellow-700' :
                      f.uploadStatus === 'FAILED' ? 'bg-red-100 text-red-700' :
                      'bg-blue-100 text-blue-700'
                    }`}>
                      {f.uploadStatus}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
