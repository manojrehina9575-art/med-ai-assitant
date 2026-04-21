import { useState, useEffect, useCallback } from 'react';
import { patientService } from '@/services/patientService';
import { fileService } from '@/services/fileService';
import { analysisService, parseResult, type AnalysisResponse, type ImageAnalysisResult } from '@/services/analysisService';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import {
  Brain,
  Loader2,
  AlertTriangle,
  CheckCircle2,
  Clock,
  XCircle,
  RefreshCw,
  ChevronDown,
  ChevronUp,
  Activity,
  Zap,
} from 'lucide-react';
import type { Patient, MedicalFile } from '@/types';

const SEVERITY_COLORS: Record<string, string> = {
  NORMAL: 'bg-green-100 text-green-800',
  MILD: 'bg-yellow-100 text-yellow-800',
  MODERATE: 'bg-orange-100 text-orange-800',
  SEVERE: 'bg-red-100 text-red-800',
  CRITICAL: 'bg-red-200 text-red-900',
};

const URGENCY_COLORS: Record<string, string> = {
  ROUTINE: 'bg-green-100 text-green-700',
  URGENT: 'bg-orange-100 text-orange-700',
  CRITICAL: 'bg-red-100 text-red-700',
};

const STATUS_CONFIG: Record<string, { icon: typeof Clock; color: string; label: string }> = {
  PENDING: { icon: Clock, color: 'text-blue-500', label: 'Pending' },
  PROCESSING: { icon: Loader2, color: 'text-yellow-500', label: 'Processing' },
  COMPLETED: { icon: CheckCircle2, color: 'text-green-500', label: 'Completed' },
  FAILED: { icon: XCircle, color: 'text-red-500', label: 'Failed' },
};

export function AnalysisPage() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = useState('');
  const [files, setFiles] = useState<MedicalFile[]>([]);
  const [selectedFile, setSelectedFile] = useState('');
  const [clinicalNotes, setClinicalNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [analyses, setAnalyses] = useState<AnalysisResponse[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [polling, setPolling] = useState<string | null>(null);

  useEffect(() => {
    patientService.list(0, 100).then((res) => setPatients(res.content)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!selectedPatient) {
      setFiles([]);
      setSelectedFile('');
      return;
    }
    fileService.list(selectedPatient, 0, 100).then((res) => setFiles(res.content)).catch(() => {});
  }, [selectedPatient]);

  const loadAnalyses = useCallback(async () => {
    if (!selectedPatient) return;
    try {
      const res = await analysisService.listByPatient(selectedPatient);
      setAnalyses(res.content);
    } catch {
      // ignore
    }
  }, [selectedPatient]);

  useEffect(() => {
    loadAnalyses();
  }, [loadAnalyses]);

  // Poll for pending/processing analyses
  useEffect(() => {
    if (!polling) return;
    const interval = setInterval(async () => {
      try {
        const updated = await analysisService.get(polling);
        if (updated.status === 'COMPLETED' || updated.status === 'FAILED') {
          setPolling(null);
          loadAnalyses();
        }
      } catch {
        setPolling(null);
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [polling, loadAnalyses]);

  const handleSubmit = async () => {
    if (!selectedPatient || !selectedFile) return;
    setError('');
    setSubmitting(true);
    try {
      const result = await analysisService.requestImageAnalysis(selectedPatient, selectedFile, clinicalNotes || undefined);
      setPolling(result.id);
      setClinicalNotes('');
      loadAnalyses();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || 'Failed to submit analysis');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRetry = async (id: string) => {
    try {
      const result = await analysisService.retry(id);
      setPolling(result.id);
      loadAnalyses();
    } catch {
      // ignore
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">AI Image Analysis</h1>
        <p className="text-muted-foreground">
          Analyze medical images using GPT-4o Vision for structured diagnostic insights
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Request Form */}
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Brain className="h-5 w-5" />
              New Analysis
            </CardTitle>
            <CardDescription>Select an image and submit for AI analysis</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Patient</Label>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={selectedPatient}
                onChange={(e) => { setSelectedPatient(e.target.value); setSelectedFile(''); }}
              >
                <option value="">Select patient...</option>
                {patients.map((p) => (
                  <option key={p.id} value={p.id}>{p.fullName} ({p.medicalRecordNumber})</option>
                ))}
              </select>
            </div>

            <div className="space-y-2">
              <Label>Medical Image</Label>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={selectedFile}
                onChange={(e) => setSelectedFile(e.target.value)}
                disabled={!selectedPatient || files.length === 0}
              >
                <option value="">Select file...</option>
                {files.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.originalFileName} ({f.fileType.replace('_', ' ')})
                  </option>
                ))}
              </select>
              {selectedPatient && files.length === 0 && (
                <p className="text-xs text-muted-foreground">No files uploaded for this patient</p>
              )}
            </div>

            <div className="space-y-2">
              <Label>Clinical Notes (optional)</Label>
              <textarea
                className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                placeholder="Patient symptoms, history, suspected conditions..."
                value={clinicalNotes}
                onChange={(e) => setClinicalNotes(e.target.value)}
              />
            </div>

            {error && (
              <div className="flex items-center gap-2 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                <AlertTriangle className="h-4 w-4" />
                {error}
              </div>
            )}

            <Button
              className="w-full"
              onClick={handleSubmit}
              disabled={!selectedPatient || !selectedFile || submitting}
            >
              {submitting ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Zap className="mr-2 h-4 w-4" />
              )}
              Run AI Analysis
            </Button>

            {polling && (
              <div className="flex items-center gap-2 rounded-md bg-blue-50 p-3 text-sm text-blue-700">
                <Loader2 className="h-4 w-4 animate-spin" />
                Analysis in progress... auto-refreshing
              </div>
            )}
          </CardContent>
        </Card>

        {/* Results List */}
        <div className="space-y-4 lg:col-span-2">
          <h2 className="text-xl font-semibold">
            {selectedPatient ? 'Analysis History' : 'Select a patient to view analyses'}
          </h2>

          {!selectedPatient ? (
            <Card>
              <CardContent className="flex flex-col items-center py-12">
                <Activity className="mb-3 h-12 w-12 text-muted-foreground" />
                <p className="text-muted-foreground">Select a patient to see analysis results</p>
              </CardContent>
            </Card>
          ) : analyses.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center py-12">
                <Brain className="mb-3 h-12 w-12 text-muted-foreground" />
                <p className="font-medium">No analyses yet</p>
                <p className="text-sm text-muted-foreground">Submit a medical image for AI analysis</p>
              </CardContent>
            </Card>
          ) : (
            analyses.map((a) => {
              const statusCfg = STATUS_CONFIG[a.status] || STATUS_CONFIG.PENDING;
              const StatusIcon = statusCfg.icon;
              const isExpanded = expandedId === a.id;

              return (
                <Card key={a.id} className="overflow-hidden">
                  <div
                    className="flex cursor-pointer items-center gap-4 p-4 hover:bg-accent/30"
                    onClick={() => setExpandedId(isExpanded ? null : a.id)}
                  >
                    <StatusIcon className={`h-5 w-5 ${statusCfg.color} ${a.status === 'PROCESSING' ? 'animate-spin' : ''}`} />
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{a.analysisType.replace('_', ' ')}</span>
                        {a.urgency && (
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${URGENCY_COLORS[a.urgency] || ''}`}>
                            {a.urgency}
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {new Date(a.createdAt).toLocaleString()}
                        {a.modelUsed && ` \u00b7 ${a.modelUsed}`}
                        {a.totalTokens && ` \u00b7 ${a.totalTokens} tokens`}
                        {a.estimatedCost && ` \u00b7 $${a.estimatedCost.toFixed(4)}`}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                        a.status === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                        a.status === 'FAILED' ? 'bg-red-100 text-red-700' :
                        a.status === 'PROCESSING' ? 'bg-yellow-100 text-yellow-700' :
                        'bg-blue-100 text-blue-700'
                      }`}>
                        {statusCfg.label}
                      </span>
                      {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                    </div>
                  </div>

                  {isExpanded && (
                    <div className="border-t p-4">
                      {a.status === 'FAILED' && (
                        <div className="mb-4 flex items-center justify-between rounded-md bg-red-50 p-3">
                          <p className="text-sm text-red-700">{a.errorMessage || 'Analysis failed'}</p>
                          <Button size="sm" variant="outline" onClick={() => handleRetry(a.id)}>
                            <RefreshCw className="mr-1 h-3 w-3" />
                            Retry
                          </Button>
                        </div>
                      )}

                      {a.clinicalNotes && (
                        <div className="mb-4">
                          <h4 className="mb-1 text-sm font-semibold text-muted-foreground">Clinical Notes</h4>
                          <p className="text-sm">{a.clinicalNotes}</p>
                        </div>
                      )}

                      {a.rawResult && (() => {
                        const parsed = parseResult(a) as ImageAnalysisResult | null;
                        if (!parsed) return null;
                        return (
                          <div className="space-y-4">
                            <div className="rounded-lg bg-primary/5 p-4">
                              <h4 className="mb-1 text-sm font-semibold">Impression</h4>
                              <p className="text-sm">{parsed.impression}</p>
                            </div>

                            {parsed.findings && parsed.findings.length > 0 && (
                              <div>
                                <h4 className="mb-2 text-sm font-semibold">Findings</h4>
                                <div className="space-y-2">
                                  {parsed.findings.map((f, idx) => (
                                    <div key={idx} className="flex items-start gap-3 rounded-lg border p-3">
                                      <span className={`mt-0.5 rounded-full px-2 py-0.5 text-xs font-medium ${SEVERITY_COLORS[f.severity] || 'bg-gray-100 text-gray-700'}`}>
                                        {f.severity}
                                      </span>
                                      <div className="flex-1">
                                        <p className="text-sm font-medium">{f.region}</p>
                                        <p className="text-sm text-muted-foreground">{f.description}</p>
                                      </div>
                                      <span className="text-xs text-muted-foreground">
                                        {(f.confidence * 100).toFixed(0)}%
                                      </span>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}

                            {parsed.icd10Codes && parsed.icd10Codes.length > 0 && (
                              <div>
                                <h4 className="mb-2 text-sm font-semibold">ICD-10 Codes</h4>
                                <div className="flex flex-wrap gap-2">
                                  {parsed.icd10Codes.map((code, idx) => (
                                    <span key={idx} className="rounded-md bg-muted px-2.5 py-1 text-xs font-mono font-medium">
                                      {code}
                                    </span>
                                  ))}
                                </div>
                              </div>
                            )}

                            {parsed.recommendations && parsed.recommendations.length > 0 && (
                              <div>
                                <h4 className="mb-2 text-sm font-semibold">Recommendations</h4>
                                <ul className="space-y-1">
                                  {parsed.recommendations.map((rec, idx) => (
                                    <li key={idx} className="flex items-start gap-2 text-sm">
                                      <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
                                      {rec}
                                    </li>
                                  ))}
                                </ul>
                              </div>
                            )}
                          </div>
                        );
                      })()}
                    </div>
                  )}
                </Card>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
