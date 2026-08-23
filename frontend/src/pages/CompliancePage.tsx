import { useState, useEffect } from 'react';
import {
  Shield, CheckCircle2, XCircle, Lock, Eye, Trash2,
  Plus, UserCheck, Sparkles, Sliders
} from 'lucide-react';
import { complianceService, PatientConsent, ConsentRequest, DataRetentionPolicy, RetentionPurgeLog, RedactionResult } from '@/services/complianceService';
import { patientService } from '@/services/patientService';
import { Patient } from '@/types';

export function CompliancePage() {
  const [activeTab, setActiveTab] = useState<'consents' | 'phi' | 'retention'>('consents');

  // Consents State
  const [consents, setConsents] = useState<PatientConsent[]>([]);
  const [patients, setPatients] = useState<Patient[]>([]);
  const [, setLoadingConsents] = useState(false);
  const [showConsentModal, setShowConsentModal] = useState(false);
  const [consentForm, setConsentForm] = useState<ConsentRequest>({
    patientId: '',
    purpose: 'AI_ANALYSIS',
    signerName: '',
    signerRelationship: 'PATIENT',
    notes: '',
  });

  // PHI Sandbox State
  const [sampleText, setSampleText] = useState(
    "Patient Johnathan Doe (MRN-847291, DOB 05/14/1982) was seen by Dr. Emily Vance at 1042 Medical Center Way, Seattle WA 98101. Phone: (206) 555-0193, email: jdoe@example.com. Pt presents with persistent cough; chest CT requested from IP 192.168.1.104."
  );
  const [redactionResult, setRedactionResult] = useState<RedactionResult | null>(null);
  const [redacting, setRedacting] = useState(false);

  // Retention State
  const [retentionPolicy, setRetentionPolicy] = useState<DataRetentionPolicy>({
    auditLogRetentionDays: 365,
    analysisRetentionDays: 730,
    chatSessionRetentionDays: 180,
    softDeletePurgeDays: 30,
    autoPurgeEnabled: false,
  });
  const [purgeLogs, setPurgeLogs] = useState<RetentionPurgeLog[]>([]);
  const [savingPolicy, setSavingPolicy] = useState(false);
  const [purging, setPurging] = useState(false);
  const [purgeMessage, setPurgeMessage] = useState<string | null>(null);

  useEffect(() => {
    loadConsents();
    loadPatients();
    loadRetentionData();
  }, []);

  const loadConsents = async () => {
    setLoadingConsents(true);
    try {
      const data = await complianceService.getAllConsents();
      setConsents(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingConsents(false);
    }
  };

  const loadPatients = async () => {
    try {
      const data = await patientService.list(0, 100);
      setPatients(data.content);
    } catch (err) {
      console.error(err);
    }
  };

  const loadRetentionData = async () => {
    try {
      const [policy, logs] = await Promise.all([
        complianceService.getRetentionPolicy(),
        complianceService.getPurgeLogs(),
      ]);
      if (policy) setRetentionPolicy(policy);
      if (logs) setPurgeLogs(logs);
    } catch (err) {
      console.error(err);
    }
  };

  const handleGrantConsent = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!consentForm.patientId || !consentForm.signerName) return;
    try {
      await complianceService.grantConsent(consentForm);
      setShowConsentModal(false);
      setConsentForm({
        patientId: '',
        purpose: 'AI_ANALYSIS',
        signerName: '',
        signerRelationship: 'PATIENT',
        notes: '',
      });
      loadConsents();
    } catch (err) {
      alert('Failed to grant consent. Please verify parameters.');
    }
  };

  const handleRevokeConsent = async (consentId: string) => {
    const reason = prompt('Please specify a clinical/legal reason for revoking consent:');
    if (reason === null) return;
    try {
      await complianceService.revokeConsent(consentId, reason);
      loadConsents();
    } catch (err) {
      alert('Failed to revoke consent.');
    }
  };

  const handleRunRedaction = async () => {
    setRedacting(true);
    try {
      const res = await complianceService.testRedaction(sampleText);
      setRedactionResult(res);
    } catch (err) {
      console.error(err);
    } finally {
      setRedacting(false);
    }
  };

  const handleSavePolicy = async () => {
    setSavingPolicy(true);
    try {
      const updated = await complianceService.updateRetentionPolicy(retentionPolicy);
      setRetentionPolicy(updated);
      alert('Data retention policy updated successfully.');
    } catch (err) {
      alert('Failed to update retention policy.');
    } finally {
      setSavingPolicy(false);
    }
  };

  const handleExecutePurge = async () => {
    if (!confirm('Are you sure you want to execute a compliance data purge according to the active retention periods?')) return;
    setPurging(true);
    setPurgeMessage(null);
    try {
      const res = await complianceService.executePurge();
      setPurgeMessage(`Purge completed: ${res.auditLogsPurged} audit logs, ${res.chatMessagesPurged} chat messages removed.`);
      loadRetentionData();
    } catch (err) {
      alert('Purge operation failed.');
    } finally {
      setPurging(false);
    }
  };

  return (
    <div className="p-6 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-900/60 p-6 rounded-2xl border border-slate-800 backdrop-blur-xl">
        <div>
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20">
              <Shield className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-white tracking-tight">Compliance & Privacy Governance</h1>
              <p className="text-sm text-slate-400">HIPAA Safe Harbor, GDPR/DPDP Patient Consent & Data Retention</p>
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex items-center gap-1 bg-slate-950/80 p-1.5 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('consents')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
              activeTab === 'consents' ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/20' : 'text-slate-400 hover:text-white'
            }`}
          >
            <UserCheck className="w-4 h-4" />
            Patient Consents
          </button>
          <button
            onClick={() => setActiveTab('phi')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
              activeTab === 'phi' ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/20' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Eye className="w-4 h-4" />
            PHI Redaction Sandbox
          </button>
          <button
            onClick={() => setActiveTab('retention')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
              activeTab === 'retention' ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/20' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Sliders className="w-4 h-4" />
            Data Retention
          </button>
        </div>
      </div>

      {/* ── 1. PATIENT CONSENTS TAB ── */}
      {activeTab === 'consents' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-white">Patient AI & Data Processing Consents</h2>
              <p className="text-xs text-slate-400">Enforce patient permission before AI inference or model training</p>
            </div>
            <button
              onClick={() => setShowConsentModal(true)}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium shadow-lg shadow-blue-500/20 transition-all"
            >
              <Plus className="w-4 h-4" />
              Record New Consent
            </button>
          </div>

          {/* Consents Table */}
          <div className="bg-slate-900/60 rounded-2xl border border-slate-800 overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-950/60 text-slate-400 uppercase text-xs border-b border-slate-800">
                <tr>
                  <th className="px-6 py-4">Patient Name</th>
                  <th className="px-6 py-4">Purpose</th>
                  <th className="px-6 py-4">Status</th>
                  <th className="px-6 py-4">Signer / Legal Relation</th>
                  <th className="px-6 py-4">Granted Date</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {consents.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                      No consent records recorded yet. Click "Record New Consent" above.
                    </td>
                  </tr>
                ) : (
                  consents.map((c) => (
                    <tr key={c.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-6 py-4 font-medium text-white">{c.patientName}</td>
                      <td className="px-6 py-4">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-200 border border-slate-700">
                          {c.purpose}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        {c.status === 'GRANTED' ? (
                          <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Granted
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs font-semibold text-rose-400 bg-rose-500/10 px-2.5 py-1 rounded-full border border-rose-500/20">
                            <XCircle className="w-3.5 h-3.5" /> Revoked
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-white">{c.signerName}</span>
                        <span className="text-xs text-slate-500 block">({c.signerRelationship})</span>
                      </td>
                      <td className="px-6 py-4 text-slate-400 text-xs">
                        {new Date(c.grantedAt).toLocaleDateString()}
                      </td>
                      <td className="px-6 py-4 text-right">
                        {c.status === 'GRANTED' && (
                          <button
                            onClick={() => handleRevokeConsent(c.id)}
                            className="px-3 py-1 text-xs text-rose-400 hover:bg-rose-500/10 border border-rose-500/20 rounded-lg transition-colors"
                          >
                            Revoke
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── 2. PHI REDACTION SANDBOX TAB ── */}
      {activeTab === 'phi' && (
        <div className="space-y-6">
          <div className="bg-slate-900/60 p-6 rounded-2xl border border-slate-800 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold text-white flex items-center gap-2">
                  <Lock className="w-5 h-5 text-blue-400" />
                  HIPAA 18 Safe Harbor PHI De-Identification Engine
                </h2>
                <p className="text-xs text-slate-400">
                  Scans names, MRNs, SSNs, phone numbers, emails, addresses, dates, and IP addresses prior to LLM inference.
                </p>
              </div>
              <button
                onClick={handleRunRedaction}
                disabled={redacting}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-sm font-medium shadow-lg shadow-blue-500/20 transition-all"
              >
                <Sparkles className="w-4 h-4" />
                {redacting ? 'Scanning PHI...' : 'Run Redaction'}
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4">
              {/* Input text */}
              <div className="space-y-2">
                <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Raw Clinical Text (with Protected Health Information)
                </label>
                <textarea
                  rows={8}
                  value={sampleText}
                  onChange={(e) => setSampleText(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-sm text-slate-200 focus:outline-none focus:border-blue-500 transition-colors font-mono"
                />
              </div>

              {/* Output text */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Scrubbed Output (Safe for External AI Ingestion)
                  </label>
                  {redactionResult && (
                    <span className="text-xs font-semibold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                      {redactionResult.totalRedactionsCount} Entities Scrubbed
                    </span>
                  )}
                </div>
                <div className="w-full min-h-[190px] bg-slate-950 border border-slate-800 rounded-xl p-4 text-sm text-emerald-300/90 font-mono whitespace-pre-wrap">
                  {redactionResult ? redactionResult.redactedText : <span className="text-slate-600">Click 'Run Redaction' to test HIPAA Safe Harbor tokenization...</span>}
                </div>
              </div>
            </div>

            {/* Redaction Entity Breakdown */}
            {redactionResult && (
              <div className="pt-4 border-t border-slate-800/80">
                <h4 className="text-xs font-semibold text-slate-400 uppercase mb-3">Redacted Entity Breakdown</h4>
                <div className="flex flex-wrap gap-2">
                  {Object.entries(redactionResult.redactionsByType).map(([type, count]) => (
                    <span key={type} className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-xs text-white">
                      <span className="font-semibold text-blue-400">{type}</span>
                      <span className="bg-blue-500/20 text-blue-300 px-1.5 py-0.5 rounded font-mono">{count}</span>
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── 3. DATA RETENTION TAB ── */}
      {activeTab === 'retention' && (
        <div className="space-y-6">
          <div className="bg-slate-900/60 p-6 rounded-2xl border border-slate-800 space-y-6">
            <div>
              <h2 className="text-lg font-semibold text-white">Automated Data Retention & Purge Rules</h2>
              <p className="text-xs text-slate-400">Configure data lifecycle policies for audit logs, patient chats, and soft-deleted records</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Audit Log Retention (Days)</label>
                <input
                  type="number"
                  value={retentionPolicy.auditLogRetentionDays}
                  onChange={(e) => setRetentionPolicy({ ...retentionPolicy, auditLogRetentionDays: parseInt(e.target.value) || 0 })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Chat Session Retention (Days)</label>
                <input
                  type="number"
                  value={retentionPolicy.chatSessionRetentionDays}
                  onChange={(e) => setRetentionPolicy({ ...retentionPolicy, chatSessionRetentionDays: parseInt(e.target.value) || 0 })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Diagnostic Analysis Retention (Days)</label>
                <input
                  type="number"
                  value={retentionPolicy.analysisRetentionDays}
                  onChange={(e) => setRetentionPolicy({ ...retentionPolicy, analysisRetentionDays: parseInt(e.target.value) || 0 })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Soft-Delete Final Purge (Days)</label>
                <input
                  type="number"
                  value={retentionPolicy.softDeletePurgeDays}
                  onChange={(e) => setRetentionPolicy({ ...retentionPolicy, softDeletePurgeDays: parseInt(e.target.value) || 0 })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>

            <div className="flex items-center justify-between pt-4 border-t border-slate-800">
              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={retentionPolicy.autoPurgeEnabled}
                  onChange={(e) => setRetentionPolicy({ ...retentionPolicy, autoPurgeEnabled: e.target.checked })}
                  className="w-4 h-4 rounded text-blue-600 bg-slate-950 border-slate-800 focus:ring-0"
                />
                <span className="text-sm font-medium text-slate-200">Enable Daily Automated Nightly Purge (2:00 AM)</span>
              </label>

              <div className="flex items-center gap-3">
                <button
                  onClick={handleExecutePurge}
                  disabled={purging}
                  className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-rose-600/20 text-rose-300 hover:bg-rose-600/30 border border-rose-500/30 text-sm font-medium transition-all"
                >
                  <Trash2 className="w-4 h-4" />
                  {purging ? 'Purging...' : 'Execute Manual Purge'}
                </button>

                <button
                  onClick={handleSavePolicy}
                  disabled={savingPolicy}
                  className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium shadow-lg shadow-blue-500/20 transition-all"
                >
                  {savingPolicy ? 'Saving...' : 'Save Policy'}
                </button>
              </div>
            </div>

            {purgeMessage && (
              <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm">
                {purgeMessage}
              </div>
            )}
          </div>

          {/* Purge Logs */}
          <div className="bg-slate-900/60 p-6 rounded-2xl border border-slate-800 space-y-4">
            <h3 className="text-base font-semibold text-white">Retention Execution Audit Logs</h3>
            <div className="overflow-hidden rounded-xl border border-slate-800">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-950/60 text-slate-400 uppercase text-xs border-b border-slate-800">
                  <tr>
                    <th className="px-6 py-3">Execution Time</th>
                    <th className="px-6 py-3">Entity Type</th>
                    <th className="px-6 py-3">Records Purged</th>
                    <th className="px-6 py-3">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800 text-slate-300">
                  {purgeLogs.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="px-6 py-6 text-center text-slate-500">
                        No purge runs recorded yet.
                      </td>
                    </tr>
                  ) : (
                    purgeLogs.map((l) => (
                      <tr key={l.id}>
                        <td className="px-6 py-3 text-xs text-slate-400">{new Date(l.executedAt).toLocaleString()}</td>
                        <td className="px-6 py-3 font-mono text-xs">{l.entityType}</td>
                        <td className="px-6 py-3 font-semibold text-white">{l.recordsPurgedCount}</td>
                        <td className="px-6 py-3">
                          <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            {l.status}
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ── RECORD CONSENT MODAL ── */}
      {showConsentModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg p-6 space-y-6 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <UserCheck className="w-5 h-5 text-blue-400" />
                Record Patient Consent
              </h3>
              <button onClick={() => setShowConsentModal(false)} className="text-slate-400 hover:text-white">
                ✕
              </button>
            </div>

            <form onSubmit={handleGrantConsent} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Select Patient *</label>
                <select
                  required
                  value={consentForm.patientId}
                  onChange={(e) => setConsentForm({ ...consentForm, patientId: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                >
                  <option value="">-- Choose Patient --</option>
                  {patients.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.firstName} {p.lastName} ({p.medicalRecordNumber})
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Consent Purpose *</label>
                <select
                  value={consentForm.purpose}
                  onChange={(e) => setConsentForm({ ...consentForm, purpose: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                >
                  <option value="AI_ANALYSIS">AI_ANALYSIS (Diagnostic Image & Blood Interpretation)</option>
                  <option value="MODEL_TRAINING">MODEL_TRAINING (De-identified Fine-Tuning & Adapter Optimization)</option>
                  <option value="RESEARCH_USE">RESEARCH_USE (Clinical Protocol Evaluation & Cohort Studies)</option>
                  <option value="DATA_SHARING">DATA_SHARING (Multi-Hospital Consultation & PACS Ingestion)</option>
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-300">Signer Full Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. John Doe"
                    value={consentForm.signerName}
                    onChange={(e) => setConsentForm({ ...consentForm, signerName: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-300">Legal Relationship</label>
                  <select
                    value={consentForm.signerRelationship}
                    onChange={(e) => setConsentForm({ ...consentForm, signerRelationship: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  >
                    <option value="PATIENT">Patient</option>
                    <option value="GUARDIAN">Parent / Legal Guardian</option>
                    <option value="POWER_OF_ATTORNEY">Medical Power of Attorney</option>
                  </select>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Clinical / Legal Notes</label>
                <textarea
                  rows={2}
                  placeholder="Optional consent details or signed paperwork reference"
                  value={consentForm.notes}
                  onChange={(e) => setConsentForm({ ...consentForm, notes: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowConsentModal(false)}
                  className="px-4 py-2 rounded-xl text-sm text-slate-400 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium shadow-lg shadow-blue-500/20 transition-all"
                >
                  Record Consent
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
