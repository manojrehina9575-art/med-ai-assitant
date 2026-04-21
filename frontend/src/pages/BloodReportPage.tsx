import { useState, useEffect, useCallback } from 'react';
import { patientService } from '@/services/patientService';
import { fileService } from '@/services/fileService';
import {
  analysisService,
  parseResult,
  type AnalysisResponse,
  type BloodReportResult,
  type CombinedAnalysisResult,
} from '@/services/analysisService';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import {
  FileText,
  Loader2,
  AlertTriangle,
  CheckCircle2,
  Clock,
  XCircle,
  RefreshCw,
  ChevronDown,
  ChevronUp,
  Zap,
  ArrowUpDown,
  Combine,
} from 'lucide-react';
import type { Patient, MedicalFile } from '@/types';

const FLAG_COLORS: Record<string, string> = {
  NORMAL: 'text-green-700 bg-green-50',
  HIGH: 'text-orange-700 bg-orange-50',
  LOW: 'text-blue-700 bg-blue-50',
  CRITICAL_HIGH: 'text-red-700 bg-red-100 font-bold',
  CRITICAL_LOW: 'text-red-700 bg-red-100 font-bold',
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

export function BloodReportPage() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = useState('');
  const [files, setFiles] = useState<MedicalFile[]>([]);
  const [selectedFile, setSelectedFile] = useState('');
  const [clinicalNotes, setClinicalNotes] = useState('');
  const [analysisMode, setAnalysisMode] = useState<'blood' | 'combined'>('blood');
  const [submitting, setSubmitting] = useState(false);
  const [analyses, setAnalyses] = useState<AnalysisResponse[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [polling, setPolling] = useState<string | null>(null);

  useEffect(() => {
    patientService.list(0, 100).then((res) => setPatients(res.content)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!selectedPatient) { setFiles([]); setSelectedFile(''); return; }
    fileService.list(selectedPatient, 0, 100).then((res) => setFiles(res.content)).catch(() => {});
  }, [selectedPatient]);

  const loadAnalyses = useCallback(async () => {
    if (!selectedPatient) return;
    try {
      const res = await analysisService.listByPatient(selectedPatient);
      setAnalyses(res.content.filter(
        (a) => a.analysisType === 'BLOOD_REPORT' || a.analysisType === 'COMBINED'
      ));
    } catch { /* ignore */ }
  }, [selectedPatient]);

  useEffect(() => { loadAnalyses(); }, [loadAnalyses]);

  useEffect(() => {
    if (!polling) return;
    const interval = setInterval(async () => {
      try {
        const updated = await analysisService.get(polling);
        if (updated.status === 'COMPLETED' || updated.status === 'FAILED') {
          setPolling(null);
          loadAnalyses();
        }
      } catch { setPolling(null); }
    }, 3000);
    return () => clearInterval(interval);
  }, [polling, loadAnalyses]);

  const handleSubmit = async () => {
    if (!selectedPatient || !selectedFile) return;
    setError('');
    setSubmitting(true);
    try {
      const fn = analysisMode === 'combined'
        ? analysisService.requestCombined
        : analysisService.requestBloodReport;
      const result = await fn(selectedPatient, selectedFile, clinicalNotes || undefined);
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
    } catch { /* ignore */ }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Blood Report &amp; Combined Analysis</h1>
        <p className="text-muted-foreground">
          Extract lab values from blood reports and combine with imaging for unified diagnosis
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Form */}
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FileText className="h-5 w-5" />
              New Report Analysis
            </CardTitle>
            <CardDescription>Upload a blood report for AI extraction</CardDescription>
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
              <Label>Blood Report File</Label>
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
            </div>

            <div className="space-y-2">
              <Label>Analysis Mode</Label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  className={`flex items-center justify-center gap-1.5 rounded-md border px-3 py-2 text-sm font-medium transition-colors ${
                    analysisMode === 'blood'
                      ? 'border-primary bg-primary/10 text-primary'
                      : 'border-input hover:bg-accent'
                  }`}
                  onClick={() => setAnalysisMode('blood')}
                >
                  <ArrowUpDown className="h-4 w-4" />
                  Blood Report
                </button>
                <button
                  type="button"
                  className={`flex items-center justify-center gap-1.5 rounded-md border px-3 py-2 text-sm font-medium transition-colors ${
                    analysisMode === 'combined'
                      ? 'border-primary bg-primary/10 text-primary'
                      : 'border-input hover:bg-accent'
                  }`}
                  onClick={() => setAnalysisMode('combined')}
                >
                  <Combine className="h-4 w-4" />
                  Combined
                </button>
              </div>
              <p className="text-xs text-muted-foreground">
                {analysisMode === 'combined'
                  ? 'Merges imaging + blood report + patient history for unified diagnosis'
                  : 'Extracts lab values with flags and clinical interpretation'}
              </p>
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

            <Button className="w-full" onClick={handleSubmit} disabled={!selectedPatient || !selectedFile || submitting}>
              {submitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Zap className="mr-2 h-4 w-4" />}
              {analysisMode === 'combined' ? 'Run Combined Analysis' : 'Analyze Blood Report'}
            </Button>

            {polling && (
              <div className="flex items-center gap-2 rounded-md bg-blue-50 p-3 text-sm text-blue-700">
                <Loader2 className="h-4 w-4 animate-spin" />
                Analysis in progress... auto-refreshing
              </div>
            )}
          </CardContent>
        </Card>

        {/* Results */}
        <div className="space-y-4 lg:col-span-2">
          <h2 className="text-xl font-semibold">
            {selectedPatient ? 'Report History' : 'Select a patient to view reports'}
          </h2>

          {!selectedPatient ? (
            <Card>
              <CardContent className="flex flex-col items-center py-12">
                <FileText className="mb-3 h-12 w-12 text-muted-foreground" />
                <p className="text-muted-foreground">Select a patient to see blood report analyses</p>
              </CardContent>
            </Card>
          ) : analyses.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center py-12">
                <FileText className="mb-3 h-12 w-12 text-muted-foreground" />
                <p className="font-medium">No blood report analyses yet</p>
                <p className="text-sm text-muted-foreground">Submit a blood report for AI extraction</p>
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
                            <RefreshCw className="mr-1 h-3 w-3" /> Retry
                          </Button>
                        </div>
                      )}

                      {a.rawResult && a.analysisType === 'BLOOD_REPORT' && (() => {
                        const parsed = parseResult(a) as BloodReportResult | null;
                        if (!parsed) return null;
                        return (
                          <div className="space-y-4">
                            <div className="rounded-lg bg-primary/5 p-4">
                              <h4 className="text-sm font-semibold">{parsed.testName}</h4>
                              <p className="mt-1 text-sm">{parsed.interpretation}</p>
                            </div>

                            {parsed.flags && parsed.flags.length > 0 && (
                              <div className="flex flex-wrap gap-2">
                                {parsed.flags.map((flag, idx) => (
                                  <span key={idx} className="rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-700">
                                    {flag.replace(/_/g, ' ')}
                                  </span>
                                ))}
                              </div>
                            )}

                            {parsed.parameters && parsed.parameters.length > 0 && (
                              <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                  <thead>
                                    <tr className="border-b text-left text-muted-foreground">
                                      <th className="pb-2 pr-4 font-medium">Parameter</th>
                                      <th className="pb-2 pr-4 font-medium">Value</th>
                                      <th className="pb-2 pr-4 font-medium">Unit</th>
                                      <th className="pb-2 pr-4 font-medium">Reference</th>
                                      <th className="pb-2 font-medium">Status</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {parsed.parameters.map((p, idx) => (
                                      <tr key={idx} className="border-b last:border-0">
                                        <td className="py-2 pr-4 font-medium">{p.name}</td>
                                        <td className="py-2 pr-4">{p.value}</td>
                                        <td className="py-2 pr-4 text-muted-foreground">{p.unit}</td>
                                        <td className="py-2 pr-4 text-muted-foreground">{p.referenceRange}</td>
                                        <td className="py-2">
                                          <span className={`rounded px-2 py-0.5 text-xs font-medium ${FLAG_COLORS[p.flag] || 'bg-gray-100 text-gray-700'}`}>
                                            {p.flag.replace(/_/g, ' ')}
                                          </span>
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            )}
                          </div>
                        );
                      })()}

                      {a.rawResult && a.analysisType === 'COMBINED' && (() => {
                        const parsed = parseResult(a) as CombinedAnalysisResult | null;
                        if (!parsed) return null;
                        return (
                          <div className="space-y-4">
                            <div className="rounded-lg bg-primary/5 p-4">
                              <h4 className="mb-1 text-sm font-semibold">Overall Assessment</h4>
                              <p className="text-sm">{parsed.overallAssessment}</p>
                            </div>

                            {parsed.clinicalCorrelation && (
                              <div className="rounded-lg border p-4">
                                <h4 className="mb-1 text-sm font-semibold">Clinical Correlation</h4>
                                <p className="text-sm text-muted-foreground">{parsed.clinicalCorrelation}</p>
                              </div>
                            )}

                            {parsed.criticalFindings && parsed.criticalFindings.length > 0 && (
                              <div className="rounded-lg bg-red-50 p-4">
                                <h4 className="mb-2 text-sm font-semibold text-red-700">Critical Findings</h4>
                                <ul className="space-y-1">
                                  {parsed.criticalFindings.map((f, idx) => (
                                    <li key={idx} className="flex items-start gap-2 text-sm text-red-700">
                                      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                                      {f}
                                    </li>
                                  ))}
                                </ul>
                              </div>
                            )}

                            {parsed.diagnoses && parsed.diagnoses.length > 0 && (
                              <div>
                                <h4 className="mb-2 text-sm font-semibold">Diagnoses</h4>
                                <div className="space-y-2">
                                  {parsed.diagnoses.map((d, idx) => (
                                    <div key={idx} className="rounded-lg border p-3">
                                      <div className="flex items-center justify-between">
                                        <span className="font-medium text-sm">{d.diagnosis}</span>
                                        <div className="flex items-center gap-2">
                                          <span className="rounded-md bg-muted px-2 py-0.5 text-xs font-mono">{d.icd10Code}</span>
                                          <span className="text-xs text-muted-foreground">{(d.confidence * 100).toFixed(0)}%</span>
                                        </div>
                                      </div>
                                      {d.supportingEvidence && d.supportingEvidence.length > 0 && (
                                        <ul className="mt-2 space-y-0.5">
                                          {d.supportingEvidence.map((ev, eidx) => (
                                            <li key={eidx} className="flex items-start gap-2 text-xs text-muted-foreground">
                                              <span className="mt-1 h-1 w-1 shrink-0 rounded-full bg-muted-foreground" />
                                              {ev}
                                            </li>
                                          ))}
                                        </ul>
                                      )}
                                    </div>
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

                            {parsed.confidenceScore != null && (
                              <div className="text-sm text-muted-foreground">
                                Overall Confidence: <span className="font-medium">{(parsed.confidenceScore * 100).toFixed(0)}%</span>
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
