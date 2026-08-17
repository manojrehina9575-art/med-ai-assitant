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
import { ImageViewer } from '@/components/medical/ImageViewer';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
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
  Combine,
  Activity,
  Copy,
  Check,
  Sparkles,
  Printer,
} from 'lucide-react';
import type { Patient, MedicalFile } from '@/types';

const FLAG_CONFIG: Record<string, { badge: string; dot: string; label: string }> = {
  NORMAL: { badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20', dot: 'bg-emerald-500', label: 'Normal' },
  HIGH: { badge: 'bg-orange-500/10 text-orange-400 border-orange-500/20 font-semibold', dot: 'bg-orange-500', label: 'High' },
  LOW: { badge: 'bg-blue-500/10 text-blue-400 border-blue-500/20 font-semibold', dot: 'bg-blue-500', label: 'Low' },
  CRITICAL_HIGH: { badge: 'bg-red-500/20 text-red-300 border-red-500/30 font-bold animate-pulse', dot: 'bg-red-500', label: 'Critical High' },
  CRITICAL_LOW: { badge: 'bg-red-500/20 text-red-300 border-red-500/30 font-bold animate-pulse', dot: 'bg-red-500', label: 'Critical Low' },
};

const URGENCY_CONFIG: Record<string, { badge: string; label: string }> = {
  ROUTINE: { badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20', label: 'Routine' },
  URGENT: { badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20', label: 'Urgent Attention' },
  CRITICAL: { badge: 'bg-red-500/20 text-red-300 border-red-500/30 animate-pulse font-bold', label: 'Critical STAT' },
};

const STATUS_CONFIG: Record<string, { icon: typeof Clock; color: string; badge: string; label: string }> = {
  PENDING: { icon: Clock, color: 'text-blue-400', badge: 'bg-blue-500/10 text-blue-400 border-blue-500/20', label: 'Queued' },
  PROCESSING: { icon: Loader2, color: 'text-amber-400', badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20', label: 'AI Extracting...' },
  COMPLETED: { icon: CheckCircle2, color: 'text-emerald-400', badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20', label: 'Completed' },
  FAILED: { icon: XCircle, color: 'text-red-400', badge: 'bg-red-500/10 text-red-400 border-red-500/20', label: 'Failed' },
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
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // Load Patients
  useEffect(() => {
    patientService.list(0, 100).then((res) => setPatients(res.content)).catch(() => {});
  }, []);

  // Load Patient Files
  useEffect(() => {
    if (!selectedPatient) {
      setFiles([]);
      setSelectedFile('');
      return;
    }
    fileService
      .list(selectedPatient, 0, 100)
      .then((res) => {
        setFiles(res.content);
        if (res.content.length > 0 && !selectedFile) {
          setSelectedFile(res.content[0].id);
        }
      })
      .catch(() => {});
  }, [selectedPatient]);

  // Load Analyses
  const loadAnalyses = useCallback(async () => {
    if (!selectedPatient) return;
    try {
      const res = await analysisService.listByPatient(selectedPatient);
      const filtered = res.content.filter(
        (a) => a.analysisType === 'BLOOD_REPORT' || a.analysisType === 'COMBINED'
      );
      setAnalyses(filtered);
      if (filtered.length > 0 && !expandedId) {
        setExpandedId(filtered[0].id);
      }
    } catch {
      // silent
    }
  }, [selectedPatient, expandedId]);

  useEffect(() => {
    loadAnalyses();
  }, [loadAnalyses]);

  // Polling
  useEffect(() => {
    if (!polling) return;
    const interval = setInterval(async () => {
      try {
        const updated = await analysisService.get(polling);
        if (updated.status === 'COMPLETED' || updated.status === 'FAILED') {
          setPolling(null);
          setSubmitting(false);
          setExpandedId(updated.id);
          loadAnalyses();
        }
      } catch {
        setPolling(null);
        setSubmitting(false);
      }
    }, 2000);
    return () => clearInterval(interval);
  }, [polling, loadAnalyses]);

  const handleSubmit = async () => {
    if (!selectedPatient || !selectedFile) {
      setError('Please select both a patient and a laboratory file.');
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      let created: AnalysisResponse;
      if (analysisMode === 'blood') {
        created = await analysisService.requestBloodReport(selectedPatient, selectedFile, clinicalNotes || undefined);
      } else {
        created = await analysisService.requestCombined(selectedPatient, selectedFile, clinicalNotes || undefined);
      }

      setPolling(created.id);
      setExpandedId(created.id);
      loadAnalyses();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to submit analysis.');
      setSubmitting(false);
    }
  };

  const handleRetry = async (analysisId: string) => {
    try {
      const retried = await analysisService.retry(analysisId);
      setPolling(retried.id);
      setExpandedId(retried.id);
      loadAnalyses();
    } catch {
      setError('Failed to retry analysis.');
    }
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(text);
    setTimeout(() => setCopiedText(null), 2000);
  };

  const selectedPatientObj = patients.find((p) => p.id === selectedPatient);
  const selectedFileObj = files.find((f) => f.id === selectedFile);

  return (
    <div className="space-y-6 max-w-[1600px] mx-auto pb-12">
      {/* 1. Executive Header */}
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between border-b border-slate-800/80 pb-5">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-600/20 text-emerald-400 border border-emerald-500/30 shadow-md">
            <FileText className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-white sm:text-2xl">
              Laboratory Pathology & Combined Diagnostic Engine
            </h1>
            <p className="text-xs text-slate-400">
              Automated biomarker extraction, abnormality flagging, and multimodal diagnostic correlation
            </p>
          </div>
        </div>

        {/* Global Patient Selector */}
        <div className="flex items-center gap-3">
          <div className="relative min-w-[280px]">
            <select
              className="h-10 w-full appearance-none rounded-lg border border-slate-700 bg-slate-900 px-3.5 pr-9 text-xs font-medium text-slate-200 shadow-inner transition-colors focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              value={selectedPatient}
              onChange={(e) => {
                setSelectedPatient(e.target.value);
                setSelectedFile('');
              }}
            >
              <option value="">Select Patient Record...</option>
              {patients.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName} (MRN: {p.medicalRecordNumber})
                </option>
              ))}
            </select>
            <ChevronDown className="pointer-events-none absolute right-3 top-3 h-4 w-4 text-slate-400" />
          </div>

          {selectedPatientObj && (
            <div className="hidden items-center gap-2 rounded-lg border border-slate-800 bg-slate-900/60 px-3 py-1.5 text-xs sm:flex">
              <div className="flex h-7 w-7 items-center justify-center rounded-full bg-emerald-600/20 font-bold text-emerald-400">
                {selectedPatientObj.fullName.charAt(0)}
              </div>
              <div>
                <p className="font-semibold text-slate-200">{selectedPatientObj.fullName}</p>
                <p className="text-[10px] text-slate-400 font-mono">
                  {selectedPatientObj.gender} &bull; Blood: {selectedPatientObj.bloodGroup || 'N/A'}
                </p>
              </div>
            </div>
          )}
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2.5 rounded-lg border border-red-500/30 bg-red-950/40 p-3 text-xs text-red-300 backdrop-blur-md">
          <AlertTriangle className="h-4 w-4 shrink-0 text-red-400" />
          <span>{error}</span>
        </div>
      )}

      {/* Main Diagnostic Workspace */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Left Column: Interactive Study Ingestion & Document Canvas (7 cols) */}
        <div className="space-y-4 lg:col-span-7">
          {/* Active Medical Study Viewer */}
          <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4 shadow-xl backdrop-blur-md">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <FileText className="h-4 w-4 text-emerald-400" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                  Laboratory Document & Report Preview
                </h3>
              </div>
              <span className="text-[10px] text-slate-500 font-mono">
                {files.length} Patient Documents
              </span>
            </div>

            {selectedPatient && selectedFile ? (
              <ImageViewer
                patientId={selectedPatient}
                fileId={selectedFile}
                fileName={selectedFileObj?.originalFileName || 'Lab Report Document'}
                fileType={selectedFileObj?.fileType.replace('_', ' ')}
                fileList={files}
                onSelectFile={(fId) => setSelectedFile(fId)}
                className="h-[490px]"
              />
            ) : (
              <div className="flex h-[400px] flex-col items-center justify-center rounded-xl border border-dashed border-slate-800 bg-slate-900/30 p-8 text-center">
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-800/60 text-slate-500 mb-3">
                  <FileText className="h-7 w-7" />
                </div>
                <h4 className="text-sm font-semibold text-slate-300">No Laboratory Report Selected</h4>
                <p className="mt-1 max-w-sm text-xs text-slate-500">
                  Select a patient above to inspect blood tests, CBC panels, or pathology documents.
                </p>
              </div>
            )}
          </div>

          {/* AI Clinical Execution Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-lg backdrop-blur-md space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Zap className="h-4 w-4 text-amber-400" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                  Diagnostic Reasoning Mode
                </h3>
              </div>

              {/* Mode Toggle */}
              <div className="flex rounded-lg border border-slate-800 bg-slate-950 p-1 text-xs">
                <button
                  type="button"
                  className={`flex items-center gap-1.5 rounded px-3 py-1 font-semibold transition-all ${
                    analysisMode === 'blood'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                  onClick={() => setAnalysisMode('blood')}
                >
                  <FileText className="h-3.5 w-3.5" />
                  Blood Report Only
                </button>
                <button
                  type="button"
                  className={`flex items-center gap-1.5 rounded px-3 py-1 font-semibold transition-all ${
                    analysisMode === 'combined'
                      ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                  onClick={() => setAnalysisMode('combined')}
                >
                  <Combine className="h-3.5 w-3.5" />
                  Combined Reasoning (Lab + Imaging)
                </button>
              </div>
            </div>

            <div className="space-y-2">
              <Label className="text-xs text-slate-400">Clinical Indication & Suspected Conditions</Label>
              <textarea
                rows={2}
                className="w-full rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs text-slate-200 placeholder-slate-600 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 font-sans"
                placeholder="e.g. Complete Blood Count (CBC) with differential. Patient presents with unexplained fever and leukocytosis..."
                value={clinicalNotes}
                onChange={(e) => setClinicalNotes(e.target.value)}
              />
            </div>

            <Button
              className={`w-full h-11 text-white font-semibold shadow-lg transition-all ${
                analysisMode === 'blood'
                  ? 'bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 shadow-emerald-600/20'
                  : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 shadow-blue-600/20'
              }`}
              onClick={handleSubmit}
              disabled={!selectedPatient || !selectedFile || submitting}
            >
              {submitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Extracting Biomarkers & Running Clinical Correlation...
                </>
              ) : (
                <>
                  <Sparkles className="mr-2 h-4 w-4" />
                  {analysisMode === 'blood' ? 'Extract & Analyze Blood Report' : 'Execute Combined Multimodal Diagnostic'}
                </>
              )}
            </Button>
          </div>
        </div>

        {/* Right Column: Extracted Values Table & Pathology Findings (5 cols) */}
        <div className="space-y-4 lg:col-span-5">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-emerald-400" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                Extracted Laboratory Findings
              </h3>
            </div>
            <span className="text-[10px] text-slate-500 font-mono">{analyses.length} Evaluations</span>
          </div>

          {/* Analyses List */}
          {analyses.length === 0 ? (
            <div className="flex h-[460px] flex-col items-center justify-center rounded-xl border border-dashed border-slate-800 bg-slate-900/20 p-8 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-800 text-slate-400 mb-3">
                <FileText className="h-6 w-6" />
              </div>
              <h4 className="text-sm font-semibold text-slate-300">No Laboratory Records Yet</h4>
              <p className="mt-1 max-w-xs text-xs text-slate-500">
                Run an extraction on the uploaded lab document to generate structured biomarker tables and diagnostic interpretations.
              </p>
            </div>
          ) : (
            <div className="space-y-4 max-h-[820px] overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
              {analyses.map((a) => {
                const isExpanded = expandedId === a.id;
                const statusCfg = STATUS_CONFIG[a.status] || STATUS_CONFIG.PENDING;
                const StatusIcon = statusCfg.icon;
                const urgencyCfg = a.urgency ? URGENCY_CONFIG[a.urgency] : null;

                const bloodResult = a.analysisType === 'BLOOD_REPORT' ? parseResult<BloodReportResult>(a) : null;
                const combinedResult = a.analysisType === 'COMBINED' ? parseResult<CombinedAnalysisResult>(a) : null;

                return (
                  <div
                    key={a.id}
                    className={`rounded-xl border transition-all overflow-hidden ${
                      isExpanded
                        ? 'border-slate-700 bg-slate-900/90 shadow-2xl ring-1 ring-emerald-500/20'
                        : 'border-slate-800/80 bg-slate-950/80 hover:border-slate-700'
                    }`}
                  >
                    {/* Header */}
                    <div
                      className="flex cursor-pointer items-center justify-between p-4 hover:bg-slate-900/60"
                      onClick={() => setExpandedId(isExpanded ? null : a.id)}
                    >
                      <div className="flex items-center gap-3 overflow-hidden">
                        <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border ${
                          a.analysisType === 'COMBINED'
                            ? 'bg-blue-500/10 text-blue-400 border-blue-500/20'
                            : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                        }`}>
                          {a.analysisType === 'COMBINED' ? <Combine className="h-4 w-4" /> : <FileText className="h-4 w-4" />}
                        </div>
                        <div className="overflow-hidden">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-xs text-slate-200">
                              {a.analysisType === 'COMBINED' ? 'Combined Diagnostic Assessment' : 'Blood Panel Extraction'}
                            </span>
                            {urgencyCfg && (
                              <span className={`rounded px-1.5 py-0.5 text-[10px] font-bold border ${urgencyCfg.badge}`}>
                                {urgencyCfg.label}
                              </span>
                            )}
                          </div>
                          <p className="text-[10px] text-slate-400 mt-0.5">
                            {new Date(a.createdAt).toLocaleString()} &bull; {a.modelUsed || 'AI Engine'}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 shrink-0">
                        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium border ${statusCfg.badge}`}>
                          <StatusIcon className={`h-3 w-3 ${a.status === 'PROCESSING' ? 'animate-spin' : ''}`} />
                          {statusCfg.label}
                        </span>
                        {isExpanded ? <ChevronUp className="h-4 w-4 text-slate-400" /> : <ChevronDown className="h-4 w-4 text-slate-400" />}
                      </div>
                    </div>

                    {/* Body */}
                    {isExpanded && (
                      <div className="border-t border-slate-800/80 p-4 space-y-4 bg-slate-950/60 text-xs">
                        {/* Error & Retry */}
                        {a.status === 'FAILED' && (
                          <div className="flex items-center justify-between rounded-lg border border-red-500/30 bg-red-950/40 p-3 text-red-300">
                            <span>{a.errorMessage || 'Laboratory extraction interrupted.'}</span>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => handleRetry(a.id)}
                              className="border-red-800 bg-red-900/40 text-red-200 hover:bg-red-800"
                            >
                              <RefreshCw className="mr-1 h-3 w-3" /> Retry
                            </Button>
                          </div>
                        )}

                        {/* Processing */}
                        {a.status === 'PROCESSING' && (
                          <div className="flex flex-col items-center justify-center p-8 text-center space-y-3">
                            <Loader2 className="h-8 w-8 animate-spin text-emerald-500" />
                            <p className="text-xs font-medium text-slate-300">
                              Neural vision network parsing laboratory document and biomarker matrix...
                            </p>
                          </div>
                        )}

                        {/* BLOOD REPORT VIEW */}
                        {a.status === 'COMPLETED' && bloodResult && (
                          <div className="space-y-4">
                            {/* Pathology Flags */}
                            {bloodResult.flags && bloodResult.flags.length > 0 && (
                              <div className="flex flex-wrap gap-1.5">
                                {bloodResult.flags.map((flag, idx) => (
                                  <span
                                    key={idx}
                                    className="rounded bg-red-500/20 px-2 py-0.5 text-[10px] font-bold text-red-300 border border-red-500/30"
                                  >
                                    &bull; {flag}
                                  </span>
                                ))}
                              </div>
                            )}

                            {/* Interpretation Card */}
                            <div className="rounded-lg border border-slate-800 bg-slate-900/90 p-3.5 space-y-2">
                              <div className="flex items-center justify-between">
                                <span className="font-bold uppercase tracking-wider text-[10px] text-emerald-400 flex items-center gap-1">
                                  <Activity className="h-3 w-3" />
                                  Pathologist Interpretation
                                </span>
                                <button
                                  onClick={() => handleCopy(bloodResult.interpretation)}
                                  className="text-slate-400 hover:text-slate-200 transition-colors"
                                  title="Copy Interpretation"
                                >
                                  {copiedText === bloodResult.interpretation ? (
                                    <Check className="h-3.5 w-3.5 text-emerald-400" />
                                  ) : (
                                    <Copy className="h-3.5 w-3.5" />
                                  )}
                                </button>
                              </div>
                              <p className="text-xs text-slate-200 leading-relaxed font-sans font-medium">
                                {bloodResult.interpretation}
                              </p>
                            </div>

                            {/* Extracted Parameters Table */}
                            {bloodResult.parameters && bloodResult.parameters.length > 0 && (
                              <div className="rounded-lg border border-slate-800 overflow-hidden bg-slate-900/40">
                                <div className="bg-slate-900 px-3 py-2 border-b border-slate-800 flex items-center justify-between">
                                  <span className="font-bold uppercase tracking-wider text-[10px] text-slate-300">
                                    {bloodResult.testName || 'Complete Blood Count (CBC) Panel'}
                                  </span>
                                  <span className="text-[10px] text-slate-500">
                                    {bloodResult.parameters.length} Biomarkers
                                  </span>
                                </div>
                                <table className="w-full text-left text-xs">
                                  <thead>
                                    <tr className="border-b border-slate-800 text-[10px] font-semibold text-slate-400 bg-slate-950/40">
                                      <th className="p-2.5">Parameter</th>
                                      <th className="p-2.5">Value</th>
                                      <th className="p-2.5">Ref Range</th>
                                      <th className="p-2.5 text-right">Status</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-slate-800/60 font-mono">
                                    {bloodResult.parameters.map((param, idx) => {
                                      const flagCfg = FLAG_CONFIG[param.flag] || FLAG_CONFIG.NORMAL;

                                      return (
                                        <tr key={idx} className="hover:bg-slate-900/40">
                                          <td className="p-2.5 font-sans font-semibold text-slate-200">
                                            {param.name}
                                          </td>
                                          <td className="p-2.5 font-bold text-slate-100">
                                            {param.value} <span className="text-[10px] font-normal text-slate-400">{param.unit}</span>
                                          </td>
                                          <td className="p-2.5 text-slate-400 text-[11px]">
                                            {param.referenceRange || 'N/A'}
                                          </td>
                                          <td className="p-2.5 text-right">
                                            <span className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] border ${flagCfg.badge}`}>
                                              <span className={`h-1.5 w-1.5 rounded-full ${flagCfg.dot}`} />
                                              {flagCfg.label}
                                            </span>
                                          </td>
                                        </tr>
                                      );
                                    })}
                                  </tbody>
                                </table>
                              </div>
                            )}

                            {/* Print Report */}
                            <Button
                              size="sm"
                              variant="outline"
                              className="w-full border-slate-800 bg-slate-900 text-slate-300 hover:bg-slate-800 hover:text-white"
                              onClick={() => window.print()}
                            >
                              <Printer className="mr-2 h-3.5 w-3.5" />
                              Print Laboratory Pathology Report
                            </Button>
                          </div>
                        )}

                        {/* COMBINED MULTIMODAL VIEW */}
                        {a.status === 'COMPLETED' && combinedResult && (
                          <div className="space-y-4">
                            {/* Overall Assessment */}
                            <div className="rounded-lg border border-blue-900/40 bg-blue-950/20 p-3.5 space-y-2">
                              <span className="font-bold uppercase tracking-wider text-[10px] text-blue-400 flex items-center gap-1">
                                <Combine className="h-3.5 w-3.5" />
                                Unified Diagnostic Assessment
                              </span>
                              <p className="text-xs text-slate-200 leading-relaxed font-sans font-medium">
                                {combinedResult.overallAssessment}
                              </p>
                            </div>

                            {/* Diagnoses with Confidence & Supporting Evidence */}
                            {combinedResult.diagnoses && combinedResult.diagnoses.length > 0 && (
                              <div className="space-y-2">
                                <span className="font-bold uppercase tracking-wider text-[10px] text-slate-400">
                                  Differential Diagnoses & Evidence
                                </span>
                                <div className="space-y-2">
                                  {combinedResult.diagnoses.map((diag, idx) => (
                                    <div key={idx} className="rounded-lg border border-slate-800 bg-slate-900/80 p-3 space-y-1.5">
                                      <div className="flex items-center justify-between">
                                        <span className="font-bold text-slate-200">{diag.diagnosis}</span>
                                        <div className="flex items-center gap-2">
                                          <span className="font-mono text-[10px] text-blue-400 font-bold">
                                            {Math.round(diag.confidence * 100)}% Confidence
                                          </span>
                                          <span className="rounded bg-blue-500/10 px-1.5 py-0.5 text-[10px] font-mono text-blue-300 border border-blue-500/20">
                                            {diag.icd10Code}
                                          </span>
                                        </div>
                                      </div>
                                      {diag.supportingEvidence && diag.supportingEvidence.length > 0 && (
                                        <ul className="list-disc list-inside text-slate-400 text-[11px] space-y-0.5">
                                          {diag.supportingEvidence.map((ev, eIdx) => (
                                            <li key={eIdx}>{ev}</li>
                                          ))}
                                        </ul>
                                      )}
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}

                            {/* Recommendations */}
                            {combinedResult.recommendations && combinedResult.recommendations.length > 0 && (
                              <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-3 space-y-1.5">
                                <span className="font-bold uppercase tracking-wider text-[10px] text-amber-400">
                                  Unified Clinical Recommendations
                                </span>
                                <ul className="space-y-1 text-slate-300 text-xs list-disc list-inside">
                                  {combinedResult.recommendations.map((rec, idx) => (
                                    <li key={idx}>{rec}</li>
                                  ))}
                                </ul>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )}
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
