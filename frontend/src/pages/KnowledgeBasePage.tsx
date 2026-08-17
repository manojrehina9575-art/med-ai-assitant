import React, { useState, useEffect, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import {
  knowledgeService,
  type KnowledgeDocument,
  type DocumentType,
  type RagResponse,
} from '@/services/knowledgeService';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
import {
  BookOpen,
  Search,
  Upload,
  FileText,
  Loader2,
  CheckCircle2,
  Sparkles,
  Copy,
  Check,
  Trash2,
  ChevronRight,
  HelpCircle,
  Database,
  Plus,
  Printer,
  ShieldCheck,
  Layers,
} from 'lucide-react';

const DOC_TYPE_CONFIG: Record<DocumentType, { label: string; badge: string }> = {
  CLINICAL_PROTOCOL: { label: 'Clinical Protocol', badge: 'bg-blue-500/10 text-blue-400 border-blue-500/20' },
  GUIDELINE: { label: 'Practice Guideline', badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
  DRUG_FORMULARY: { label: 'Drug Formulary', badge: 'bg-purple-500/10 text-purple-400 border-purple-500/20' },
  SOP: { label: 'Hospital SOP', badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20' },
  JOURNAL: { label: 'Medical Journal', badge: 'bg-slate-500/10 text-slate-400 border-slate-500/20' },
};

const SUGGESTED_QUERIES = [
  'What is our hospital protocol for Community-Acquired Pneumonia (CAP)?',
  'What are the first-line empiric antibiotics for suspected Inpatient Sepsis?',
  'What are the pre-operative fasting and glucose management guidelines for diabetic patients?',
  'What is the emergency anticoagulation reversal protocol for acute ICH?',
];

export function KnowledgeBasePage() {
  const [activeTab, setActiveTab] = useState<'qa' | 'docs'>('qa');

  // Q&A State
  const [query, setQuery] = useState('');
  const [loadingQuery, setLoadingQuery] = useState(false);
  const [ragResult, setRagResult] = useState<RagResponse | null>(null);
  const [expandedCitation, setExpandedCitation] = useState<number | null>(null);
  const [copied, setCopied] = useState(false);

  // Documents State
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [selectedType, setSelectedType] = useState<DocumentType | 'ALL'>('ALL');
  const [searchFilter, setSearchFilter] = useState('');

  // Upload State
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [docTitle, setDocTitle] = useState('');
  const [docType, setDocType] = useState<DocumentType>('CLINICAL_PROTOCOL');
  const [docSource, setDocSource] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [showUploadModal, setShowUploadModal] = useState(false);

  // Load Documents
  const loadDocuments = useCallback(async () => {
    setLoadingDocs(true);
    try {
      const typeParam = selectedType === 'ALL' ? undefined : selectedType;
      const res = await knowledgeService.listDocuments(typeParam, 0, 100);
      setDocuments(res.content);
    } catch {
      // silent
    } finally {
      setLoadingDocs(false);
    }
  }, [selectedType]);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  // Handle RAG Query
  const handleQuerySubmit = async (searchQuery?: string) => {
    const q = searchQuery || query;
    if (!q.trim()) return;

    setLoadingQuery(true);
    setRagResult(null);
    setExpandedCitation(null);

    try {
      const res = await knowledgeService.queryKnowledgeBase(q);
      setRagResult(res);
    } catch (err) {
      console.error('RAG query failed:', err);
    } finally {
      setLoadingQuery(false);
    }
  };

  // Handle Dropzone Upload
  const onDrop = useCallback((acceptedFiles: File[]) => {
    if (acceptedFiles.length > 0) {
      const file = acceptedFiles[0];
      setUploadFile(file);
      if (!docTitle) {
        setDocTitle(file.name.replace(/\.[^/.]+$/, ''));
      }
    }
  }, [docTitle]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    maxFiles: 1,
    accept: {
      'application/pdf': ['.pdf'],
      'text/plain': ['.txt'],
      'text/markdown': ['.md'],
    },
  });

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadFile) return;

    setUploading(true);
    setUploadError('');

    try {
      await knowledgeService.uploadDocument(
        uploadFile,
        docTitle || uploadFile.name,
        docType,
        docSource || undefined
      );

      setUploadFile(null);
      setDocTitle('');
      setDocSource('');
      setShowUploadModal(false);
      loadDocuments();
    } catch (err: any) {
      setUploadError(err?.response?.data?.message || 'Failed to upload document.');
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteDoc = async (id: string) => {
    if (!confirm('Are you sure you want to delete this document and its vector embeddings?')) return;
    try {
      await knowledgeService.deleteDocument(id);
      loadDocuments();
    } catch {
      alert('Failed to delete document.');
    }
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const filteredDocs = documents.filter((d) =>
    searchFilter ? d.title.toLowerCase().includes(searchFilter.toLowerCase()) : true
  );

  return (
    <div className="space-y-6 max-w-[1600px] mx-auto pb-12">
      {/* 1. Executive Header */}
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between border-b border-slate-800/80 pb-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-blue-600 text-white shadow-lg shadow-indigo-500/20">
            <BookOpen className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-white sm:text-2xl">
              Hospital Knowledge Base & Clinical RAG Engine
            </h1>
            <p className="text-xs text-slate-400">
              Grounded AI clinical decision support indexed directly from hospital protocols & SOPs
            </p>
          </div>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center gap-2 rounded-xl border border-slate-800 bg-slate-950 p-1 text-xs">
          <button
            type="button"
            className={`flex items-center gap-2 rounded-lg px-4 py-2 font-semibold transition-all ${
              activeTab === 'qa'
                ? 'bg-blue-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
            onClick={() => setActiveTab('qa')}
          >
            <Sparkles className="h-4 w-4" />
            AI Clinical Q&A
          </button>
          <button
            type="button"
            className={`flex items-center gap-2 rounded-lg px-4 py-2 font-semibold transition-all ${
              activeTab === 'docs'
                ? 'bg-blue-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
            onClick={() => setActiveTab('docs')}
          >
            <Database className="h-4 w-4" />
            Protocol Documents ({documents.length})
          </button>
        </div>
      </div>

      {/* 2. TAB 1: AI Clinical Q&A Grounded Assistant */}
      {activeTab === 'qa' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          {/* Query Console (7 cols) */}
          <div className="space-y-4 lg:col-span-7">
            {/* Search Input Box */}
            <div className="rounded-xl border border-slate-800 bg-slate-950/80 p-5 shadow-2xl backdrop-blur-md space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center gap-2">
                  <Search className="h-4 w-4 text-blue-400" />
                  Clinical Query Terminal
                </span>
                <span className="rounded bg-emerald-500/10 px-2 py-0.5 text-[10px] font-bold text-emerald-400 border border-emerald-500/20">
                  Grounded with pgvector
                </span>
              </div>

              <div className="relative">
                <textarea
                  rows={3}
                  className="w-full rounded-lg border border-slate-800 bg-slate-900/90 p-3.5 pr-12 text-xs text-slate-100 placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 font-sans"
                  placeholder="Ask any clinical protocol question (e.g. 'What is our hospital CAP antibiotic regimen?')..."
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                      handleQuerySubmit();
                    }
                  }}
                />
              </div>

              {/* Suggested Medical Queries */}
              <div className="space-y-2">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
                  Suggested Hospital Inquiries:
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {SUGGESTED_QUERIES.map((sq, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => {
                        setQuery(sq);
                        handleQuerySubmit(sq);
                      }}
                      className="rounded-md border border-slate-800 bg-slate-900 px-2.5 py-1 text-[11px] text-slate-300 hover:border-blue-500/50 hover:text-white transition-all text-left"
                    >
                      &bull; {sq}
                    </button>
                  ))}
                </div>
              </div>

              <Button
                className="w-full h-11 bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-semibold hover:from-blue-500 hover:to-indigo-500 shadow-lg shadow-blue-600/20"
                onClick={() => handleQuerySubmit()}
                disabled={!query.trim() || loadingQuery}
              >
                {loadingQuery ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Searching Hospital Vector Index & Formulating Clinical Advice...
                  </>
                ) : (
                  <>
                    <Sparkles className="mr-2 h-4 w-4" />
                    Run Grounded Clinical Search
                  </>
                )}
              </Button>
            </div>

            {/* Answer Display */}
            {ragResult && (
              <div className="rounded-xl border border-slate-800 bg-slate-950/80 p-5 shadow-2xl space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center gap-2">
                    <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                      Grounded Clinical Guidance
                    </h3>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 text-xs text-slate-400 hover:text-slate-200"
                      onClick={() => handleCopy(ragResult.answer)}
                    >
                      {copied ? <Check className="h-3.5 w-3.5 mr-1 text-emerald-400" /> : <Copy className="h-3.5 w-3.5 mr-1" />}
                      {copied ? 'Copied' : 'Copy'}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 text-xs text-slate-400 hover:text-slate-200"
                      onClick={() => window.print()}
                    >
                      <Printer className="h-3.5 w-3.5 mr-1" />
                      Print
                    </Button>
                  </div>
                </div>

                <div className="text-xs text-slate-200 leading-relaxed space-y-3 font-sans whitespace-pre-wrap">
                  {ragResult.answer}
                </div>

                {/* Suggested Follow-Ups */}
                {ragResult.suggestedFollowUps && ragResult.suggestedFollowUps.length > 0 && (
                  <div className="rounded-lg border border-slate-800/80 bg-slate-900/60 p-3 space-y-2 mt-4">
                    <span className="font-bold uppercase tracking-wider text-[10px] text-amber-400 flex items-center gap-1">
                      <HelpCircle className="h-3.5 w-3.5" />
                      Suggested Clinical Follow-Ups
                    </span>
                    <div className="space-y-1">
                      {ragResult.suggestedFollowUps.map((fu, idx) => (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => {
                            setQuery(fu);
                            handleQuerySubmit(fu);
                          }}
                          className="flex items-center gap-1.5 text-left text-xs text-blue-400 hover:text-blue-300 transition-colors w-full"
                        >
                          <ChevronRight className="h-3.5 w-3.5 shrink-0" />
                          <span>{fu}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Retrieved Citations Sidebar (5 cols) */}
          <div className="space-y-4 lg:col-span-5">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-blue-400" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                  Grounded Citation Evidence ({ragResult?.citations.length || 0})
                </h3>
              </div>
              <span className="text-[10px] text-slate-500 font-mono">pgvector Cosine Match</span>
            </div>

            {!ragResult ? (
              <div className="flex h-[360px] flex-col items-center justify-center rounded-xl border border-dashed border-slate-800 bg-slate-900/20 p-8 text-center">
                <BookOpen className="h-10 w-10 text-slate-600 mb-3" />
                <h4 className="text-sm font-semibold text-slate-300">No Query Executed</h4>
                <p className="mt-1 max-w-xs text-xs text-slate-500">
                  Type a clinical question to retrieve vector-matched protocol passages and document citations.
                </p>
              </div>
            ) : ragResult.citations.length === 0 ? (
              <div className="rounded-xl border border-amber-500/20 bg-amber-950/20 p-4 text-xs text-amber-300">
                No specific hospital guidelines matched this query in your vector index. General clinical guidance was formulated.
              </div>
            ) : (
              <div className="space-y-3 max-h-[750px] overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
                {ragResult.citations.map((cit, idx) => {
                  const isExp = expandedCitation === idx;

                  return (
                    <div
                      key={idx}
                      className={`rounded-xl border transition-all p-3.5 ${
                        isExp
                          ? 'border-blue-500/50 bg-blue-950/20 shadow-lg'
                          : 'border-slate-800 bg-slate-950/80 hover:border-slate-700'
                      }`}
                    >
                      <div
                        className="flex items-center justify-between cursor-pointer"
                        onClick={() => setExpandedCitation(isExp ? null : idx)}
                      >
                        <div className="flex items-center gap-2.5 overflow-hidden">
                          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-blue-600/20 text-xs font-bold text-blue-400">
                            #{idx + 1}
                          </span>
                          <div className="overflow-hidden">
                            <p className="truncate text-xs font-semibold text-slate-200">{cit.title}</p>
                            <span className="text-[10px] text-slate-400 font-mono">
                              {cit.documentType.replace('_', ' ')} &bull; Chunk #{cit.chunkIndex}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          {cit.similarityScore !== undefined && (
                            <span className="rounded bg-emerald-500/10 px-1.5 py-0.5 text-[10px] font-bold text-emerald-400 font-mono">
                              {Math.round(cit.similarityScore * 100)}% match
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Excerpt Body */}
                      <div className="mt-2.5 border-t border-slate-800/80 pt-2 text-xs text-slate-300 font-sans leading-relaxed">
                        <p className="italic text-slate-400 text-[11px] mb-1">Passage Excerpt:</p>
                        <p className="bg-slate-900/90 rounded-md p-2.5 border border-slate-800 font-mono text-[11px]">
                          "{cit.excerpt}"
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 3. TAB 2: Hospital Protocols & Document Registry */}
      {activeTab === 'docs' && (
        <div className="space-y-4">
          {/* Top Registry Control Bar */}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between bg-slate-950/80 p-4 rounded-xl border border-slate-800">
            <div className="flex flex-wrap items-center gap-2">
              <div className="relative min-w-[220px]">
                <Search className="absolute left-3 top-2.5 h-3.5 w-3.5 text-slate-500" />
                <input
                  type="text"
                  placeholder="Filter documents..."
                  className="h-9 w-full rounded-lg border border-slate-800 bg-slate-900 pl-9 pr-3 text-xs text-slate-200 placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                  value={searchFilter}
                  onChange={(e) => setSearchFilter(e.target.value)}
                />
              </div>

              {/* Type Filter Pills */}
              <div className="flex items-center gap-1 overflow-x-auto text-xs">
                {(['ALL', 'CLINICAL_PROTOCOL', 'GUIDELINE', 'DRUG_FORMULARY', 'SOP'] as const).map((t) => (
                  <button
                    key={t}
                    onClick={() => setSelectedType(t)}
                    className={`rounded-md px-2.5 py-1 text-[11px] font-medium transition-all ${
                      selectedType === t
                        ? 'bg-blue-600 text-white font-semibold'
                        : 'bg-slate-900 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    {t === 'ALL' ? 'All Types' : t.replace('_', ' ')}
                  </button>
                ))}
              </div>
            </div>

            <Button
              className="bg-blue-600 text-white hover:bg-blue-700 font-semibold shadow-md shrink-0"
              onClick={() => setShowUploadModal(true)}
            >
              <Plus className="h-4 w-4 mr-1.5" />
              Ingest New Protocol
            </Button>
          </div>

          {/* Document Ingestion Modal */}
          {showUploadModal && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm">
              <div className="w-full max-w-lg rounded-2xl border border-slate-800 bg-slate-950 p-6 shadow-2xl space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center gap-2">
                    <Upload className="h-5 w-5 text-blue-400" />
                    <h3 className="text-base font-bold text-slate-100">Ingest Knowledge Document</h3>
                  </div>
                  <button
                    onClick={() => setShowUploadModal(false)}
                    className="text-slate-400 hover:text-slate-200"
                  >
                    &times;
                  </button>
                </div>

                {uploadError && (
                  <div className="rounded-lg border border-red-500/30 bg-red-950/30 p-3 text-xs text-red-300">
                    {uploadError}
                  </div>
                )}

                <form onSubmit={handleUploadSubmit} className="space-y-4 text-xs">
                  {/* Dropzone */}
                  <div
                    {...getRootProps()}
                    className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-6 text-center transition-colors cursor-pointer ${
                      isDragActive
                        ? 'border-blue-500 bg-blue-500/10'
                        : 'border-slate-800 bg-slate-900/50 hover:border-slate-700'
                    }`}
                  >
                    <input {...getInputProps()} />
                    <FileText className="h-8 w-8 text-blue-400 mb-2" />
                    {uploadFile ? (
                      <p className="font-semibold text-slate-200">{uploadFile.name}</p>
                    ) : (
                      <>
                        <p className="font-semibold text-slate-200">Drag & drop PDF, TXT, or MD file here</p>
                        <p className="text-[11px] text-slate-500 mt-1">Hospital SOPs, Guidelines, Dosing charts</p>
                      </>
                    )}
                  </div>

                  <div className="space-y-1">
                    <Label className="text-slate-300">Document Title</Label>
                    <input
                      type="text"
                      className="w-full rounded-lg border border-slate-800 bg-slate-900 p-2.5 text-xs text-slate-200 focus:border-blue-500 focus:outline-none"
                      placeholder="e.g. Hospital Community-Acquired Pneumonia Protocol 2026"
                      value={docTitle}
                      onChange={(e) => setDocTitle(e.target.value)}
                      required
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <Label className="text-slate-300">Category</Label>
                      <select
                        className="w-full rounded-lg border border-slate-800 bg-slate-900 p-2.5 text-xs text-slate-200 focus:border-blue-500 focus:outline-none"
                        value={docType}
                        onChange={(e) => setDocType(e.target.value as DocumentType)}
                      >
                        <option value="CLINICAL_PROTOCOL">Clinical Protocol</option>
                        <option value="GUIDELINE">Practice Guideline</option>
                        <option value="DRUG_FORMULARY">Drug Formulary</option>
                        <option value="SOP">Hospital SOP</option>
                        <option value="JOURNAL">Medical Journal</option>
                      </select>
                    </div>

                    <div className="space-y-1">
                      <Label className="text-slate-300">Source / Author</Label>
                      <input
                        type="text"
                        className="w-full rounded-lg border border-slate-800 bg-slate-900 p-2.5 text-xs text-slate-200 focus:border-blue-500 focus:outline-none"
                        placeholder="e.g. Dept of Infectious Diseases"
                        value={docSource}
                        onChange={(e) => setDocSource(e.target.value)}
                      />
                    </div>
                  </div>

                  <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-800">
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => setShowUploadModal(false)}
                      disabled={uploading}
                    >
                      Cancel
                    </Button>
                    <Button
                      type="submit"
                      className="bg-blue-600 text-white hover:bg-blue-700"
                      disabled={!uploadFile || uploading}
                    >
                      {uploading ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Indexing Vectors...
                        </>
                      ) : (
                        'Upload & Index Document'
                      )}
                    </Button>
                  </div>
                </form>
              </div>
            </div>
          )}

          {/* Document Table */}
          <div className="rounded-xl border border-slate-800 bg-slate-950 overflow-hidden shadow-xl">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-[10px] font-semibold text-slate-400 bg-slate-900/60 uppercase tracking-wider">
                  <th className="p-3.5">Document Title</th>
                  <th className="p-3.5">Category</th>
                  <th className="p-3.5">Vector Chunks</th>
                  <th className="p-3.5">Size</th>
                  <th className="p-3.5">Status</th>
                  <th className="p-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {loadingDocs ? (
                  <tr>
                    <td colSpan={6} className="p-8 text-center text-slate-400">
                      <Loader2 className="h-6 w-6 animate-spin mx-auto mb-2 text-blue-500" />
                      Loading indexed documents...
                    </td>
                  </tr>
                ) : filteredDocs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="p-8 text-center text-slate-500">
                      No documents found in knowledge base. Ingest hospital guidelines using the button above.
                    </td>
                  </tr>
                ) : (
                  filteredDocs.map((doc) => {
                    const typeCfg = DOC_TYPE_CONFIG[doc.documentType] || DOC_TYPE_CONFIG.GUIDELINE;

                    return (
                      <tr key={doc.id} className="hover:bg-slate-900/40 transition-colors">
                        <td className="p-3.5">
                          <p className="font-semibold text-slate-200">{doc.title}</p>
                          <p className="text-[10px] text-slate-500 font-mono">{doc.fileName} {doc.source && `\u2022 ${doc.source}`}</p>
                        </td>
                        <td className="p-3.5">
                          <span className={`inline-flex rounded px-2 py-0.5 text-[10px] font-semibold border ${typeCfg.badge}`}>
                            {typeCfg.label}
                          </span>
                        </td>
                        <td className="p-3.5 font-mono text-slate-300">
                          <span className="flex items-center gap-1">
                            <Layers className="h-3.5 w-3.5 text-blue-400" />
                            {doc.totalChunks} Chunks
                          </span>
                        </td>
                        <td className="p-3.5 text-slate-400 font-mono text-[11px]">
                          {doc.fileSizeBytes ? `${Math.round(doc.fileSizeBytes / 1024)} KB` : 'N/A'}
                        </td>
                        <td className="p-3.5">
                          <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${
                            doc.status === 'READY'
                              ? 'bg-emerald-500/10 text-emerald-400'
                              : doc.status === 'FAILED'
                              ? 'bg-red-500/10 text-red-400'
                              : 'bg-amber-500/10 text-amber-400'
                          }`}>
                            <span className={`h-1.5 w-1.5 rounded-full ${
                              doc.status === 'READY' ? 'bg-emerald-500' : doc.status === 'FAILED' ? 'bg-red-500' : 'bg-amber-500 animate-ping'
                            }`} />
                            {doc.status}
                          </span>
                        </td>
                        <td className="p-3.5 text-right">
                          <Button
                            size="sm"
                            variant="ghost"
                            className="h-7 w-7 p-0 text-slate-400 hover:text-red-400 hover:bg-red-950/30"
                            onClick={() => handleDeleteDoc(doc.id)}
                            title="Delete Document"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
