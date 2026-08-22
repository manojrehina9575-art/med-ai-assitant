import { useState, useEffect, useCallback } from 'react';
import { patientService } from '@/services/patientService';
import {
  agentService,
  type AgentWorkflow,
  type AgentWorkflowStep,
  type ToolDefinition,
} from '@/services/agentService';
import { Button } from '@/components/ui/Button';
import { Label } from '@/components/ui/Label';
import {
  Sparkles,
  Bot,
  Calendar,
  Pill,
  FlaskConical,
  FileText,
  Bell,
  Search,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  ShieldCheck,
  Wand2,
  Check,
  X,
  Loader2,
  Copy,
  History,
} from 'lucide-react';
import type { Patient } from '@/types';

const PRESET_PROTOCOLS = [
  {
    title: 'Discharge Protocol',
    icon: FileText,
    color: '#3b82f6',
    goal: 'Discharge patient with Amoxicillin 500mg PO TID for 7 days, schedule outpatient follow-up in 7 days, and dispatch discharge care instructions.',
    badge: 'Discharge',
  },
  {
    title: 'Pre-Op Clearance',
    icon: FlaskConical,
    color: '#8b5cf6',
    goal: 'Order pre-operative lab panel (CBC with Diff, CMP, PT/INR, Type & Screen), check patient medical history, and schedule pre-anesthesia consult in 3 days.',
    badge: 'Pre-Op',
  },
  {
    title: 'Post-Op Follow-up',
    icon: Calendar,
    color: '#10b981',
    goal: 'Schedule surgical wound check in 10 days, prescribe Ibuprofen 600mg PRN for 5 days, and notify care team.',
    badge: 'Post-Op',
  },
  {
    title: 'Acute Lab Triage',
    icon: AlertTriangle,
    color: '#f59e0b',
    goal: 'Order STAT diagnostic labs (CBC, Comprehensive Metabolic Panel, Troponin I, CRP) and alert clinical care team.',
    badge: 'STAT Triage',
  },
];

const TOOL_ICONS: Record<string, React.ElementType> = {
  scheduleAppointment: Calendar,
  writePrescription: Pill,
  orderLabTest: FlaskConical,
  generateDischargeSummary: FileText,
  sendNotification: Bell,
  searchPatientHistory: Search,
};

export function WorkflowsPage() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [selectedPatientId, setSelectedPatientId] = useState<string>('');
  const [goal, setGoal] = useState('');
  const [tools, setTools] = useState<ToolDefinition[]>([]);
  const [activeWorkflow, setActiveWorkflow] = useState<AgentWorkflow | null>(null);
  const [recentWorkflows, setRecentWorkflows] = useState<AgentWorkflow[]>([]);
  const [starting, setStarting] = useState(false);
  const [confirmingStepId, setConfirmingStepId] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    patientService.list(0, 100).then((res) => setPatients(res.content)).catch(() => {});
    agentService.listTools().then(setTools).catch(() => {});
  }, []);

  const loadWorkflows = useCallback(async () => {
    try {
      const res = await agentService.listWorkflows(selectedPatientId || undefined, 0, 10);
      setRecentWorkflows(res.content);
      if (res.content.length > 0 && !activeWorkflow) {
        setActiveWorkflow(res.content[0]);
      }
    } catch {
      // silent
    }
  }, [selectedPatientId, activeWorkflow]);

  useEffect(() => {
    loadWorkflows();
  }, [loadWorkflows]);

  const handleStartWorkflow = async (customGoal?: string) => {
    const goalToRun = customGoal || goal;
    if (!goalToRun.trim()) return;

    setStarting(true);
    try {
      const wf = await agentService.startWorkflow(goalToRun.trim(), selectedPatientId || undefined);
      setActiveWorkflow(wf);
      setGoal('');
      loadWorkflows();
    } catch (err) {
      console.error('Failed to start workflow:', err);
    } finally {
      setStarting(false);
    }
  };

  const handleConfirmStep = async (stepId: string, approved: boolean) => {
    if (!activeWorkflow) return;
    setConfirmingStepId(stepId);
    try {
      const updated = await agentService.confirmStep(activeWorkflow.id, stepId, approved);
      setActiveWorkflow(updated);
      loadWorkflows();
    } catch (err) {
      console.error('Failed to confirm step:', err);
    } finally {
      setConfirmingStepId(null);
    }
  };

  const getStepStatusBadge = (step: AgentWorkflowStep) => {
    if (step.confirmationStatus === 'PENDING') {
      return <span className="badge badge-amber text-[10px] animate-pulse">Doctor Approval Required</span>;
    }
    if (step.status === 'COMPLETED') {
      return <span className="badge badge-green text-[10px]">Completed</span>;
    }
    if (step.status === 'SKIPPED') {
      return <span className="badge badge-slate text-[10px]">Rejected / Skipped</span>;
    }
    if (step.status === 'FAILED') {
      return <span className="badge badge-red text-[10px]">Failed</span>;
    }
    return <span className="badge badge-blue text-[10px]">Executing</span>;
  };

  const copyReport = () => {
    if (!activeWorkflow?.finalOutput) return;
    navigator.clipboard.writeText(activeWorkflow.finalOutput);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6 max-w-[1400px]">
      {/* ── Page Header ── */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <div
              className="flex h-9 w-9 items-center justify-center rounded-xl"
              style={{
                background: 'linear-gradient(135deg, #8b5cf6, #3b82f6)',
                boxShadow: '0 0 20px rgba(139,92,246,0.35)',
              }}
            >
              <Wand2 className="h-5 w-5 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>
                Clinical Agent & LangGraph4j Workflows
              </h1>
              <p className="text-xs mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
                Goal-oriented autonomous clinical action execution with Human-in-the-Loop confirmations
              </p>
            </div>
          </div>
        </div>

        {/* Patient selector */}
        <div className="flex items-center gap-3">
          <select
            className="input-field"
            value={selectedPatientId}
            onChange={(e) => setSelectedPatientId(e.target.value)}
            style={{
              height: 38,
              minWidth: 260,
              background: 'var(--surface, #111827)',
              border: '1px solid var(--clr-border, #1e2d45)',
              color: 'var(--clr-text, #f1f5f9)',
              borderRadius: 8,
              paddingLeft: 12,
              fontSize: 13,
            }}
          >
            <option value="">Select Patient for Context...</option>
            {patients.map((p) => (
              <option key={p.id} value={p.id}>
                {p.fullName} (MRN: {p.medicalRecordNumber})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* ── Preset Protocols Row ── */}
      <div>
        <p className="section-label mb-2.5">Preset Clinical Protocols</p>
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {PRESET_PROTOCOLS.map((proto) => {
            const Icon = proto.icon;
            return (
              <div
                key={proto.title}
                onClick={() => {
                  setGoal(proto.goal);
                  handleStartWorkflow(proto.goal);
                }}
                className="rounded-xl p-4 cursor-pointer transition-all duration-200 group"
                style={{
                  background: 'var(--surface, #111827)',
                  border: '1px solid var(--clr-border, #1e2d45)',
                }}
                onMouseEnter={(e) => {
                  const el = e.currentTarget;
                  el.style.borderColor = proto.color + '60';
                  el.style.transform = 'translateY(-1px)';
                  el.style.boxShadow = `0 8px 24px ${proto.color}15`;
                }}
                onMouseLeave={(e) => {
                  const el = e.currentTarget;
                  el.style.borderColor = 'var(--clr-border, #1e2d45)';
                  el.style.transform = '';
                  el.style.boxShadow = '';
                }}
              >
                <div className="flex items-center justify-between mb-2">
                  <div
                    className="flex h-8 w-8 items-center justify-center rounded-lg"
                    style={{ background: `${proto.color}18` }}
                  >
                    <Icon className="h-4 w-4" style={{ color: proto.color }} />
                  </div>
                  <span className="badge badge-slate text-[10px]">{proto.badge}</span>
                </div>
                <p className="text-sm font-semibold text-white group-hover:text-blue-300 transition-colors">
                  {proto.title}
                </p>
                <p className="text-xs mt-1 line-clamp-2" style={{ color: 'var(--clr-text-3)' }}>
                  {proto.goal}
                </p>
                <div className="flex items-center gap-1 mt-3 text-xs font-semibold" style={{ color: proto.color }}>
                  <span>Execute Protocol</span>
                  <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* ── Custom Goal Prompt Bar ── */}
      <div
        className="rounded-2xl p-5"
        style={{
          background: 'var(--surface, #111827)',
          border: '1px solid var(--clr-border, #1e2d45)',
        }}
      >
        <Label>Clinical Objective / Goal</Label>
        <div className="flex gap-2 mt-1.5">
          <input
            type="text"
            className="input-field flex-1"
            placeholder="e.g., Discharge patient with amoxicillin 500mg TID for 7 days, schedule follow-up in 1 week, and order post-discharge CBC panel..."
            value={goal}
            onChange={(e) => setGoal(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleStartWorkflow()}
          />
          <Button
            onClick={() => handleStartWorkflow()}
            disabled={starting || !goal.trim()}
            className="px-5"
          >
            {starting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Planning with LangGraph4j...
              </>
            ) : (
              <>
                <Sparkles className="h-4 w-4" />
                Run Agent
              </>
            )}
          </Button>
        </div>
      </div>

      {/* ── Active Workflow Execution Viewer ── */}
      {activeWorkflow && (
        <div className="grid lg:grid-cols-3 gap-5 animate-in">
          {/* Left: Step-by-Step Interactive Timeline (2 cols) */}
          <div
            className="lg:col-span-2 rounded-2xl p-6 space-y-6"
            style={{
              background: 'var(--surface, #111827)',
              border: '1px solid var(--clr-border, #1e2d45)',
            }}
          >
            {/* Workflow Header */}
            <div className="flex items-start justify-between gap-4 pb-4 border-b border-slate-800">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span
                    className={`badge ${
                      activeWorkflow.status === 'COMPLETED'
                        ? 'badge-green'
                        : activeWorkflow.status === 'AWAITING_APPROVAL'
                        ? 'badge-amber'
                        : activeWorkflow.status === 'FAILED'
                        ? 'badge-red'
                        : 'badge-blue'
                    }`}
                  >
                    {activeWorkflow.status.replace(/_/g, ' ')}
                  </span>
                  {activeWorkflow.patientName && (
                    <span className="text-xs text-slate-400">
                      Patient: <strong className="text-white">{activeWorkflow.patientName}</strong> (MRN: {activeWorkflow.patientMrn})
                    </span>
                  )}
                </div>
                <h3 className="text-base font-bold text-white mt-1" style={{ fontFamily: 'Plus Jakarta Sans' }}>
                  {activeWorkflow.goal}
                </h3>
              </div>
              <span className="text-xs font-mono text-slate-500 shrink-0">
                {new Date(activeWorkflow.createdAt).toLocaleTimeString()}
              </span>
            </div>

            {/* Steps Timeline */}
            <div className="space-y-4">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">
                LangGraph4j Execution Plan ({activeWorkflow.steps?.length || 0} Actions)
              </p>

              {activeWorkflow.steps?.map((step) => {
                const ToolIcon = TOOL_ICONS[step.toolName] || Bot;
                const isPendingApproval = step.confirmationStatus === 'PENDING';
                const isExecutingThis = confirmingStepId === step.id;

                return (
                  <div
                    key={step.id}
                    className={`rounded-xl p-4 transition-all ${
                      isPendingApproval
                        ? 'bg-amber-950/20 border-amber-500/40 shadow-lg shadow-amber-950/30'
                        : 'bg-slate-900/60 border-slate-800'
                    }`}
                    style={{ border: `1px solid ${isPendingApproval ? '#f59e0b60' : 'var(--clr-border, #1e2d45)'}` }}
                  >
                    <div className="flex items-start justify-between gap-3 mb-2">
                      <div className="flex items-center gap-3">
                        <div
                          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-white"
                          style={{
                            background: isPendingApproval
                              ? '#f59e0b25'
                              : step.status === 'COMPLETED'
                              ? '#10b98125'
                              : '#3b82f625',
                            color: isPendingApproval
                              ? '#fbbf24'
                              : step.status === 'COMPLETED'
                              ? '#34d399'
                              : '#60a5fa',
                          }}
                        >
                          <ToolIcon className="h-4 w-4" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-mono font-bold text-slate-400">
                              Step {step.stepIndex}
                            </span>
                            <span className="text-xs font-bold text-white">`{step.toolName}`</span>
                          </div>
                          <p className="text-xs text-slate-300 mt-0.5">{step.actionSummary}</p>
                        </div>
                      </div>
                      {getStepStatusBadge(step)}
                    </div>

                    {/* Step Parameters Display */}
                    {step.inputPayload && (
                      <div className="mt-3 rounded-lg bg-slate-950/70 p-3 text-xs font-mono text-slate-300 space-y-1">
                        {Object.entries(step.inputPayload).map(([k, v]) => (
                          <div key={k} className="flex items-start gap-2">
                            <span className="text-slate-500 font-semibold">{k}:</span>
                            <span className="text-slate-200 break-all">
                              {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Output / Result Data */}
                    {step.outputPayload && Object.keys(step.outputPayload).length > 0 && (
                      <div className="mt-2 text-xs text-emerald-400 flex items-center gap-1.5 font-mono">
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        <span>Action Persisted & Recorded</span>
                      </div>
                    )}

                    {/* Human-in-the-Loop Confirmation Bar */}
                    {isPendingApproval && (
                      <div className="mt-4 pt-3 border-t border-amber-500/20 flex items-center justify-between flex-wrap gap-3">
                        <div className="flex items-center gap-2 text-xs text-amber-300 font-medium">
                          <ShieldCheck className="h-4 w-4" />
                          <span>Practitioner confirmation required to commit to medical record</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <Button
                            size="sm"
                            variant="destructive"
                            disabled={isExecutingThis}
                            onClick={() => handleConfirmStep(step.id, false)}
                          >
                            <X className="h-3.5 w-3.5" /> Reject
                          </Button>
                          <Button
                            size="sm"
                            disabled={isExecutingThis}
                            onClick={() => handleConfirmStep(step.id, true)}
                            style={{ background: 'linear-gradient(135deg, #10b981, #059669)' }}
                          >
                            {isExecutingThis ? (
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            ) : (
                              <Check className="h-3.5 w-3.5" />
                            )}
                            Approve Action
                          </Button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Right: Synthesis Summary & Registered Tools (1 col) */}
          <div className="space-y-5">
            {/* Synthesis Card */}
            {activeWorkflow.finalOutput && (
              <div
                className="rounded-2xl p-5"
                style={{
                  background: 'var(--surface, #111827)',
                  border: '1px solid var(--clr-border, #1e2d45)',
                }}
              >
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-bold text-white flex items-center gap-2" style={{ fontFamily: 'Plus Jakarta Sans' }}>
                    <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                    Clinical Execution Summary
                  </h3>
                  <button
                    onClick={copyReport}
                    className="text-xs text-slate-400 hover:text-white flex items-center gap-1"
                  >
                    {copied ? <Check className="h-3 w-3 text-emerald-400" /> : <Copy className="h-3 w-3" />}
                    {copied ? 'Copied' : 'Copy'}
                  </button>
                </div>
                <div className="text-xs text-slate-300 leading-relaxed whitespace-pre-wrap rounded-xl bg-slate-950/60 p-3.5 font-sans">
                  {activeWorkflow.finalOutput}
                </div>
              </div>
            )}

            {/* Recent Workflows */}
            {recentWorkflows.length > 0 && (
              <div
                className="rounded-2xl p-5"
                style={{
                  background: 'var(--surface, #111827)',
                  border: '1px solid var(--clr-border, #1e2d45)',
                }}
              >
                <h3 className="text-sm font-bold text-white mb-3 flex items-center gap-2" style={{ fontFamily: 'Plus Jakarta Sans' }}>
                  <History className="h-4 w-4 text-blue-400" />
                  Recent Workflows
                </h3>
                <div className="space-y-1.5 max-h-[220px] overflow-y-auto pr-1">
                  {recentWorkflows.map((wf) => (
                    <div
                      key={wf.id}
                      onClick={() => setActiveWorkflow(wf)}
                      className={`rounded-xl p-2.5 text-xs cursor-pointer transition-all ${
                        activeWorkflow.id === wf.id
                          ? 'bg-blue-600/20 border-blue-500/40 text-white'
                          : 'bg-slate-900/40 border-slate-800 text-slate-300 hover:bg-slate-900'
                      }`}
                      style={{ border: '1px solid var(--clr-border, #1e2d45)' }}
                    >
                      <p className="font-semibold truncate">{wf.goal}</p>
                      <div className="flex items-center justify-between mt-1 text-[10px] text-slate-400">
                        <span>{new Date(wf.createdAt).toLocaleTimeString()}</span>
                        <span className="badge badge-slate text-[9px]">{wf.status}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Tool Registry Inspector */}
            <div
              className="rounded-2xl p-5"
              style={{
                background: 'var(--surface, #111827)',
                border: '1px solid var(--clr-border, #1e2d45)',
              }}
            >
              <h3 className="text-sm font-bold text-white mb-3" style={{ fontFamily: 'Plus Jakarta Sans' }}>
                Registered Clinical Tools ({tools.length})
              </h3>
              <div className="space-y-2 max-h-[380px] overflow-y-auto pr-1">
                {tools.map((t) => (
                  <div
                    key={t.name}
                    className="rounded-xl p-3 text-xs"
                    style={{
                      background: 'var(--surface-2, #1a2235)',
                      border: '1px solid var(--clr-border, #1e2d45)',
                    }}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-mono font-bold text-blue-400">{t.name}</span>
                      {t.requiresConfirmation ? (
                        <span className="badge badge-amber text-[9px]">HITL Required</span>
                      ) : (
                        <span className="badge badge-green text-[9px]">Auto-Exec</span>
                      )}
                    </div>
                    <p className="text-slate-400 text-[11px] leading-normal">{t.description}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
