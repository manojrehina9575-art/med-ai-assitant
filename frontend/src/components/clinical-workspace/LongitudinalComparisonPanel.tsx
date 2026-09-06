import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, GitCompareArrows, Loader2, LocateFixed, RefreshCcw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { longitudinalApi } from '@/services/longitudinalApi';
import { reportService, type ReportReview } from '@/services/reportService';
import { cn } from '@/utils/cn';
import type {
  AnatomySelection,
  AnatomyTarget,
  FindingChangeType,
  FindingComparison,
  LongitudinalRequestStatus,
  LongitudinalResult,
  LongitudinalStructuredFinding,
  LongitudinalSummary,
} from '@/types/clinicalWorkspace';

interface LongitudinalComparisonPanelProps {
  currentReview: ReportReview | null;
  /**
   * Sends a mapped structure to the workspace's shared anatomy preview. The panel never owns the
   * anatomy view state itself.
   */
  onViewAnatomy?: (selection: AnatomySelection) => void;
}

const changePresentation: Record<FindingChangeType, { label: string; className: string }> = {
  NEW: {
    label: 'New / not matched in prior',
    className: 'border-blue-400/35 bg-blue-500/10 text-blue-100',
  },
  RESOLVED: {
    label: 'Resolved / not present in current',
    className: 'border-emerald-400/35 bg-emerald-500/10 text-emerald-100',
  },
  INCREASED: {
    label: 'Potential interval increase',
    className: 'border-amber-400/35 bg-amber-500/10 text-amber-100',
  },
  DECREASED: {
    label: 'Potential interval decrease',
    className: 'border-cyan-400/35 bg-cyan-500/10 text-cyan-100',
  },
  UNCHANGED: {
    label: 'No measured change detected',
    className: 'border-slate-500/35 bg-slate-500/10 text-slate-100',
  },
  CHANGED: {
    label: 'Finding attributes changed',
    className: 'border-violet-400/35 bg-violet-500/10 text-violet-100',
  },
  INDETERMINATE: {
    label: 'Comparison indeterminate',
    className: 'border-zinc-500/35 bg-zinc-500/10 text-zinc-100',
  },
};

export function LongitudinalComparisonPanel({ currentReview, onViewAnatomy }: LongitudinalComparisonPanelProps) {
  const [priorReviews, setPriorReviews] = useState<ReportReview[]>([]);
  const [priorLoadStatus, setPriorLoadStatus] = useState<LongitudinalRequestStatus>('IDLE');
  const [priorLoadError, setPriorLoadError] = useState<string | null>(null);
  const [selectedPriorReviewId, setSelectedPriorReviewId] = useState('');
  const [comparisonStatus, setComparisonStatus] = useState<LongitudinalRequestStatus>('IDLE');
  const [comparisonError, setComparisonError] = useState<string | null>(null);
  const [longitudinalResult, setLongitudinalResult] = useState<LongitudinalResult | null>(null);

  useEffect(() => {
    setPriorReviews([]);
    setPriorLoadError(null);
    setSelectedPriorReviewId('');
    setComparisonStatus('IDLE');
    setComparisonError(null);
    setLongitudinalResult(null);

    if (!currentReview) {
      setPriorLoadStatus('IDLE');
      return;
    }

    let cancelled = false;
    setPriorLoadStatus('LOADING');

    reportService
      .forPatient(currentReview.patientId, 0, 20)
      .then((page) => {
        if (cancelled) return;
        setPriorReviews(page.content);
        setPriorLoadStatus('SUCCESS');
      })
      .catch((error) => {
        if (cancelled) return;
        setPriorLoadError(priorLoadFailureMessage(error));
        setPriorLoadStatus('ERROR');
      });

    return () => {
      cancelled = true;
    };
  }, [currentReview]);

  const priorCandidates = useMemo(
    () => priorReviews
      .filter((review) => isPriorCandidate(review, currentReview))
      .sort((a, b) => (reviewTime(b) ?? 0) - (reviewTime(a) ?? 0)),
    [currentReview, priorReviews]
  );
  const selectedPriorReview = priorCandidates.find((review) => review.id === selectedPriorReviewId) ?? null;

  function handlePriorSelection(priorReviewId: string) {
    setSelectedPriorReviewId(priorReviewId);
    setComparisonStatus('IDLE');
    setComparisonError(null);
    setLongitudinalResult(null);
  }

  async function runComparison() {
    if (!currentReview || !selectedPriorReviewId) return;

    setComparisonStatus('LOADING');
    setComparisonError(null);
    setLongitudinalResult(null);

    try {
      const result = await longitudinalApi.compareReports(currentReview.id, selectedPriorReviewId);
      setLongitudinalResult(result);
      setComparisonStatus('SUCCESS');
    } catch (error) {
      setComparisonError(comparisonFailureMessage(error));
      setComparisonStatus('ERROR');
    }
  }

  if (!currentReview) {
    return (
      <div className="p-4">
        <StatusMessage title="Report review loading" message="Prior comparison is available after the current report review loads." />
      </div>
    );
  }

  return (
    <div className="space-y-4 p-4">
      <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end">
        <div>
          <label htmlFor="prior-review-select" className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            Prior report
          </label>
          <select
            id="prior-review-select"
            aria-label="Prior report"
            value={selectedPriorReviewId}
            onChange={(event) => handlePriorSelection(event.target.value)}
            disabled={priorLoadStatus === 'LOADING' || priorCandidates.length === 0}
            className="mt-2 h-10 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 text-sm text-slate-100 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-500/20 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <option value="">Select prior report...</option>
            {priorCandidates.map((review) => (
              <option key={review.id} value={review.id}>
                {priorReviewLabel(review)}
              </option>
            ))}
          </select>
        </div>
        <Button
          type="button"
          onClick={() => void runComparison()}
          disabled={!selectedPriorReviewId || comparisonStatus === 'LOADING'}
          className="h-10"
        >
          {comparisonStatus === 'LOADING' ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              Comparing
            </>
          ) : (
            <>
              <GitCompareArrows className="h-4 w-4" />
              Compare
            </>
          )}
        </Button>
      </div>

      {priorLoadStatus === 'LOADING' && (
        <StatusMessage title="Loading prior reports" message="Fetching signed reports for this patient." icon="loading" />
      )}
      {priorLoadStatus === 'ERROR' && (
        <StatusMessage title="Prior reports unavailable" message={priorLoadError ?? 'Could not load prior reports.'} tone="error" />
      )}
      {priorLoadStatus === 'SUCCESS' && priorCandidates.length === 0 && (
        <StatusMessage title="No signed prior reports" message="No signed prior reports are available for this patient." />
      )}
      {priorLoadStatus === 'SUCCESS' && priorCandidates.length > 0 && !selectedPriorReviewId && (
        <StatusMessage title="Ready to compare" message="Select a signed prior report to run deterministic longitudinal comparison." />
      )}
      {selectedPriorReview && comparisonStatus === 'IDLE' && (
        <StatusMessage
          title="Prior selected"
          message={`Ready to compare against ${priorReviewLabel(selectedPriorReview)}.`}
        />
      )}
      {comparisonStatus === 'LOADING' && (
        <StatusMessage title="Comparing reports" message="Comparing reports..." icon="loading" />
      )}
      {comparisonStatus === 'ERROR' && (
        <div className="rounded-lg border border-red-500/30 bg-red-950/20 p-4">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-300" />
            <div className="flex-1">
              <p className="text-sm font-semibold text-red-100">Longitudinal comparison failed</p>
              <p className="mt-1 text-xs leading-5 text-red-200/80">
                {comparisonError ?? 'The server could not compare these reports right now. Please retry.'}
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="mt-3"
                onClick={() => void runComparison()}
                disabled={!selectedPriorReviewId}
              >
                <RefreshCcw className="h-3.5 w-3.5" />
                Retry
              </Button>
            </div>
          </div>
        </div>
      )}

      {longitudinalResult && (
        <div className="space-y-4">
          <SummaryGrid summary={longitudinalResult.summary} />
          {longitudinalResult.comparisons.length === 0 ? (
            <StatusMessage title="No comparable findings" message="The comparison completed without matched structured findings." />
          ) : (
            <div className="space-y-3">
              {longitudinalResult.comparisons.map((comparison, index) => (
                <ComparisonRow
                  key={`${comparison.changeType}-${index}`}
                  comparison={comparison}
                  onViewAnatomy={onViewAnatomy}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function SummaryGrid({ summary }: { summary: LongitudinalSummary }) {
  const rows = [
    { label: 'New', value: summary.newFindings },
    { label: 'Resolved', value: summary.resolvedFindings },
    { label: 'Increased', value: summary.increasedFindings },
    { label: 'Decreased', value: summary.decreasedFindings },
    { label: 'Unchanged', value: summary.unchangedFindings },
    { label: 'Changed', value: summary.changedFindings },
    { label: 'Indeterminate', value: summary.indeterminateFindings },
  ];

  return (
    <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
      {rows.map((row) => (
        <div key={row.label} className="rounded-lg border border-slate-800 bg-slate-950/40 px-3 py-2">
          <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">{row.label}</p>
          <p className="mt-1 text-lg font-bold text-white">{row.value}</p>
        </div>
      ))}
    </div>
  );
}

function ComparisonRow({
  comparison,
  onViewAnatomy,
}: {
  comparison: FindingComparison;
  onViewAnatomy?: (selection: AnatomySelection) => void;
}) {
  const representativeFinding = comparison.currentFinding ?? comparison.priorFinding ?? null;
  const presentation = changePresentation[comparison.changeType];
  const anatomyActions = anatomyActionsFor(comparison);

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-950/35 p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-sm font-semibold text-white">{findingHeadline(representativeFinding, comparison.changeType)}</h3>
          <p className="mt-1 text-xs leading-5 text-slate-400">{comparison.explanation}</p>
        </div>
        <div className={cn('inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1 text-[11px] font-semibold', presentation.className)}>
          <span>{comparison.changeType}</span>
          <span className="text-current opacity-75">{presentation.label}</span>
        </div>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <FindingField label="Finding Type" value={representativeFinding?.findingType ?? 'Not available'} />
        <FindingField label="Anatomy" value={representativeFinding?.anatomy ?? representativeFinding?.anatomyText ?? 'Unspecified'} />
        <FindingField label="Side" value={representativeFinding?.side ?? 'Unspecified'} />
        <FindingField label="Region" value={representativeFinding?.region ?? 'Unspecified'} />
        <FindingField
          label="Delta"
          value={comparison.measurementDeltaMm == null ? 'N/A' : `${formatSignedNumber(comparison.measurementDeltaMm)} mm`}
        />
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <FindingField
          label="Prior Measurement"
          value={measurementLabel(comparison.priorFinding, comparison.priorMeasurementMm)}
        />
        <FindingField
          label="Current Measurement"
          value={measurementLabel(comparison.currentFinding, comparison.currentMeasurementMm)}
        />
      </div>

      {onViewAnatomy && anatomyActions.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2">
          {anatomyActions.map((action) => (
            <Button
              key={action.label}
              type="button"
              size="sm"
              variant="outline"
              onClick={() => onViewAnatomy(action.selection)}
            >
              <LocateFixed className="h-3.5 w-3.5" />
              {action.label}
            </Button>
          ))}
        </div>
      )}

      <details className="mt-4 rounded-lg border border-slate-800 bg-slate-950/35 px-3 py-2">
        <summary className="cursor-pointer text-xs font-semibold text-slate-300">Source evidence</summary>
        <div className="mt-3 grid gap-3 md:grid-cols-2">
          <EvidenceBlock label="Prior report" finding={comparison.priorFinding} />
          <EvidenceBlock label="Current report" finding={comparison.currentFinding} />
        </div>
      </details>
    </div>
  );
}

interface AnatomyAction {
  label: string;
  selection: AnatomySelection;
}

/**
 * Builds the anatomy actions for one comparison from the backend targets only.
 *
 * <p>Identical prior and current targets collapse into a single action. Differing targets stay
 * separate and labelled, so the panel never silently picks one of them.
 */
function anatomyActionsFor(comparison: FindingComparison): AnatomyAction[] {
  const changeLabel = changePresentation[comparison.changeType].label;
  const current = comparison.currentAnatomyTarget
    ? anatomySelectionFromTarget(
        comparison.currentAnatomyTarget,
        'LONGITUDINAL_CURRENT',
        'Current report',
        changeLabel,
        comparison.currentFinding?.sourceText
      )
    : null;
  const prior = comparison.priorAnatomyTarget
    ? anatomySelectionFromTarget(
        comparison.priorAnatomyTarget,
        'LONGITUDINAL_PRIOR',
        'Prior report',
        changeLabel,
        comparison.priorFinding?.sourceText
      )
    : null;

  if (current && prior) {
    return isSameAnatomyTarget(comparison.currentAnatomyTarget, comparison.priorAnatomyTarget)
      ? [{ label: 'View Anatomy', selection: current }]
      : [
          { label: 'View Prior Anatomy', selection: prior },
          { label: 'View Current Anatomy', selection: current },
        ];
  }

  const single = current ?? prior;
  return single ? [{ label: 'View Anatomy', selection: single }] : [];
}

function anatomySelectionFromTarget(
  target: AnatomyTarget,
  sourceKind: 'LONGITUDINAL_CURRENT' | 'LONGITUDINAL_PRIOR',
  sourceLabel: string,
  comparisonLabel: string,
  sourceText?: string | null
): AnatomySelection {
  return {
    structure: target.structureCode,
    displayName: target.displayName,
    side: target.side,
    region: target.region,
    system: readableLabel(target.system),
    viewerKey: target.viewerKey ?? null,
    sourceLabel,
    sourceText: sourceText ?? null,
    sourceKind,
    comparisonLabel,
  };
}

/** Same structure identity. Bilateral/unspecified targets have no viewer key, so compare fields. */
function isSameAnatomyTarget(
  a?: AnatomyTarget | null,
  b?: AnatomyTarget | null
): boolean {
  if (!a || !b) return false;
  if (a.viewerKey && b.viewerKey) return a.viewerKey === b.viewerKey;
  return (
    a.system === b.system &&
    a.structureCode === b.structureCode &&
    a.side === b.side &&
    a.region === b.region
  );
}

function FindingField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">{label}</p>
      <p className="mt-0.5 break-words text-xs font-medium text-slate-200">{value}</p>
    </div>
  );
}

function EvidenceBlock({ label, finding }: { label: string; finding?: LongitudinalStructuredFinding | null }) {
  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">{label}</p>
      <p className="mt-1 rounded-md bg-slate-900/80 px-3 py-2 text-xs leading-5 text-slate-300">
        {finding?.sourceText?.trim() || 'No matching structured finding.'}
      </p>
    </div>
  );
}

function StatusMessage({
  title,
  message,
  tone = 'neutral',
  icon,
}: {
  title: string;
  message: string;
  tone?: 'neutral' | 'error';
  icon?: 'loading';
}) {
  const isError = tone === 'error';

  return (
    <div
      className={cn(
        'rounded-lg border px-4 py-3',
        isError ? 'border-red-500/30 bg-red-950/20' : 'border-slate-800 bg-slate-950/35'
      )}
    >
      <div className="flex items-start gap-2">
        {icon === 'loading' && <Loader2 className="mt-0.5 h-4 w-4 animate-spin text-blue-300" />}
        {isError && <AlertTriangle className="mt-0.5 h-4 w-4 text-red-300" />}
        <div>
          <p className={cn('text-sm font-semibold', isError ? 'text-red-100' : 'text-white')}>{title}</p>
          <p className={cn('mt-1 text-xs leading-5', isError ? 'text-red-200/80' : 'text-slate-400')}>{message}</p>
        </div>
      </div>
    </div>
  );
}

function isPriorCandidate(review: ReportReview, currentReview: ReportReview | null): boolean {
  if (!currentReview) return false;
  if (review.id === currentReview.id) return false;
  if (review.patientId !== currentReview.patientId) return false;
  if (review.status !== 'SIGNED') return false;
  if (!review.finalContent?.trim()) return false;

  const currentTime = reviewTime(currentReview);
  const priorTime = reviewTime(review);
  if (currentTime == null || priorTime == null) return false;

  return priorTime < currentTime;
}

function priorReviewLabel(review: ReportReview): string {
  const date = formatDate(review.signedAt ?? review.createdAt);
  const type = readableLabel(review.analysisType ?? 'Report review');
  return `${date} - ${type} - ${review.id.slice(0, 8)}`;
}

function reviewTime(review: ReportReview): number | null {
  const date = new Date(review.signedAt ?? review.createdAt);
  const time = date.getTime();
  return Number.isNaN(time) ? null : time;
}

function findingHeadline(finding: LongitudinalStructuredFinding | null, changeType: FindingChangeType): string {
  if (!finding) return readableLabel(changeType);
  const anatomy = finding.anatomyText?.trim() || finding.anatomy || null;
  return [readableLabel(finding.findingType), anatomy ? readableLabel(anatomy) : null]
    .filter((part): part is string => Boolean(part))
    .join(' / ');
}

function measurementLabel(finding: LongitudinalStructuredFinding | null | undefined, normalizedMeasurementMm?: number | null): string {
  if (finding?.measurement != null) {
    return `${formatNumber(finding.measurement)} ${finding.unit ?? 'mm'}`;
  }
  if (normalizedMeasurementMm != null) {
    return `${formatNumber(normalizedMeasurementMm)} mm`;
  }
  return 'Not measured';
}

function comparisonFailureMessage(error: unknown): string {
  const status = (error as { response?: { status?: number } }).response?.status;
  if (status === 401) {
    return 'Your session expired before comparison could run. Sign in again if this continues.';
  }
  if (status === 403) {
    return 'Your account is not allowed to compare these reports.';
  }
  if (status === 404) {
    return 'One of the selected report reviews was not found in this workspace.';
  }
  if (status === 400 || status === 409) {
    return 'Selected reports could not be compared. Confirm the prior report is signed and belongs to the same patient.';
  }
  if (status && status >= 500) {
    return 'The server could not compare these reports right now. Please retry.';
  }
  return 'Network error while comparing reports. Please retry.';
}

function priorLoadFailureMessage(error: unknown): string {
  const status = (error as { response?: { status?: number } }).response?.status;
  if (status === 401 || status === 403) {
    return 'Your session is not authorized to load prior reports for this patient.';
  }
  if (status && status >= 500) {
    return 'The server could not load prior reports right now.';
  }
  return 'Network error while loading prior reports.';
}

function readableLabel(value: string): string {
  return value
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'Date not recorded';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date);
}

function formatNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function formatSignedNumber(value: number): string {
  if (value > 0) return `+${formatNumber(value)}`;
  return formatNumber(value);
}
