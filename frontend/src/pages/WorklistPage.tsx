import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  reportService,
  parseDraft,
  draftAsNarrative,
  type ReportReview,
  type WorklistSummary,
  type CriticalEscalation,
  type ReviewAction,
} from '@/services/reportService';
import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/stores/authStore';
import {
  Loader2,
  CheckCircle2,
  PenLine,
  XCircle,
  AlertTriangle,
  Clock,
  UserCheck,
  FileText,
  Inbox,
  ShieldAlert,
  RotateCcw,
} from 'lucide-react';

/** Elapsed time, because "waiting 3h" is the number a reading room actually acts on. */
function waitedFor(iso: string): string {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000));
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ${minutes % 60}m`;
  return `${Math.floor(hours / 24)}d ${hours % 24}h`;
}

const STATUS_BADGE: Record<string, string> = {
  DRAFT: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  IN_REVIEW: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  SIGNED: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  REJECTED: 'bg-red-500/10 text-red-400 border-red-500/20',
  AMENDED: 'bg-violet-500/10 text-violet-400 border-violet-500/20',
};

const URGENCY_BADGE: Record<string, string> = {
  ROUTINE: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
  URGENT: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  CRITICAL: 'bg-red-500/20 text-red-300 border-red-500/30 font-bold',
};

type Mode = 'view' | 'edit' | 'reject';

export function WorklistPage() {
  const { userId, role } = useAuthStore();
  const canSign = role === 'DOCTOR' || role === 'HOSPITAL_ADMIN';

  const [reviews, setReviews] = useState<ReportReview[]>([]);
  const [summary, setSummary] = useState<WorklistSummary | null>(null);
  const [escalations, setEscalations] = useState<CriticalEscalation[]>([]);
  const [selected, setSelected] = useState<ReportReview | null>(null);

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const [mode, setMode] = useState<Mode>('view');
  const [narrative, setNarrative] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [ackFor, setAckFor] = useState<CriticalEscalation | null>(null);
  const [ackAction, setAckAction] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const [list, counts, critical] = await Promise.all([
        reportService.worklist(0, 50),
        reportService.summary(),
        reportService.criticalResults(),
      ]);
      setReviews(list.content);
      setSummary(counts);
      setEscalations(critical);

      // Keep the open report selected across a refresh; drop it once it leaves the worklist.
      setSelected((current) =>
        current ? list.content.find((r) => r.id === current.id) ?? null : null
      );
    } catch {
      setError('Could not load the worklist. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const draft = useMemo(() => parseDraft(selected?.draftContent ?? null), [selected]);

  function openReport(review: ReportReview) {
    setSelected(review);
    setMode('view');
    setRejectionReason('');
    setNarrative(draftAsNarrative(parseDraft(review.draftContent), review.draftContent));
  }

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError('');
    try {
      await action();
      await load();
      setMode('view');
    } catch (e) {
      const err = e as { response?: { data?: { message?: string } } };
      setError(err.response?.data?.message ?? 'That did not go through. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  const sign = (action: ReviewAction) => {
    if (!selected) return;
    void run(() =>
      reportService.sign(selected.id, action, {
        finalContent: action === 'EDITED' ? narrative : undefined,
        rejectionReason: action === 'REJECTED' ? rejectionReason : undefined,
      })
    );
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24 text-slate-400">
        <Loader2 className="h-5 w-5 animate-spin mr-2" />
        Loading worklist…
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Critical results sit above everything: the reading room is where a clinician looks,
          and an unacknowledged critical finding outranks the queue. */}
      {escalations.length > 0 && (
        <div className="rounded-xl border border-red-500/30 bg-red-950/30 p-4">
          <div className="flex items-center gap-2 mb-3">
            <ShieldAlert className="h-5 w-5 text-red-400" />
            <h2 className="text-sm font-bold text-red-200">
              {escalations.length} critical result{escalations.length > 1 ? 's' : ''} awaiting acknowledgement
            </h2>
          </div>
          <div className="space-y-2">
            {escalations.map((e) => (
              <div
                key={e.id}
                className="flex flex-wrap items-center gap-3 rounded-lg border border-red-500/20 bg-red-950/40 px-3 py-2"
              >
                <span className={`px-2 py-0.5 rounded text-[10px] border ${URGENCY_BADGE[e.urgency]}`}>
                  {e.urgency}
                </span>
                <span className="text-sm text-red-100 font-medium">{e.patientName ?? 'Patient'}</span>
                <span className="text-xs text-red-300/80 flex-1 min-w-[12rem]">{e.findingSummary}</span>
                <span className="text-[11px] text-red-400/70 tabular-nums">
                  waiting {waitedFor(e.createdAt)} · escalation level {e.escalationLevel}
                </span>
                {canSign && (
                  <Button size="sm" variant="destructive" onClick={() => { setAckFor(e); setAckAction(''); }}>
                    Acknowledge
                  </Button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <SummaryTile icon={Inbox} label="Awaiting review" value={summary.awaitingReview} tone="text-blue-400" />
          <SummaryTile icon={UserCheck} label="In review" value={summary.inReview} tone="text-amber-400" />
          <SummaryTile icon={CheckCircle2} label="Signed today" value={summary.signedToday} tone="text-emerald-400" />
          <SummaryTile
            icon={PenLine}
            label="Corrected / accepted"
            value={`${summary.editedAllTime} / ${summary.acceptedAllTime}`}
            tone="text-violet-400"
          />
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-red-500/30 bg-red-950/30 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
        {/* Queue */}
        <div className="lg:col-span-2 rounded-xl border" style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface, #111827)' }}>
          <div className="px-4 py-3 border-b flex items-center justify-between" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
            <h2 className="text-sm font-semibold text-white">Reading worklist</h2>
            <button onClick={() => void load()} className="text-slate-500 hover:text-slate-300" title="Refresh">
              <RotateCcw className="h-3.5 w-3.5" />
            </button>
          </div>

          {reviews.length === 0 ? (
            <p className="px-4 py-10 text-center text-sm text-slate-500">
              Nothing waiting. Completed analyses appear here for sign-off.
            </p>
          ) : (
            <ul className="divide-y" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
              {reviews.map((review) => {
                const urgency = parseDraft(review.draftContent)?.urgency ?? 'ROUTINE';
                const isSelected = selected?.id === review.id;
                return (
                  <li key={review.id}>
                    <button
                      onClick={() => openReport(review)}
                      className={`w-full text-left px-4 py-3 transition-colors ${
                        isSelected ? 'bg-blue-950/30' : 'hover:bg-slate-800/40'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-white truncate">
                            {review.patientName ?? 'Unknown patient'}
                          </p>
                          <p className="text-xs text-slate-500 mt-0.5">
                            {review.analysisType?.replace(/_/g, ' ').toLowerCase() ?? 'analysis'}
                          </p>
                        </div>
                        <div className="flex flex-col items-end gap-1 shrink-0">
                          <span className={`px-1.5 py-0.5 rounded text-[10px] border ${URGENCY_BADGE[urgency]}`}>
                            {urgency}
                          </span>
                          <span className="text-[11px] text-slate-500 tabular-nums flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {waitedFor(review.createdAt)}
                          </span>
                        </div>
                      </div>
                      {review.claimedBy && (
                        <p className="mt-1.5 text-[11px] text-amber-400/80">
                          {review.claimedBy === userId ? 'Claimed by you' : 'Claimed by another clinician'}
                        </p>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {/* Report */}
        <div className="lg:col-span-3 rounded-xl border" style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface, #111827)' }}>
          {!selected ? (
            <div className="flex flex-col items-center justify-center py-24 text-slate-500">
              <FileText className="h-8 w-8 mb-2 opacity-40" />
              <p className="text-sm">Select a report to review</p>
            </div>
          ) : (
            <div className="p-5 space-y-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-base font-semibold text-white">{selected.patientName ?? 'Patient'}</h2>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {selected.analysisType?.replace(/_/g, ' ').toLowerCase()} · waiting {waitedFor(selected.createdAt)}
                  </p>
                </div>
                <span className={`px-2 py-0.5 rounded text-[10px] border ${STATUS_BADGE[selected.status]}`}>
                  {selected.status.replace('_', ' ')}
                </span>
              </div>

              {/* Unsigned output is labelled as such wherever it appears. The whole regulatory
                  position is that a clinician decides, so the UI never presents a draft as a report. */}
              <div className="flex items-center gap-2 rounded-lg border border-amber-500/20 bg-amber-950/20 px-3 py-2">
                <AlertTriangle className="h-3.5 w-3.5 text-amber-400 shrink-0" />
                <p className="text-[11px] text-amber-300/90">
                  AI-generated draft. Not a clinical report until a licensed practitioner signs it.
                </p>
              </div>

              {mode === 'view' && (
                <DraftView draft={draft} raw={selected.draftContent} />
              )}

              {mode === 'edit' && (
                <div className="space-y-2">
                  <label className="text-xs font-medium text-slate-400">
                    Corrected report — this text is what you are signing
                  </label>
                  <textarea
                    value={narrative}
                    onChange={(e) => setNarrative(e.target.value)}
                    rows={16}
                    className="w-full rounded-lg border bg-slate-950/60 px-3 py-2 text-sm text-slate-200 font-mono leading-relaxed focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                    style={{ borderColor: 'var(--clr-border, #1e2d45)' }}
                  />
                  <p className="text-[11px] text-slate-500">
                    Your correction is kept alongside the original draft — the pair is what improves the model.
                  </p>
                </div>
              )}

              {mode === 'reject' && (
                <div className="space-y-2">
                  <label className="text-xs font-medium text-slate-400">
                    Why is this draft unusable?
                  </label>
                  <textarea
                    value={rejectionReason}
                    onChange={(e) => setRejectionReason(e.target.value)}
                    rows={4}
                    placeholder="e.g. Findings describe the wrong side; image was mislabelled."
                    className="w-full rounded-lg border bg-slate-950/60 px-3 py-2 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-red-500/40"
                    style={{ borderColor: 'var(--clr-border, #1e2d45)' }}
                  />
                  <p className="text-[11px] text-slate-500">
                    Required. Without a reason the model cannot be improved and the rejection cannot be reviewed.
                  </p>
                </div>
              )}

              {!canSign ? (
                <p className="text-xs text-slate-500 border-t pt-3" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
                  Your role can view drafts but not sign them. Signing requires a doctor account.
                </p>
              ) : (
                <div className="flex flex-wrap gap-2 border-t pt-4" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
                  {mode === 'view' && (
                    <>
                      {selected.claimedBy !== userId && (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={busy}
                          onClick={() => void run(() => reportService.claim(selected.id))}
                        >
                          <UserCheck className="h-3.5 w-3.5" />
                          Claim
                        </Button>
                      )}
                      <Button
                        size="sm"
                        disabled={busy}
                        style={{ background: '#059669' }}
                        onClick={() => sign('ACCEPTED')}
                      >
                        {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
                        Accept &amp; sign
                      </Button>
                      <Button variant="outline" size="sm" disabled={busy} onClick={() => setMode('edit')}>
                        <PenLine className="h-3.5 w-3.5" />
                        Correct &amp; sign
                      </Button>
                      <Button variant="destructive" size="sm" disabled={busy} onClick={() => setMode('reject')}>
                        <XCircle className="h-3.5 w-3.5" />
                        Reject
                      </Button>
                    </>
                  )}

                  {mode === 'edit' && (
                    <>
                      <Button
                        size="sm"
                        disabled={busy || narrative.trim().length === 0}
                        style={{ background: '#059669' }}
                        onClick={() => sign('EDITED')}
                      >
                        {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
                        Sign correction
                      </Button>
                      <Button variant="ghost" size="sm" disabled={busy} onClick={() => setMode('view')}>
                        Cancel
                      </Button>
                    </>
                  )}

                  {mode === 'reject' && (
                    <>
                      <Button
                        variant="destructive"
                        size="sm"
                        disabled={busy || rejectionReason.trim().length === 0}
                        onClick={() => sign('REJECTED')}
                      >
                        {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <XCircle className="h-3.5 w-3.5" />}
                        Confirm rejection
                      </Button>
                      <Button variant="ghost" size="sm" disabled={busy} onClick={() => setMode('view')}>
                        Cancel
                      </Button>
                    </>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Acknowledgement forces an action to be recorded: the notification duty asks what happened
          to the patient, not that a button was pressed. */}
      {ackFor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div
            className="w-full max-w-lg rounded-xl border p-5 space-y-4"
            style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface, #111827)' }}
          >
            <div className="flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-red-400" />
              <h3 className="text-sm font-semibold text-white">Acknowledge critical result</h3>
            </div>
            <p className="text-sm text-slate-300">
              <span className="font-medium">{ackFor.patientName ?? 'Patient'}</span> — {ackFor.findingSummary}
            </p>
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-slate-400">What action did you take?</label>
              <textarea
                value={ackAction}
                onChange={(e) => setAckAction(e.target.value)}
                rows={3}
                placeholder="e.g. Patient reviewed at bedside, chest drain inserted, admitted to HDU."
                className="w-full rounded-lg border bg-slate-950/60 px-3 py-2 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-red-500/40"
                style={{ borderColor: 'var(--clr-border, #1e2d45)' }}
              />
              <p className="text-[11px] text-slate-500">
                Recorded against your name and the time. Required.
              </p>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" size="sm" onClick={() => setAckFor(null)}>
                Cancel
              </Button>
              <Button
                variant="destructive"
                size="sm"
                disabled={busy || ackAction.trim().length === 0}
                onClick={() =>
                  void run(async () => {
                    await reportService.acknowledgeCritical(ackFor.id, ackAction);
                    setAckFor(null);
                  })
                }
              >
                {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
                Acknowledge
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function SummaryTile({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: typeof Inbox;
  label: string;
  value: number | string;
  tone: string;
}) {
  return (
    <div
      className="rounded-xl border px-4 py-3"
      style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface, #111827)' }}
    >
      <div className="flex items-center gap-2 mb-1">
        <Icon className={`h-3.5 w-3.5 ${tone}`} />
        <span className="text-[11px] uppercase tracking-wide text-slate-500">{label}</span>
      </div>
      <p className="text-xl font-semibold text-white tabular-nums">{value}</p>
    </div>
  );
}

function DraftView({ draft, raw }: { draft: ReturnType<typeof parseDraft>; raw: string | null }) {
  // An unreadable draft must not render as "no findings" — a reviewer would sign an empty report.
  if (!draft) {
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-2 rounded-lg border border-red-500/20 bg-red-950/20 px-3 py-2">
          <AlertTriangle className="h-3.5 w-3.5 text-red-400 shrink-0" />
          <p className="text-[11px] text-red-300">
            This draft could not be read as a structured report. The raw output is shown below — do not
            sign it without checking the source study.
          </p>
        </div>
        <pre className="rounded-lg bg-slate-950/60 p-3 text-[11px] text-slate-400 overflow-x-auto whitespace-pre-wrap">
          {raw ?? '(empty)'}
        </pre>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {draft.findings.length > 0 && (
        <section>
          <h3 className="text-[11px] uppercase tracking-wide text-slate-500 mb-1.5">Findings</h3>
          <ul className="space-y-1.5">
            {draft.findings.map((f, i) => (
              <li key={i} className="text-sm text-slate-300 flex gap-2">
                <span className="text-slate-600">•</span>
                <span>
                  {f.region && <span className="text-slate-400">{f.region}: </span>}
                  {f.description}
                  {f.severity && <span className="text-slate-500"> ({f.severity.toLowerCase()})</span>}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {draft.impression && (
        <section>
          <h3 className="text-[11px] uppercase tracking-wide text-slate-500 mb-1.5">Impression</h3>
          <p className="text-sm text-slate-200 leading-relaxed">{draft.impression}</p>
        </section>
      )}

      {draft.recommendations.length > 0 && (
        <section>
          <h3 className="text-[11px] uppercase tracking-wide text-slate-500 mb-1.5">Recommendations</h3>
          <ul className="space-y-1">
            {draft.recommendations.map((r, i) => (
              <li key={i} className="text-sm text-slate-300 flex gap-2">
                <span className="text-slate-600">•</span>
                <span>{r}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {draft.icd10Codes.length > 0 && (
        <section>
          <h3 className="text-[11px] uppercase tracking-wide text-slate-500 mb-1.5">ICD-10</h3>
          <div className="flex flex-wrap gap-1.5">
            {draft.icd10Codes.map((code) => (
              <span
                key={code}
                className="px-2 py-0.5 rounded text-[11px] border border-slate-600/40 bg-slate-800/40 text-slate-300 font-mono"
              >
                {code}
              </span>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
