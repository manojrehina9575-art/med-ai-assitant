import { AlertTriangle, CheckCircle2, Loader2, RotateCcw, ShieldAlert } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { QaIssueCard } from './QaIssueCard';
import { qaSeverityMeta } from './qaSeverity';
import type { AnatomySelection, QaIssue, QaRequestStatus, QaSeverity } from '@/types/clinicalWorkspace';

interface QaPanelProps {
  issues: QaIssue[];
  dismissedIssueIds: string[];
  selectedIssueId: string | null;
  requestStatus: QaRequestStatus;
  errorMessage?: string | null;
  onRetry?: () => void;
  onReviewIssue: (issue: QaIssue) => void;
  onDismissIssue: (issue: QaIssue) => void;
  onViewAnatomy: (issue: QaIssue, selection: AnatomySelection) => void;
}

const severityOrder: QaSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

function summarizeIssues(issues: QaIssue[]) {
  return severityOrder
    .map((severity) => {
      const count = issues.filter((issue) => issue.severity === severity).length;
      return count > 0 ? { severity, count } : null;
    })
    .filter((item): item is { severity: QaSeverity; count: number } => Boolean(item));
}

export function QaPanel({
  issues,
  dismissedIssueIds,
  selectedIssueId,
  requestStatus,
  errorMessage,
  onRetry,
  onReviewIssue,
  onDismissIssue,
  onViewAnatomy,
}: QaPanelProps) {
  const visibleIssues = issues.filter((issue) => !dismissedIssueIds.includes(issue.id));
  const summary = summarizeIssues(visibleIssues);
  const hasDismissedIssues = dismissedIssueIds.length > 0 && issues.length > 0;

  return (
    <Card className="h-full min-w-0">
      <CardHeader className="border-b" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2">
              <ShieldAlert className="h-4 w-4 text-amber-400" />
              QA Review
            </CardTitle>
            <CardDescription>Potential report issues and supporting evidence</CardDescription>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            {dismissedIssueIds.length > 0 && (
              <span className="badge badge-slate text-[10px]">{dismissedIssueIds.length} dismissed</span>
            )}
            {requestStatus === 'SUCCESS' && visibleIssues.length > 0 && (
              <span className="inline-flex items-center gap-1 rounded-full border border-red-500/30 bg-red-500/15 px-2 py-0.5 text-[11px] font-bold text-red-300">
                <AlertTriangle className="h-3 w-3" />
                {visibleIssues.length} {visibleIssues.length === 1 ? 'Issue' : 'Issues'}
              </span>
            )}
          </div>
        </div>

        <div className="mt-3 flex flex-wrap gap-2">
          {requestStatus === 'LOADING' ? (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-[11px] font-semibold text-blue-300">
              <Loader2 className="h-3 w-3 animate-spin" />
              Running QA
            </span>
          ) : requestStatus === 'ERROR' ? (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-red-500/25 bg-red-500/10 px-2.5 py-1 text-[11px] font-semibold text-red-300">
              <AlertTriangle className="h-3 w-3" />
              QA unavailable
            </span>
          ) : requestStatus === 'IDLE' ? (
            <span className="inline-flex rounded-full border border-slate-600/30 bg-slate-800/40 px-2.5 py-1 text-[11px] font-semibold text-slate-300">
              Ready to run
            </span>
          ) : summary.length === 0 ? (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-1 text-[11px] font-semibold text-emerald-300">
              <CheckCircle2 className="h-3 w-3" />
              No potential issues
            </span>
          ) : (
            summary.map(({ severity, count }) => (
              <span
                key={severity}
                className={`inline-flex rounded-full border px-2.5 py-1 text-[11px] font-bold ${qaSeverityMeta[severity].badge}`}
              >
                {count} {qaSeverityMeta[severity].label}
              </span>
            ))
          )}
        </div>
      </CardHeader>

      <CardContent className="space-y-3 p-4">
        {requestStatus === 'LOADING' ? (
          <div className="flex min-h-48 items-center justify-center rounded-lg border border-dashed border-blue-500/20 bg-blue-950/20 px-4 py-8 text-center">
            <div>
              <Loader2 className="mx-auto mb-2 h-6 w-6 animate-spin text-blue-300" />
              <p className="text-sm font-semibold text-white">Running report QA</p>
              <p className="mt-1 text-xs text-slate-500">The report text is not modified.</p>
            </div>
          </div>
        ) : requestStatus === 'ERROR' ? (
          <div className="rounded-lg border border-red-500/25 bg-red-950/20 px-4 py-4">
            <div className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-300" />
              <div className="min-w-0">
                <p className="text-sm font-semibold text-red-100">QA request failed</p>
                <p className="mt-1 text-xs leading-5 text-red-200/80">
                  {errorMessage ?? 'Could not run QA for this report.'}
                </p>
                {onRetry && (
                  <Button type="button" size="sm" variant="outline" className="mt-3" onClick={onRetry}>
                    <RotateCcw className="h-3.5 w-3.5" />
                    Retry
                  </Button>
                )}
              </div>
            </div>
          </div>
        ) : requestStatus === 'IDLE' ? (
          <div className="flex min-h-48 items-center justify-center rounded-lg border border-dashed border-slate-700 bg-slate-950/30 px-4 py-8 text-center">
            <div>
              <ShieldAlert className="mx-auto mb-2 h-6 w-6 text-slate-400" />
              <p className="text-sm font-semibold text-white">QA not run yet</p>
              <p className="mt-1 text-xs text-slate-500">Use Run QA when this review is ready to evaluate.</p>
            </div>
          </div>
        ) : visibleIssues.length === 0 ? (
          <div className="flex min-h-48 items-center justify-center rounded-lg border border-dashed border-slate-700 bg-slate-950/30 px-4 py-8 text-center">
            <div>
              <CheckCircle2 className="mx-auto mb-2 h-6 w-6 text-emerald-400" />
              <p className="text-sm font-semibold text-white">No visible QA issues</p>
              <p className="mt-1 text-xs text-slate-500">
                {hasDismissedIssues
                  ? 'Dismissed issues can be restored with Run QA.'
                  : 'No potential QA issues detected by the current checks.'}
              </p>
            </div>
          </div>
        ) : (
          visibleIssues.map((issue) => (
            <QaIssueCard
              key={issue.id}
              issue={issue}
              isSelected={issue.id === selectedIssueId}
              onReview={onReviewIssue}
              onDismiss={onDismissIssue}
              onViewAnatomy={onViewAnatomy}
            />
          ))
        )}
      </CardContent>
    </Card>
  );
}
