import React, { useState, useEffect, useCallback } from 'react';
import { patientService } from '@/services/patientService';
import { fileService } from '@/services/fileService';
import {
  analysisService,
  parseImageResult,
  type AnalysisResponse,
  type ImageAnalysisResult,
} from '@/services/analysisService';
import { ImageViewer } from '@/components/medical/ImageViewer';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
import {
  Brain,
  Loader2,
  CheckCircle2,
  Clock,
  XCircle,
  RefreshCw,
  ChevronDown,
  ChevronUp,
  Activity,
  Zap,
  Tag,
  Stethoscope,
  FileImage,
  Copy,
  Check,
  Sparkles,
  AlertCircle,
  Printer,
} from 'lucide-react';
import type { Patient, MedicalFile } from '@/types';

const SEVERITY_CONFIG: Record<string, { badge: string; border: string; bg: string; dot: string }> = {
  NORMAL: {
    badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    border: 'border-emerald-900/40',
    bg: 'bg-emerald-950/10',
    dot: 'bg-emerald-500',
  },
  MILD: {
    badge: 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20',
    border: 'border-yellow-900/40',
    bg: 'bg-yellow-950/10',
    dot: 'bg-yellow-500',
  },
  MODERATE: {
    badge: 'bg-orange-500/10 text-orange-400 border-orange-500/20',
    border: 'border-orange-900/40',
    bg: 'bg-orange-950/10',
    dot: 'bg-orange-500',
  },
  SEVERE: {
    badge: 'bg-red-500/10 text-red-400 border-red-500/20',
    border: 'border-red-900/40',
    bg: 'bg-red-950/10',
    dot: 'bg-red-500',
  },
  CRITICAL: {
    badge: 'bg-rose-500/20 text-rose-300 border-rose-500/30 font-bold animate-pulse',
    border: 'border-rose-800/80 shadow-rose-950/50 shadow-lg',
    bg: 'bg-rose-950/30',
    dot: 'bg-rose-500',
  },
};

const URGENCY_CONFIG: Record<string, { badge: string; label: string }> = {
  ROUTINE: { badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20', label: 'Routine Priority' },
  URGENT: { badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20', label: 'Urgent Attention' },
  CRITICAL: { badge: 'bg-red-500/20 text-red-300 border-red-500/30 animate-pulse font-bold', label: 'Critical STAT Alert' },
};

const STATUS_CONFIG: Record<string, { icon: typeof Clock; color: string; badge: string; label: string }> = {
  PENDING: { icon: Clock, color: 'text-blue-400', badge: 'bg-blue-500/10 text-blue-400 border-blue-500/20', label: 'Queued' },
  PROCESSING: { icon: Loader2, color: 'text-amber-400', badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20', label: 'AI Analyzing...' },
  COMPLETED: { icon: CheckCircle2, color: 'text-emerald-400', badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20', label: 'Completed' },
  FAILED: { icon: XCircle, color: 'text-red-400', badge: 'bg-red-500/10 text-red-400 border-red-500/20', label: 'Failed' },
};

const CLINICAL_PRESETS = [
  'Suspected pneumonia / lower lobe consolidation',
  'Chest trauma evaluation & rib fracture check',
  'Pleural effusion & pulmonary edema screening',
  'Routine pre-operative cardiothoracic clearance',
];

export function AnalysisPage() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = useState('');
  const [files, setFiles] = useState<MedicalFile[]>([]);
  const [selectedFile, setSelectedFile] = useState('');
  const [clinicalNotes, setClinicalNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [analyses, setAnalyses] = useState<AnalysisResponse[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'COMPLETED' | 'PROCESSING' | 'FAILED'>('ALL');
  const [error, setError] = useState('');
  const [polling, setPolling] = useState<string | null>(null);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

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

  // Load Analyses for Patient
  const loadAnalyses = useCallback(async () => {
    if (!selectedPatient) {
      setAnalyses([]);
      return;
    }
    try {
      const res = await analysisService.getPatientAnalyses(selectedPatient, 0, 50);
      setAnalyses(res.content);
      // Auto-expand latest
      if (res.content.length > 0 && !expandedId) {
        setExpandedId(res.content[0].id);
      }
    } catch {
      // silent
    }
  }, [selectedPatient, expandedId]);

  useEffect(() => {
    loadAnalyses();
  }, [loadAnalyses]);

  // Polling for async background analysis
  useEffect(() => {
    if (!polling) return;

    const interval = setInterval(async () => {
      try {
        const result = await analysisService.getAnalysis(polling);
        if (result.status === 'COMPLETED' || result.status === 'FAILED') {
          setPolling(null);
          setSubmitting(false);
          setExpandedId(result.id);
          loadAnalyses();
        }
      } catch {
        setPolling(null);
        setSubmitting(false);
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [polling, loadAnalyses]);

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!selectedPatient || !selectedFile) {
      setError('Please select both a patient and a medical study.');
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      const created = await analysisService.createAnalysis({
        patientId: selectedPatient,
        medicalFileId: selectedFile,
        analysisType: 'IMAGE_ANALYSIS',
        clinicalNotes: clinicalNotes || undefined,
      });

      setPolling(created.id);
      setExpandedId(created.id);
      loadAnalyses();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to submit analysis request.');
      setSubmitting(false);
    }
  };

  const handleRetry = async (analysisId: string) => {
    try {
      const retried = await analysisService.retryAnalysis(analysisId);
      setPolling(retried.id);
      setExpandedId(retried.id);
      loadAnalyses();
    } catch {
      setError('Failed to retry analysis.');
    }
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCode(text);
    setTimeout(() => setCopiedCode(null), 2000);
  };

  const selectedPatientObj = patients.find((p) => p.id === selectedPatient);
  const selectedFileObj = files.find((f) => f.id === selectedFile);
  const filteredAnalyses = analyses.filter((a) => statusFilter === 'ALL' || a.status === statusFilter);

  return (
    <div className="space-y-6 max-w-[1600px] mx-auto pb-12">
      {/* 1. Executive Medical Header */}
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between border-b border-slate-800/80 pb-5">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600/20 text-blue-400 border border-blue-500/30 shadow-md">
              <Brain className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight text-white sm:text-2xl">
                Diagnostic AI Radiology & PACS Workstation
              </h1>
              <p className="text-xs text-slate-400">
                Automated multi-modal clinical intelligence &bull; DICOM, X-Ray, CT, & Laboratory Reports
              </p>
            </div>
          </div>
        </div>

        {/* Global Patient Selector Pills */}
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
              <div className="flex h-7 w-7 items-center justify-center rounded-full bg-blue-600/20 font-bold text-blue-400">
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
          <AlertCircle className="h-4 w-4 shrink-0 text-red-400" />
          <span>{error}</span>
        </div>
      )}

      {/* Main Diagnostic Workspace */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Left Column: Interactive Study Ingestion & PACS Viewer (7 cols) */}
        <div className="space-y-4 lg:col-span-7">
          {/* Active Medical Study Viewer */}
          <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4 shadow-xl backdrop-blur-md">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <FileImage className="h-4 w-4 text-blue-400" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                  Medical Study Inspection Canvas
                </h3>
              </div>
              <span className="text-[10px] text-slate-500 font-mono">
                {files.length} Studies on File
              </span>
            </div>

            {selectedPatient && selectedFile ? (
              <ImageViewer
                patientId={selectedPatient}
                fileId={selectedFile}
                fileName={selectedFileObj?.originalFileName || 'Medical Study'}
                fileType={selectedFileObj?.fileType.replace('_', ' ')}
                fileList={files}
                onSelectFile={(fId) => setSelectedFile(fId)}
                className="h-[490px]"
              />
            ) : (
              <div className="flex h-[400px] flex-col items-center justify-center rounded-xl border border-dashed border-slate-800 bg-slate-900/30 p-8 text-center">
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-800/60 text-slate-500 mb-3">
                  <FileImage className="h-7 w-7" />
                </div>
                <h4 className="text-sm font-semibold text-slate-300">No Medical Study Selected</h4>
                <p className="mt-1 max-w-sm text-xs text-slate-500">
                  Select a patient above and choose a radiological scan or laboratory document to preview.
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
                  Clinical Indication & AI Request
                </h3>
              </div>
              <span className="rounded bg-blue-500/10 px-2 py-0.5 text-[10px] font-bold text-blue-400 border border-blue-500/20">
                Llama 3.3 70B Vision Engine
              </span>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs">
                <Label className="text-slate-400">Clinical Indication / Suspected Conditions</Label>
                <span className="text-[10px] text-slate-500">Quick Presets</span>
              </div>

              {/* Quick Clinical Presets */}
              <div className="flex flex-wrap gap-1.5">
                {CLINICAL_PRESETS.map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => setClinicalNotes(preset)}
                    className="rounded-md border border-slate-800 bg-slate-950/80 px-2.5 py-1 text-[10px] font-medium text-slate-300 transition-colors hover:border-blue-500/50 hover:text-white"
                  >
                    + {preset}
                  </button>
                ))}
              </div>

              <textarea
                rows={2}
                className="w-full rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs text-slate-200 placeholder-slate-600 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 font-sans"
                placeholder="e.g. 54-year-old male with persistent cough, fever for 4 days. Evaluate for left lower lobe consolidation..."
                value={clinicalNotes}
                onChange={(e) => setClinicalNotes(e.target.value)}
              />
            </div>

            <Button
              className="w-full h-11 bg-gradient-to-r from-blue-600 to-indigo-600 text-white hover:from-blue-500 hover:to-indigo-500 font-semibold shadow-lg shadow-blue-600/20 transition-all"
              onClick={() => handleSubmit()}
              disabled={!selectedPatient || !selectedFile || submitting}
            >
              {submitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Processing Diagnostic Vision AI...
                </>
              ) : (
                <>
                  <Sparkles className="mr-2 h-4 w-4" />
                  Execute AI Diagnostic Evaluation
                </>
              )}
            </Button>
          </div>
        </div>

        {/* Right Column: AI Diagnostic Findings & Clinical Impression (5 cols) */}
        <div className="space-y-4 lg:col-span-5">
          {/* Results Header & Filters */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-blue-400" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                Clinical Diagnostic Findings
              </h3>
            </div>

            {analyses.length > 0 && (
              <div className="flex items-center gap-1 rounded-lg border border-slate-800 bg-slate-950 p-1 text-xs">
                {(['ALL', 'COMPLETED', 'PROCESSING', 'FAILED'] as const).map((filter) => (
                  <button
                    key={filter}
                    className={`rounded px-2 py-0.5 text-[10px] font-medium transition-all ${
                      statusFilter === filter
                        ? 'bg-blue-600 text-white shadow-sm'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                    onClick={() => setStatusFilter(filter)}
                  >
                    {filter === 'ALL' ? 'All' : filter.charAt(0) + filter.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* List of Analyses */}
          {filteredAnalyses.length === 0 ? (
            <div className="flex h-[460px] flex-col items-center justify-center rounded-xl border border-dashed border-slate-800 bg-slate-900/20 p-8 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-800 text-slate-400 mb-3">
                <Brain className="h-6 w-6" />
              </div>
              <h4 className="text-sm font-semibold text-slate-300">No Diagnostic Records Yet</h4>
              <p className="mt-1 max-w-xs text-xs text-slate-500">
                Run an AI analysis on the selected study to generate structured radiology impressions and ICD-10 codes.
              </p>
            </div>
          ) : (
            <div className="space-y-4 max-h-[820px] overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
              {filteredAnalyses.map((a) => {
                const isExpanded = expandedId === a.id;
                const statusCfg = STATUS_CONFIG[a.status] || STATUS_CONFIG.PENDING;
                const StatusIcon = statusCfg.icon;
                const urgencyCfg = a.urgency ? URGENCY_CONFIG[a.urgency] : null;
                const parsed: ImageAnalysisResult | null = parseImageResult(a);

                return (
                  <div
                    key={a.id}
                    className={`rounded-xl border transition-all overflow-hidden ${
                      isExpanded
                        ? 'border-slate-700 bg-slate-900/90 shadow-2xl ring-1 ring-blue-500/20'
                        : 'border-slate-800/80 bg-slate-950/80 hover:border-slate-700'
                    }`}
                  >
                    {/* Item Accordion Header */}
                    <div
                      className="flex cursor-pointer items-center justify-between p-4 hover:bg-slate-900/60"
                      onClick={() => setExpandedId(isExpanded ? null : a.id)}
                    >
                      <div className="flex items-center gap-3 overflow-hidden">
                        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-500/10 text-blue-400 border border-blue-500/20">
                          <Stethoscope className="h-4 w-4" />
                        </div>
                        <div className="overflow-hidden">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-xs text-slate-200">
                              {a.analysisType.replace('_', ' ')}
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

                    {/* Detailed Findings Body */}
                    {isExpanded && (
                      <div className="border-t border-slate-800/80 p-4 space-y-4 bg-slate-950/60 text-xs">
                        {/* Error & Retry */}
                        {a.status === 'FAILED' && (
                          <div className="flex items-center justify-between rounded-lg border border-red-500/30 bg-red-950/40 p-3 text-red-300">
                            <span>{a.errorMessage || 'Evaluation interrupted.'}</span>
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

                        {/* Processing Skeleton */}
                        {a.status === 'PROCESSING' && (
                          <div className="flex flex-col items-center justify-center p-8 text-center space-y-3">
                            <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
                            <p className="text-xs font-medium text-slate-300">
                              Neural vision network evaluating radiologic findings...
                            </p>
                          </div>
                        )}

                        {/* Complete Structured Radiology Findings */}
                        {a.status === 'COMPLETED' && parsed && (
                          <div className="space-y-4">
                            {/* Executive Clinical Impression */}
                            <div className="rounded-lg border border-slate-800 bg-slate-900/90 p-3.5 space-y-2">
                              <div className="flex items-center justify-between">
                                <span className="font-bold uppercase tracking-wider text-[10px] text-blue-400 flex items-center gap-1">
                                  <Activity className="h-3 w-3" />
                                  Clinical Impression
                                </span>
                                <button
                                  onClick={() => handleCopy(parsed.impression)}
                                  className="text-slate-400 hover:text-slate-200 transition-colors"
                                  title="Copy Clinical Impression"
                                >
                                  {copiedCode === parsed.impression ? (
                                    <Check className="h-3.5 w-3.5 text-emerald-400" />
                                  ) : (
                                    <Copy className="h-3.5 w-3.5" />
                                  )}
                                </button>
                              </div>
                              <p className="text-xs text-slate-200 leading-relaxed font-sans font-medium">
                                {parsed.impression}
                              </p>
                            </div>

                            {/* Structured Anatomical Findings Cards */}
                            {parsed.findings && parsed.findings.length > 0 && (
                              <div className="space-y-2">
                                <span className="font-bold uppercase tracking-wider text-[10px] text-slate-400">
                                  Anatomical Findings ({parsed.findings.length})
                                </span>

                                <div className="space-y-2">
                                  {parsed.findings.map((finding, idx) => {
                                    const sevCfg = SEVERITY_CONFIG[finding.severity] || SEVERITY_CONFIG.NORMAL;

                                    return (
                                      <div
                                        key={idx}
                                        className={`rounded-lg border p-3 transition-all ${sevCfg.border} ${sevCfg.bg}`}
                                      >
                                        <div className="flex items-center justify-between mb-1.5">
                                          <span className="font-bold text-slate-200 flex items-center gap-1.5">
                                            <span className={`h-2 w-2 rounded-full ${sevCfg.dot}`} />
                                            {finding.region}
                                          </span>
                                          <div className="flex items-center gap-1.5">
                                            {finding.confidence && (
                                              <span className="font-mono text-[10px] text-slate-400">
                                                {Math.round(finding.confidence * 100)}% conf
                                              </span>
                                            )}
                                            <span className={`rounded px-1.5 py-0.5 text-[10px] font-bold border ${sevCfg.badge}`}>
                                              {finding.severity}
                                            </span>
                                          </div>
                                        </div>
                                        <p className="text-xs text-slate-300 font-sans leading-normal">
                                          {finding.description}
                                        </p>
                                      </div>
                                    );
                                  })}
                                </div>
                              </div>
                            )}

                            {/* ICD-10 Codes */}
                            {parsed.icd10Codes && parsed.icd10Codes.length > 0 && (
                              <div className="space-y-1.5">
                                <span className="font-bold uppercase tracking-wider text-[10px] text-slate-400">
                                  Diagnostic ICD-10 Classification
                                </span>
                                <div className="flex flex-wrap gap-1.5">
                                  {parsed.icd10Codes.map((item, idx) => (
                                    <button
                                      key={idx}
                                      onClick={() => handleCopy(item.code)}
                                      className="group flex items-center gap-1 rounded-md border border-slate-800 bg-slate-900 px-2 py-1 text-[11px] text-slate-300 hover:border-blue-500/50 hover:text-white transition-all"
                                      title="Click to copy code"
                                    >
                                      <Tag className="h-3 w-3 text-blue-400" />
                                      <strong className="font-mono text-blue-300">{item.code}</strong>
                                      <span className="text-slate-400 font-normal">&bull; {item.description}</span>
                                    </button>
                                  ))}
                                </div>
                              </div>
                            )}

                            {/* Actionable Clinical Recommendations */}
                            {parsed.recommendations && parsed.recommendations.length > 0 && (
                              <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-3 space-y-1.5">
                                <span className="font-bold uppercase tracking-wider text-[10px] text-amber-400">
                                  Recommended Clinical Actions
                                </span>
                                <ul className="space-y-1 text-slate-300 text-xs list-disc list-inside">
                                  {parsed.recommendations.map((rec, idx) => (
                                    <li key={idx}>{rec}</li>
                                  ))}
                                </ul>
                              </div>
                            )}

                            {/* Print / Export Report Button */}
                            <div className="pt-2">
                              <Button
                                size="sm"
                                variant="outline"
                                className="w-full border-slate-800 bg-slate-900 text-slate-300 hover:bg-slate-800 hover:text-white"
                                onClick={() => window.print()}
                              >
                                <Printer className="mr-2 h-3.5 w-3.5" />
                                Print Official Radiology Report
                              </Button>
                            </div>
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
