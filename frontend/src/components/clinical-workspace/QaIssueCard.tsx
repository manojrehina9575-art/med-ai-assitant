import { Eye, LocateFixed, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { qaSeverityMeta } from './qaSeverity';
import type { AnatomySelection, QaIssue } from '@/types/clinicalWorkspace';

interface QaIssueCardProps {
  issue: QaIssue;
  isSelected: boolean;
  onReview: (issue: QaIssue) => void;
  onDismiss: (issue: QaIssue) => void;
  onViewAnatomy: (issue: QaIssue, selection: AnatomySelection) => void;
}

export function QaIssueCard({ issue, isSelected, onReview, onDismiss, onViewAnatomy }: QaIssueCardProps) {
  const severity = qaSeverityMeta[issue.severity];
  const anatomyCandidates = anatomySelectionsFor(issue);
  // More than one mapped structure means the issue itself is the conflict. Each candidate gets its
  // own labelled action so the view never implies one of them is the correct side.
  const hasMultipleAnatomyTargets = anatomyCandidates.length > 1;

  return (
    <article
      className={`rounded-lg border-y border-r p-3 transition-colors ${isSelected ? severity.border : 'border-slate-800'}`}
      style={{
        background: isSelected ? 'rgba(59,130,246,0.08)' : 'var(--surface-2, #1a2235)',
        borderLeft: `3px solid ${severity.accent}`,
      }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-bold ${severity.badge}`}>
              {severity.label}
            </span>
            <span className="font-mono text-[10px] text-slate-500">{issue.type}</span>
          </div>
          <p className="mt-2 text-sm font-medium leading-6 text-slate-100">{issue.message}</p>
          {issue.recommendation && (
            <p className="mt-1 text-xs leading-5 text-slate-400">{issue.recommendation}</p>
          )}
        </div>
      </div>

      {issue.evidence.length > 0 && (
        <div className="mt-3 space-y-2">
          <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Supporting Evidence</p>
          {issue.evidence.map((item, index) => {
            const accentColor = evidenceAccent(index);
            return (
              <div
                key={`${issue.id}-${item.label}`}
                className="rounded-md border-y border-r border-slate-800 bg-slate-950/40 px-3 py-2"
                style={{ borderLeft: `2px solid ${accentColor}` }}
              >
                <p className="text-[10px] font-semibold uppercase tracking-wider" style={{ color: accentColor }}>
                  {item.label}
                </p>
                {item.normalizedLabel && (
                  <p className="mt-1 text-xs font-semibold text-blue-200">{item.normalizedLabel}</p>
                )}
                <p className="mt-1 text-xs leading-5 text-slate-300">"{item.text}"</p>
              </div>
            );
          })}
        </div>
      )}

      <div className="mt-3 flex flex-wrap gap-2">
        <Button type="button" size="sm" variant={isSelected ? 'default' : 'outline'} onClick={() => onReview(issue)}>
          <Eye className="h-3.5 w-3.5" />
          Mark as Reviewed
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={() => onDismiss(issue)}>
          <XCircle className="h-3.5 w-3.5" />
          Dismiss
        </Button>
        {anatomyCandidates.map((candidate, index) => (
          <Button
            key={`${issue.id}-anatomy-${candidate.sourceLabel ?? index}`}
            type="button"
            size="sm"
            variant="secondary"
            onClick={() => onViewAnatomy(issue, candidate)}
          >
            <LocateFixed className="h-3.5 w-3.5" />
            {hasMultipleAnatomyTargets && candidate.sourceLabel
              ? `View ${candidate.sourceLabel} Anatomy`
              : 'View Anatomy'}
          </Button>
        ))}
      </div>

      {hasMultipleAnatomyTargets && (
        <p className="mt-2 text-[11px] leading-5 text-amber-200/80">
          This issue maps to more than one structure. Review each source finding; the correct side is
          not determined by the system.
        </p>
      )}
    </article>
  );
}

/** Backend-mapped structures for this issue, falling back to the single default selection. */
function anatomySelectionsFor(issue: QaIssue): AnatomySelection[] {
  if (issue.anatomyCandidates && issue.anatomyCandidates.length > 0) return issue.anatomyCandidates;
  return issue.anatomySelection ? [issue.anatomySelection] : [];
}

const EVIDENCE_ACCENTS = ['#3b82f6', '#a855f7', '#8b5cf6', '#06b6d4'];

function evidenceAccent(index: number): string {
  return EVIDENCE_ACCENTS[index % EVIDENCE_ACCENTS.length];
}
