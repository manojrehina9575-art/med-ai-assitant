import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { useParams, useSearchParams } from 'react-router-dom';
import { AnatomyPreview } from '@/components/clinical-workspace/AnatomyPreview';
import { ClinicalContextSidebar } from '@/components/clinical-workspace/ClinicalContextSidebar';
import { ClinicalContextTabs } from '@/components/clinical-workspace/ClinicalContextTabs';
import { ClinicalHeader } from '@/components/clinical-workspace/ClinicalHeader';
import { QaPanel } from '@/components/clinical-workspace/QaPanel';
import { ReportPanel } from '@/components/clinical-workspace/ReportPanel';
import { StudyImagesPanel } from '@/components/clinical-workspace/StudyImagesPanel';
import { WorkspaceActions } from '@/components/clinical-workspace/WorkspaceActions';
import { WorkspaceTabBar } from '@/components/clinical-workspace/WorkspaceTabBar';
import { demoClinicalWorkspace } from '@/data/demoClinicalWorkspace';
import { reportQaApi } from '@/services/reportQaApi';
import {
  parseDraft,
  reportService,
  type ReportReview,
  type ReportReviewSection,
  type ReviewStatus,
} from '@/services/reportService';
import type {
  AnatomySelection,
  ClinicalContextTab,
  ClinicalReportStatus,
  ClinicalWorkspaceStudy,
  DraftReport,
  QaIssue,
  QaRequestStatus,
  ReportQaEvidence,
  ReportQaIssue,
} from '@/types/clinicalWorkspace';

export function ClinicalWorkspacePage() {
  const params = useParams<{ reviewId?: string }>();
  const [searchParams] = useSearchParams();
  const reviewIdFromRoute = params.reviewId ?? searchParams.get('reviewId');
  const reviewId = reviewIdFromRoute?.trim() ? reviewIdFromRoute.trim() : null;
  const isDemoMode = !reviewId;

  const [review, setReview] = useState<ReportReview | null>(null);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reportStatus, setReportStatus] = useState<ClinicalReportStatus>(
    demoClinicalWorkspace.study.reportStatus
  );
  const [qaIssues, setQaIssues] = useState<QaIssue[]>(demoClinicalWorkspace.qaIssues);
  const [qaRequestStatus, setQaRequestStatus] = useState<QaRequestStatus>('SUCCESS');
  const [qaError, setQaError] = useState<string | null>(null);
  const [selectedQaIssueId, setSelectedQaIssueId] = useState<string | null>(
    demoClinicalWorkspace.qaIssues[0]?.id ?? null
  );
  const [selectedAnatomy, setSelectedAnatomy] = useState<AnatomySelection | null>(
    demoClinicalWorkspace.defaultAnatomySelection
  );
  const [dismissedIssueIds, setDismissedIssueIds] = useState<string[]>([]);
  const [activeContextTab, setActiveContextTab] = useState<ClinicalContextTab>('clinical-workspace');
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  useEffect(() => {
    setActionNotice(null);
    setDismissedIssueIds([]);
    setQaError(null);

    if (!reviewId) {
      setReview(null);
      setReviewLoading(false);
      setReviewError(null);
      setQaIssues(demoClinicalWorkspace.qaIssues);
      setQaRequestStatus('SUCCESS');
      setSelectedQaIssueId(demoClinicalWorkspace.qaIssues[0]?.id ?? null);
      setSelectedAnatomy(demoClinicalWorkspace.defaultAnatomySelection);
      setReportStatus(demoClinicalWorkspace.study.reportStatus);
      return;
    }

    let cancelled = false;
    setReview(null);
    setReviewLoading(true);
    setReviewError(null);
    setQaIssues([]);
    setQaRequestStatus('IDLE');
    setSelectedQaIssueId(null);
    setSelectedAnatomy(null);
    setReportStatus('DRAFT');

    reportService
      .get(reviewId)
      .then((loaded) => {
        if (cancelled) return;
        setReview(loaded);
        setReportStatus(mapReviewStatus(loaded.status));
        setActionNotice('Report review loaded. Run QA manually when ready.');
      })
      .catch((error) => {
        if (cancelled) return;
        setReviewError(reportLoadFailureMessage(error));
      })
      .finally(() => {
        if (!cancelled) setReviewLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [reviewId]);

  const currentStudy = useMemo(
    () => {
      if (review) return studyFromReview(review);
      if (isDemoMode) return demoClinicalWorkspace.study;
      return pendingStudy(reviewId);
    },
    [isDemoMode, review, reviewId]
  );
  const currentReport = useMemo(
    () => {
      if (review) return reportFromReview(review);
      if (isDemoMode) return demoClinicalWorkspace.report;
      return pendingReport(reviewId);
    },
    [isDemoMode, review, reviewId]
  );
  const selectedIssue = useMemo(
    () => qaIssues.find((issue) => issue.id === selectedQaIssueId) ?? null,
    [qaIssues, selectedQaIssueId]
  );
  const isQaAnatomySelection = !selectedAnatomy?.sourceKind || selectedAnatomy.sourceKind === 'QA';
  const visibleQaIssues = useMemo(
    () => qaIssues.filter((issue) => !dismissedIssueIds.includes(issue.id)),
    [qaIssues, dismissedIssueIds]
  );
  // No backend patient record backs the demo case, so real-data panels (images, prior reports,
  // medical history) only fetch once a real report review is open.
  const contextPatientId = !isDemoMode && review ? review.patientId : null;

  function reviewIssue(issue: QaIssue) {
    setSelectedQaIssueId(issue.id);
    setSelectedAnatomy(qaPreviewSelection(issue));
    setActionNotice('Issue selected for local report review.');
  }

  function dismissIssue(issue: QaIssue) {
    // QA feedback persistence does not exist yet; dismiss is intentionally local to this view.
    setDismissedIssueIds((current) => (current.includes(issue.id) ? current : [...current, issue.id]));
    setSelectedQaIssueId((current) => {
      if (current !== issue.id) return current;
      const nextIssue = qaIssues.find(
        (candidate) => candidate.id !== issue.id && !dismissedIssueIds.includes(candidate.id)
      );
      return nextIssue?.id ?? null;
    });
    setActionNotice('Issue dismissed locally. Backend QA feedback persistence is not available yet.');
  }

  function viewAnatomy(issue: QaIssue, selection?: AnatomySelection) {
    const target = selection ?? issue.anatomySelection;
    if (!target) return;
    setSelectedQaIssueId(issue.id);
    setSelectedAnatomy({ ...target, sourceKind: 'QA' });
    setActionNotice(
      target.sourceLabel
        ? `Anatomy updated to the mapped structure from the ${target.sourceLabel} evidence.`
        : 'Anatomy updated to the mapped structure for the selected QA issue.'
    );
  }

  // Longitudinal comparisons feed the same AnatomyPreview as QA. The QA issue selection is left
  // alone; the preview simply stops showing QA-specific context while a comparison target is shown.
  function viewLongitudinalAnatomy(selection: AnatomySelection) {
    setSelectedAnatomy(selection);
    setActionNotice(
      `Anatomy updated to the mapped structure from the ${selection.sourceLabel ?? 'comparison'}.`
    );
  }

  async function runQa() {
    setDismissedIssueIds([]);

    if (!reviewId) {
      setQaIssues(demoClinicalWorkspace.qaIssues);
      setQaRequestStatus('SUCCESS');
      setQaError(null);
      setSelectedQaIssueId(demoClinicalWorkspace.qaIssues[0]?.id ?? null);
      setSelectedAnatomy(demoClinicalWorkspace.defaultAnatomySelection);
      setReportStatus('REVIEW_REQUIRED');
      setActionNotice('Demo QA refreshed locally. Open a report review to run backend QA.');
      return;
    }

    setQaRequestStatus('LOADING');
    setQaError(null);
    setActionNotice(null);

    try {
      const result = await reportQaApi.runReportQa(reviewId);
      const mappedIssues = result.issues.map(mapReportQaIssue);
      const defaultIssue = mappedIssues[0] ?? null;
      setQaIssues(mappedIssues);
      setSelectedQaIssueId(defaultIssue?.id ?? null);
      setSelectedAnatomy(qaPreviewSelection(defaultIssue));
      setQaRequestStatus('SUCCESS');
      setReportStatus(result.status === 'REVIEW_RECOMMENDED' ? 'REVIEW_REQUIRED' : mapReviewStatus(review?.status ?? 'DRAFT'));
      setActionNotice(
        result.issueCount > 0
          ? 'Review recommended. Clinician review required.'
          : 'QA completed. No potential QA issues detected by the current checks.'
      );
    } catch (error) {
      setQaRequestStatus('ERROR');
      setQaError(qaFailureMessage(error));
      setActionNotice('QA could not run. No report text was changed.');
    }
  }

  function saveDraft() {
    setActionNotice('Draft state noted locally for this frontend shell. No backend save was performed.');
  }

  function markReadyToSign() {
    setReportStatus('READY_TO_SIGN');
    setActionNotice('Report marked ready to sign in this local demo view only.');
  }

  function addClinicalNote() {
    setActionNotice('Clinical notes are not available yet for this workspace.');
  }

  return (
    <div className="space-y-5 pb-8">
      {reviewLoading && (
        <div className="flex items-center rounded-xl border px-4 py-3 text-sm text-slate-300" style={{ background: 'var(--surface, #111827)', borderColor: 'var(--clr-border, #1e2d45)' }}>
          <Loader2 className="mr-2 h-4 w-4 animate-spin text-blue-300" />
          Loading report review...
        </div>
      )}

      {reviewError && (
        <div className="rounded-xl border border-red-500/25 bg-red-950/20 px-4 py-3">
          <div className="flex items-start gap-2">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-300" />
            <div>
              <p className="text-sm font-semibold text-red-100">Report review unavailable</p>
              <p className="mt-1 text-xs leading-5 text-red-200/80">{reviewError}</p>
            </div>
          </div>
        </div>
      )}

      <ClinicalHeader
        study={currentStudy}
        reportStatus={reportStatus}
        contextLabel={isDemoMode ? 'Demo case' : 'Report review'}
        visibleQaIssues={visibleQaIssues}
      />

      <WorkspaceTabBar activeTab={activeContextTab} onTabChange={setActiveContextTab} />

      <WorkspaceActions
        reportStatus={reportStatus}
        qaStatus={qaRequestStatus}
        notice={actionNotice}
        onSaveDraft={saveDraft}
        onRunQa={() => void runQa()}
        onMarkReady={markReadyToSign}
        runQaDisabled={Boolean(reviewId && (reviewLoading || reviewError))}
      />

      {/*
        Both views stay mounted and are toggled with `hidden` rather than conditionally rendered, so
        switching tabs never discards in-progress state elsewhere on the page — e.g. a longitudinal
        comparison already selected on the Prior Studies tab survives a trip back to the QA view.
      */}
      <div className={activeContextTab === 'clinical-workspace' ? undefined : 'hidden'}>
        <div className="grid gap-5 xl:grid-cols-[minmax(0,0.65fr)_minmax(36rem,1.9fr)_minmax(20rem,0.8fr)]">
          <ReportPanel report={currentReport} selectedIssue={selectedIssue} />
          <AnatomyPreview
            selection={selectedAnatomy}
            linkedIssueType={selectedAnatomy && isQaAnatomySelection && selectedIssue?.anatomySelection ? selectedIssue.type : null}
            conflictNote={selectedAnatomy && isQaAnatomySelection ? anatomyConflictNote(selectedIssue) : null}
            patientId={contextPatientId}
          />
          <QaPanel
            issues={qaIssues}
            dismissedIssueIds={dismissedIssueIds}
            selectedIssueId={selectedQaIssueId}
            requestStatus={qaRequestStatus}
            errorMessage={qaError}
            onRetry={() => void runQa()}
            onReviewIssue={reviewIssue}
            onDismissIssue={dismissIssue}
            onViewAnatomy={viewAnatomy}
          />
        </div>

        <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <StudyImagesPanel patientId={contextPatientId} />
          <ClinicalContextSidebar
            patientId={contextPatientId}
            onViewPriorReports={() => setActiveContextTab('prior-studies')}
            onAddNote={addClinicalNote}
          />
        </div>
      </div>

      <div className={activeContextTab === 'clinical-workspace' ? 'hidden' : undefined}>
        <ClinicalContextTabs
          activeTab={activeContextTab}
          priorStudies={demoClinicalWorkspace.priorStudies}
          timeline={demoClinicalWorkspace.timeline}
          audit={demoClinicalWorkspace.audit}
          isDemoMode={isDemoMode}
          currentReview={review}
          onViewAnatomy={viewLongitudinalAnatomy}
        />
      </div>
    </div>
  );
}

function mapReviewStatus(status: ReviewStatus): ClinicalReportStatus {
  if (status === 'SIGNED') return 'SIGNED';
  if (status === 'DRAFT') return 'DRAFT';
  return 'REVIEW_REQUIRED';
}

function studyFromReview(review: ReportReview): ClinicalWorkspaceStudy {
  const analysisLabel = readableLabel(review.analysisType ?? 'Report review');
  return {
    id: review.analysisId,
    accessionNumber: review.analysisId.slice(0, 8).toUpperCase(),
    studyType: analysisLabel,
    modality: analysisLabel,
    fileType: 'OTHER',
    studyDate: formatDate(review.createdAt),
    reportStatus: mapReviewStatus(review.status),
    patient: {
      id: review.patientId,
      fullName: review.patientName ?? 'Patient',
      medicalRecordNumber: 'Not recorded',
      dateOfBirth: 'Not recorded',
      ageLabel: 'Age not recorded',
    },
  };
}

function reportFromReview(review: ReportReview): DraftReport {
  const source = reportSource(review);
  const sectionBody = sectionBodyFromReview(review.sections ?? []);
  const hasBackendSections = Object.values(sectionBody).some((body) => body.length > 0);
  if (hasBackendSections) {
    return {
      id: review.id,
      radiologist: review.signedBy ?? review.claimedBy ?? 'Unassigned',
      createdAt: formatTime(review.createdAt),
      sections: [
        {
          id: 'findings',
          title: 'Findings',
          body: sectionBody.findings.length > 0
            ? sectionBody.findings
            : ['No findings text recorded.'],
        },
        {
          id: 'comparison',
          title: 'Comparison',
          body: sectionBody.comparison.length > 0
            ? sectionBody.comparison
            : ['Not recorded in this review.'],
        },
        {
          id: 'impression',
          title: 'Impression',
          body: sectionBody.impression.length > 0
            ? sectionBody.impression
            : ['No impression text recorded.'],
        },
      ],
      metadata: reportMetadataFromReview(review),
    };
  }

  const parsed = parseDraft(source);
  const findings = parsed?.findings.map(formatFinding).filter(Boolean) ?? [];
  const impression = parsed?.impression ? [parsed.impression] : [];

  return {
    id: review.id,
    radiologist: review.signedBy ?? review.claimedBy ?? 'Unassigned',
    createdAt: formatTime(review.createdAt),
    sections: [
      {
        id: 'findings',
        title: 'Findings',
        body: findings.length > 0
          ? findings
          : parsed
          ? ['No findings text recorded.']
          : ['No findings text recorded.'],
      },
      {
        id: 'comparison',
        title: 'Comparison',
        body: ['Not recorded in this review.'],
      },
      {
        id: 'impression',
        title: 'Impression',
        body: impression.length > 0 ? impression : ['No impression text recorded.'],
      },
    ],
    metadata: reportMetadataFromReview(review),
  };
}

function reportMetadataFromReview(review: ReportReview): DraftReport['metadata'] {
  const lastUpdatedIso = review.signedAt ?? review.claimedAt ?? null;
  return {
    reportStatus: mapReviewStatus(review.status),
    createdAt: formatDate(review.createdAt),
    createdBy: null,
    lastUpdatedAt: lastUpdatedIso ? formatDate(lastUpdatedIso) : null,
    lastUpdatedLabel: lastUpdatedIso ? formatDate(lastUpdatedIso) : formatDate(review.createdAt),
  };
}

function sectionBodyFromReview(sections: ReportReviewSection[]) {
  return sections.reduce(
    (body, section) => {
      const text = section.text?.trim();
      if (!text) return body;
      if (section.section === 'FINDINGS') body.findings.push(text);
      if (section.section === 'COMPARISON') body.comparison.push(text);
      if (section.section === 'IMPRESSION') body.impression.push(text);
      return body;
    },
    { findings: [] as string[], comparison: [] as string[], impression: [] as string[] }
  );
}

function reportSource(review: ReportReview): string | null {
  if (review.status === 'SIGNED' && review.finalContent) return review.finalContent;
  return review.draftContent ?? review.finalContent;
}

function formatFinding(finding: { region?: string; description?: string; severity?: string }): string {
  const region = finding.region ? `${finding.region}: ` : '';
  const severity = finding.severity ? ` (${finding.severity})` : '';
  return `${region}${finding.description ?? ''}${severity}`.trim();
}

function pendingStudy(reviewId: string | null): ClinicalWorkspaceStudy {
  return {
    id: reviewId ?? 'pending-review',
    accessionNumber: reviewId?.slice(0, 8).toUpperCase() ?? 'PENDING',
    studyType: 'Report review',
    modality: 'Pending',
    fileType: 'OTHER',
    studyDate: 'Loading',
    reportStatus: 'DRAFT',
    patient: {
      id: 'pending-patient',
      fullName: 'Loading report review',
      medicalRecordNumber: 'Pending',
      dateOfBirth: 'Pending',
      ageLabel: 'Pending',
    },
  };
}

function pendingReport(reviewId: string | null): DraftReport {
  return {
    id: reviewId ?? 'pending-report',
    radiologist: 'Pending',
    createdAt: 'pending',
    sections: [
      { id: 'findings', title: 'Findings', body: ['Loading report text.'] },
      { id: 'comparison', title: 'Comparison', body: ['Loading report text.'] },
      { id: 'impression', title: 'Impression', body: ['Loading report text.'] },
    ],
  };
}

function mapReportQaIssue(issue: ReportQaIssue): QaIssue {
  const normalizedEvidence = issue.evidence?.map(mapReportQaEvidence) ?? [];
  const fallbackEvidence = [
    issue.findingText ? { label: readableLabel(issue.sectionA ?? 'Findings'), text: issue.findingText } : null,
    issue.impressionText ? { label: readableLabel(issue.sectionB ?? 'Impression'), text: issue.impressionText } : null,
  ].filter((item): item is { label: string; text: string } => Boolean(item));
  const anatomyCandidates = anatomyCandidatesFromIssue(issue);

  return {
    id: issue.id,
    severity: issue.severity,
    type: issue.type,
    message: issue.message || 'Potential laterality conflict. Clinician review required.',
    recommendation: 'Review recommended. Clinician review required before sign-off.',
    evidence: normalizedEvidence.length > 0 ? normalizedEvidence : fallbackEvidence,
    anatomySelection: defaultAnatomyCandidate(anatomyCandidates),
    anatomyCandidates,
  };
}

/**
 * Prefers the backend anatomy targets. Each mapped structure stays tied to the evidence section it
 * came from, so a laterality conflict keeps both candidates instead of collapsing to one side.
 */
function anatomyCandidatesFromIssue(issue: ReportQaIssue): AnatomySelection[] {
  const fromTargets = (issue.evidence ?? [])
    .map(anatomySelectionFromEvidence)
    .filter((selection): selection is AnatomySelection => Boolean(selection));

  if (fromTargets.length > 0) return fromTargets;

  const reconstructed = anatomySelectionFromIssue(issue);
  return reconstructed ? [reconstructed] : [];
}

/** Defaults to the Impression evidence for conflicts, matching the review-first anatomy view. */
function defaultAnatomyCandidate(candidates: AnatomySelection[]): AnatomySelection | undefined {
  return candidates.find((candidate) => candidate.sourceLabel === 'Impression')
    ?? candidates.find((candidate) => candidate.sourceLabel === 'Findings')
    ?? candidates[0];
}

function qaPreviewSelection(issue: QaIssue | null): AnatomySelection | null {
  if (!issue?.anatomySelection) return null;
  return { ...issue.anatomySelection, sourceKind: 'QA' };
}

function anatomySelectionFromEvidence(evidence: ReportQaEvidence): AnatomySelection | null {
  const target = evidence.anatomyTarget;
  if (!target) return null;

  return {
    structure: target.structureCode,
    displayName: target.displayName,
    side: target.side,
    region: target.region,
    system: readableLabel(target.system),
    viewerKey: target.viewerKey ?? null,
    sourceLabel: readableLabel(evidence.sourceSection),
    sourceText: evidence.sourceText,
  };
}

/**
 * Builds a note when an issue maps to more than one structure. Names every mapped structure and its
 * source section without asserting which one is correct.
 */
function anatomyConflictNote(issue: QaIssue | null): string | null {
  const candidates = issue?.anatomyCandidates ?? [];
  if (candidates.length < 2) return null;

  const distinctSides = new Set(candidates.map((candidate) => candidate.side));
  if (distinctSides.size < 2) return null;

  const mapped = candidates
    .map((candidate) => `${candidate.sourceLabel ?? 'Source'}: ${candidate.displayName}`)
    .join(' \u2022 ');
  return `${mapped}. This report maps to more than one structure; the correct side is not determined by the system.`;
}

function mapReportQaEvidence(evidence: ReportQaEvidence) {
  return {
    label: readableLabel(evidence.sourceSection),
    normalizedLabel: normalizedEvidenceLabel(evidence),
    text: evidence.sourceText,
  };
}

function normalizedEvidenceLabel(evidence: ReportQaEvidence): string {
  const anatomy = evidence.anatomyText?.trim().toLowerCase()
    || (evidence.anatomy ? readableLabel(evidence.anatomy).toLowerCase() : null);
  const side = evidence.side !== 'UNSPECIFIED' ? readableLabel(evidence.side) : null;
  const location = [side, anatomy].filter(Boolean).join(' ');
  const region = evidence.region !== 'UNSPECIFIED' ? readableLabel(evidence.region) : null;

  return [readableLabel(evidence.findingType), location || null, region]
    .filter((part): part is string => Boolean(part))
    .join(' \u2022 ');
}

function anatomySelectionFromIssue(issue: ReportQaIssue): AnatomySelection | undefined {
  if (!issue.anatomyCode) return undefined;
  const side = issue.sideA ?? 'UNSPECIFIED';
  const structure = issue.anatomyCode;
  const region = issue.region ?? 'UNSPECIFIED';
  return {
    structure,
    displayName: `${readableLabel(side)} ${readableLabel(structure)}`.trim(),
    side,
    region,
    system: 'Reported anatomy',
    viewerKey: null,
    sourceLabel: readableLabel(issue.sectionA ?? 'Findings'),
    sourceText: issue.findingText ?? null,
  };
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

function formatTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'time not recorded';
  return new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' }).format(date);
}

function reportLoadFailureMessage(error: unknown): string {
  const status = (error as { response?: { status?: number } }).response?.status;
  if (status === 401 || status === 403) {
    return 'Your session is not authorized to open this report review.';
  }
  if (status === 404) {
    return 'This report review was not found in the current workspace.';
  }
  if (status && status >= 500) {
    return 'The server could not load this report review right now.';
  }
  return 'Network error while loading this report review.';
}

function qaFailureMessage(error: unknown): string {
  const status = (error as { response?: { status?: number } }).response?.status;
  if (status === 401) {
    return 'Your session expired before QA could run. Sign in again if this continues.';
  }
  if (status === 403) {
    return 'Your account is not allowed to run QA for this report review.';
  }
  if (status === 404) {
    return 'This report review was not found in the current workspace.';
  }
  if (status && status >= 500) {
    return 'The server could not run QA right now. Please retry.';
  }
  return 'Network error while running QA. Please retry.';
}
