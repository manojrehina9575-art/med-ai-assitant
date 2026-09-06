import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ClinicalWorkspacePage } from './ClinicalWorkspacePage';
import { WorklistPage } from './WorklistPage';
import { longitudinalApi } from '@/services/longitudinalApi';
import { reportQaApi } from '@/services/reportQaApi';
import {
  reportService,
  type ReportReview,
  type WorklistSummary,
} from '@/services/reportService';
import { useAuthStore } from '@/stores/authStore';
import type { PagedResponse } from '@/types';
import type {
  AnatomyTarget,
  FindingChangeType,
  LongitudinalResult,
  LongitudinalStructuredFinding,
  ReportQaEvidence,
  ReportQaResult,
} from '@/types/clinicalWorkspace';

vi.mock('@/services/longitudinalApi', () => ({
  longitudinalApi: {
    compareReports: vi.fn(),
  },
}));

vi.mock('@/services/reportQaApi', () => ({
  reportQaApi: {
    runReportQa: vi.fn(),
  },
}));

vi.mock('@/services/reportService', async () => {
  const actual = await vi.importActual<typeof import('@/services/reportService')>(
    '@/services/reportService'
  );

  return {
    ...actual,
    reportService: {
      ...actual.reportService,
      worklist: vi.fn(),
      summary: vi.fn(),
      criticalResults: vi.fn(),
      get: vi.fn(),
      forPatient: vi.fn(),
      claim: vi.fn(),
      sign: vi.fn(),
      acknowledgeCritical: vi.fn(),
    },
  };
});

const REVIEW_ID = '11111111-1111-4111-8111-111111111111';
const ANALYSIS_ID = '22222222-2222-4222-8222-222222222222';
const PATIENT_ID = '33333333-3333-4333-8333-333333333333';
const PRIOR_REVIEW_ID = '44444444-4444-4444-8444-444444444444';
const FINDING_TEXT = 'Comminuted fracture involving the proximal right humerus.';
const IMPRESSION_TEXT = 'Comminuted fracture of the proximal left humerus.';
const REAL_FINDINGS_TEXT = 'There is a comminuted fracture involving the proximal right humerus.';
const REAL_COMPARISON_TEXT = 'No prior study available.';
const REAL_IMPRESSION_TEXT = 'Comminuted fracture of the proximal left humerus.';
const REAL_PASTED_REPORT = `FINDINGS:
${REAL_FINDINGS_TEXT}

COMPARISON:
${REAL_COMPARISON_TEXT}

IMPRESSION:
${REAL_IMPRESSION_TEXT}`;
const DEMO_COMPARISON_ISSUE = 'No prior shoulder examination is available for interval comparison.';
const SAFE_NO_ISSUES_COPY = 'No potential QA issues detected by the current checks.';

describe('QA workflow regression coverage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    useAuthStore.getState().setAuth({
      accessToken: 'test-access-token',
      userId: 'doctor-user-id',
      tenantId: 'tenant-id',
      email: 'doctor@example.test',
      fullName: 'Dr. Mira Patel',
      role: 'DOCTOR',
      tenantName: 'QA Hospital',
    });
    vi.mocked(reportService.summary).mockResolvedValue(worklistSummary());
    vi.mocked(reportService.criticalResults).mockResolvedValue([]);
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([]));
  });

  afterEach(() => {
    cleanup();
    useAuthStore.getState().clear();
  });

  it('displays a real ReportReview in the worklist and links to its QA Workspace route', async () => {
    const user = userEvent.setup();
    const review = makeReview();
    vi.mocked(reportService.worklist).mockResolvedValue(pagedReviews([review]));

    render(
      <MemoryRouter>
        <WorklistPage />
      </MemoryRouter>
    );

    const worklistReview = await screen.findByRole('button', { name: /Asha Menon/i });
    expect(screen.getByText('Reading worklist')).toBeInTheDocument();

    await user.click(worklistReview);

    const qaWorkspaceLink = await screen.findByRole('link', { name: /QA Workspace/i });
    expect(qaWorkspaceLink).toHaveAttribute('href', `/clinical-workspace/${REVIEW_ID}`);
    expect(screen.getAllByText('Asha Menon').length).toBeGreaterThan(0);
  });

  it('runs QA for the route review id and renders the returned laterality evidence', async () => {
    const user = userEvent.setup();
    const qaResponse = deferred<ReportQaResult>();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportQaApi.runReportQa).mockReturnValue(qaResponse.promise);

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    expect(reportService.get).toHaveBeenCalledWith(REVIEW_ID);
    expect(screen.getByText('QA not run yet')).toBeInTheDocument();
    expect(screen.queryByText(DEMO_COMPARISON_ISSUE)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));

    expect(reportQaApi.runReportQa).toHaveBeenCalledTimes(1);
    expect(reportQaApi.runReportQa).toHaveBeenCalledWith(REVIEW_ID);
    expect(screen.getByRole('button', { name: /Running QA/i })).toBeDisabled();
    expect(screen.getByText('Running report QA')).toBeInTheDocument();

    qaResponse.resolve(lateralityQaResult());

    expect((await screen.findAllByText('LATERALITY_CONFLICT')).length).toBeGreaterThan(0);
    expect(screen.getByText('High')).toBeInTheDocument();
    expect(screen.getAllByText(/Potential laterality conflict/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(FINDING_TEXT, { exact: false }).length).toBeGreaterThan(0);
    expect(screen.getAllByText(IMPRESSION_TEXT, { exact: false }).length).toBeGreaterThan(0);
    expect(screen.queryByText('COMPARISON_GAP')).not.toBeInTheDocument();
    expect(screen.queryByText(DEMO_COMPARISON_ISSUE)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Dismiss/i }));

    await waitFor(() => expect(screen.queryByText('LATERALITY_CONFLICT')).not.toBeInTheDocument());
    expect(screen.getByText('No visible QA issues')).toBeInTheDocument();
    expect(screen.getByText('Dismissed issues can be restored with Run QA.')).toBeInTheDocument();
    expect(reportQaApi.runReportQa).toHaveBeenCalledTimes(1);
  });

  it('renders backend-parsed sections for a real pasted report and defaults QA anatomy to impression', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makePastedReportReview());
    vi.mocked(reportQaApi.runReportQa).mockResolvedValue(realLateralityQaResultWithAnatomyTargets());

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');

    const findingsSection = sectionNamed('Findings');
    const comparisonSection = sectionNamed('Comparison');
    const impressionSection = sectionNamed('Impression');

    expect(within(findingsSection).getByText(REAL_FINDINGS_TEXT)).toBeInTheDocument();
    expect(within(findingsSection).queryByText(/COMPARISON:/i)).not.toBeInTheDocument();
    expect(within(findingsSection).queryByText(/IMPRESSION:/i)).not.toBeInTheDocument();
    expect(within(comparisonSection).getByText(REAL_COMPARISON_TEXT)).toBeInTheDocument();
    expect(within(impressionSection).getByText(REAL_IMPRESSION_TEXT)).toBeInTheDocument();

    expect(screen.queryByText('No acute dislocation.')).not.toBeInTheDocument();
    expect(screen.queryByText('No prior shoulder examination is available.')).not.toBeInTheDocument();
    expect(screen.queryByText('Right Shoulder')).not.toBeInTheDocument();
    expect(screen.getByText('No anatomy selected')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));

    expect((await screen.findAllByText('LATERALITY_CONFLICT')).length).toBeGreaterThan(0);
    expect(screen.getByText('High')).toBeInTheDocument();
    expect(screen.getAllByText(REAL_FINDINGS_TEXT, { exact: false }).length).toBeGreaterThan(0);
    expect(screen.getAllByText(REAL_IMPRESSION_TEXT, { exact: false }).length).toBeGreaterThan(0);
    expect(await screen.findByText('viewerKey: skeleton.humerus.left')).toBeInTheDocument();
    expect(screen.getByText('Left proximal humerus')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Impression')).toBeInTheDocument();
    expect(screen.queryByText('Right Shoulder')).not.toBeInTheDocument();

    const findingsAnatomyAction = screen.getByRole('button', { name: /View Findings anatomy/i });
    expect(screen.getByRole('button', { name: /View Impression anatomy/i })).toBeInTheDocument();

    await user.click(findingsAnatomyAction);

    expect(await screen.findByText('viewerKey: skeleton.humerus.right')).toBeInTheDocument();
    expect(screen.getByText('Right proximal humerus')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /View Impression anatomy/i }));

    expect(await screen.findByText('viewerKey: skeleton.humerus.left')).toBeInTheDocument();
    expect(screen.getByText('Left proximal humerus')).toBeInTheDocument();
  });

  it('uses the backend anatomy target for View Anatomy instead of reconstructing anatomy strings', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportQaApi.runReportQa).mockResolvedValue(lateralityQaResultWithAnatomyTargets());

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));

    const findingsAnatomyAction = await screen.findByRole('button', { name: /View Findings anatomy/i });
    expect(screen.getByRole('button', { name: /View Impression anatomy/i })).toBeInTheDocument();
    // The reconstructed-from-strings label must not be what the panel renders.
    expect(screen.queryByText('Right Humerus')).not.toBeInTheDocument();

    await user.click(findingsAnatomyAction);

    expect(await screen.findByText('Right proximal humerus')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Findings')).toBeInTheDocument();
    expect(screen.getByText('Skeletal')).toBeInTheDocument();
    expect(screen.getByText('HUMERUS')).toBeInTheDocument();
    expect(
      screen.getByText('Anatomy updated to the mapped structure from the Findings evidence.')
    ).toBeInTheDocument();
  });

  it('offers both conflicting anatomy targets without claiming one side is correct', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportQaApi.runReportQa).mockResolvedValue(lateralityQaResultWithAnatomyTargets());

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));

    await user.click(await screen.findByRole('button', { name: /View Impression anatomy/i }));

    expect(await screen.findByText('Left proximal humerus')).toBeInTheDocument();
    expect(screen.queryByText('Right proximal humerus')).not.toBeInTheDocument();
    expect(screen.getByText('Source finding: Impression')).toBeInTheDocument();
    expect(screen.getByText('Conflicting mapped structures')).toBeInTheDocument();
    expect(
      screen.getByText(/Findings: Right proximal humerus .* Impression: Left proximal humerus/i)
    ).toBeInTheDocument();
    // Stated both on the issue card and on the anatomy panel.
    expect(screen.getAllByText(/the correct side is not determined by the system/i).length).toBe(2);
    expect(screen.queryByText(/correct anatomical diagnosis/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/verified injury location/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /View Findings anatomy/i }));

    expect(await screen.findByText('Right proximal humerus')).toBeInTheDocument();
    expect(screen.queryByText('Left proximal humerus')).not.toBeInTheDocument();
  });

  it('drives the 3D viewer with the backend viewer key for each conflicting QA side', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportQaApi.runReportQa).mockResolvedValue(lateralityQaResultWithAnatomyTargets());

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));
    await user.click(await screen.findByRole('button', { name: /View Findings anatomy/i }));

    // The viewer resolved the skeletal target from the key, not from the display name.
    expect(await screen.findByText('viewerKey: skeleton.humerus.right')).toBeInTheDocument();
    expect(screen.getByText(/Mapped structures: Humerus\./)).toBeInTheDocument();
    expect(screen.getByText('Right proximal humerus')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /View Impression anatomy/i }));

    expect(await screen.findByText('viewerKey: skeleton.humerus.left')).toBeInTheDocument();
    expect(screen.queryByText('viewerKey: skeleton.humerus.right')).not.toBeInTheDocument();
    expect(screen.getByText('Left proximal humerus')).toBeInTheDocument();
    // The viewer shows a structure; it never states which side is clinically correct.
    expect(screen.queryByText(/clinically correct/i)).not.toBeInTheDocument();
  });

  it('falls back to issue-level anatomy when the backend returns no anatomy target', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportQaApi.runReportQa).mockResolvedValue(lateralityQaResult());

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));

    const anatomyAction = await screen.findByRole('button', { name: /^View Anatomy$/i });
    expect(screen.queryByRole('button', { name: /View Impression anatomy/i })).not.toBeInTheDocument();

    await user.click(anatomyAction);

    expect(await screen.findByText('Right Humerus')).toBeInTheDocument();
    expect(screen.queryByText('Conflicting mapped structures')).not.toBeInTheDocument();
    // No viewer key means no single structure to render, and the metadata still stands.
    expect(
      screen.getByText('No single 3D structure is available for this mapped finding.')
    ).toBeInTheDocument();
    expect(screen.getByText('HUMERUS')).toBeInTheDocument();
    expect(screen.getByText('PROXIMAL')).toBeInTheDocument();
  });

  it('renders a safe no-issue state without leaking demo QA issues', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportQaApi.runReportQa).mockResolvedValue(noIssuesQaResult());

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));

    expect(await screen.findByText('No visible QA issues')).toBeInTheDocument();
    expect(screen.getAllByText(SAFE_NO_ISSUES_COPY).length).toBeGreaterThan(0);
    expect(screen.queryByText('LATERALITY_CONFLICT')).not.toBeInTheDocument();
    expect(screen.queryByText('COMPARISON_GAP')).not.toBeInTheDocument();
    expect(screen.queryByText(DEMO_COMPARISON_ISSUE)).not.toBeInTheDocument();
  });

  it('shows a safe QA error and retries against the same review id', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportQaApi.runReportQa)
      .mockRejectedValueOnce({
        response: { status: 500 },
        message: 'Internal stack trace should not render',
        stack: 'Error: internal-service-details',
      })
      .mockResolvedValueOnce(noIssuesQaResult());

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));

    expect(await screen.findByText('QA request failed')).toBeInTheDocument();
    expect(screen.getByText('The server could not run QA right now. Please retry.')).toBeInTheDocument();
    expect(screen.queryByText(/Internal stack trace/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/internal-service-details/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Retry/i }));

    expect(reportQaApi.runReportQa).toHaveBeenCalledTimes(2);
    expect(reportQaApi.runReportQa).toHaveBeenNthCalledWith(1, REVIEW_ID);
    expect(reportQaApi.runReportQa).toHaveBeenNthCalledWith(2, REVIEW_ID);
    expect(await screen.findByText('No visible QA issues')).toBeInTheDocument();
  });

  it('loads signed prior reviews for the current patient and keeps prior choice explicit', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(
      pagedReviews([
        makeSignedPriorReview(),
        makeReview(),
        makeSignedPriorReview({
          id: '55555555-5555-4555-8555-555555555555',
          signedAt: '2026-09-04T08:00:00.000Z',
          createdAt: '2026-09-04T07:30:00.000Z',
        }),
        makeSignedPriorReview({
          id: '66666666-6666-4666-8666-666666666666',
          finalContent: null,
        }),
      ])
    );

    renderClinicalWorkspace(REVIEW_ID);

    await screen.findByText('Report review loaded. Run QA manually when ready.');
    await user.click(screen.getByRole('button', { name: /^Prior Studies$/i }));
    const select = await screen.findByLabelText('Prior report');
    await screen.findByRole('option', { name: /44444444/i });

    expect(reportService.forPatient).toHaveBeenCalledWith(PATIENT_ID, 0, 20);
    expect(within(select).getAllByRole('option')).toHaveLength(2);
    expect(select).toHaveValue('');

    await user.selectOptions(select, PRIOR_REVIEW_ID);

    expect(select).toHaveValue(PRIOR_REVIEW_ID);
    expect(screen.getByText(/Ready to compare against/i)).toBeInTheDocument();
    expect(longitudinalApi.compareReports).not.toHaveBeenCalled();
  });

  it('calls the longitudinal endpoint only after explicit selection and renders measured increase evidence', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(longitudinalResult('INCREASED'));

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    expect(longitudinalApi.compareReports).toHaveBeenCalledTimes(1);
    expect(longitudinalApi.compareReports).toHaveBeenCalledWith(REVIEW_ID, PRIOR_REVIEW_ID);
    expect(await screen.findByText('INCREASED')).toBeInTheDocument();
    expect(screen.getByText('Potential interval increase')).toBeInTheDocument();
    expect(screen.getByText('NODULE')).toBeInTheDocument();
    expect(screen.getByText('LUNG')).toBeInTheDocument();
    expect(screen.getByText('LEFT')).toBeInTheDocument();
    expect(screen.getByText('UPPER')).toBeInTheDocument();
    expect(screen.getAllByText('5 mm').length).toBeGreaterThan(0);
    expect(screen.getAllByText('8 mm').length).toBeGreaterThan(0);
    expect(screen.getByText('Source evidence')).toBeInTheDocument();
    expect(screen.getByText(/Prior report described a 5 mm left upper lobe nodule/i)).toBeInTheDocument();
    expect(screen.getByText(/Current report describes an 8 mm left upper lobe nodule/i)).toBeInTheDocument();
  });

  it('shows a non-destructive loading state while longitudinal comparison is running', async () => {
    const user = userEvent.setup();
    const comparison = deferred<LongitudinalResult>();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockReturnValue(comparison.promise);

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    expect(screen.getByRole('button', { name: /Comparing/i })).toBeDisabled();
    expect(screen.getByText('Comparing reports...')).toBeInTheDocument();

    comparison.resolve(longitudinalResult('INCREASED'));
    expect(await screen.findByText('INCREASED')).toBeInTheDocument();
  });

  it.each([
    ['NEW', 'New / not matched in prior'],
    ['RESOLVED', 'Resolved / not present in current'],
    ['UNCHANGED', 'No measured change detected'],
    ['DECREASED', 'Potential interval decrease'],
    ['CHANGED', 'Finding attributes changed'],
    ['INDETERMINATE', 'Comparison indeterminate'],
  ] satisfies [FindingChangeType, string][])('renders %s comparison output with the backend label only', async (changeType, label) => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(longitudinalResult(changeType));

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    expect(await screen.findByText(changeType)).toBeInTheDocument();
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.queryByText(/clinically verified/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/clinically identical/i)).not.toBeInTheDocument();
  });

  it('renders one View Anatomy action for a comparison whose prior and current anatomy match', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(
      longitudinalResultWithAnatomy('INCREASED')
    );

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    expect(await screen.findByText('INCREASED')).toBeInTheDocument();
    const anatomyAction = screen.getByRole('button', { name: /^View Anatomy$/i });
    expect(screen.queryByRole('button', { name: /View Prior Anatomy/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /View Current Anatomy/i })).not.toBeInTheDocument();

    await user.click(anatomyAction);

    // The backend displayName is used verbatim; nothing is rebuilt from LUNG/LEFT/UPPER strings.
    expect(await screen.findByText('Left upper lung')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Current report')).toBeInTheDocument();
    expect(screen.getByText('Comparison: Potential interval increase')).toBeInTheDocument();
    expect(screen.getByText('Respiratory')).toBeInTheDocument();
    expect(screen.queryByText('Left Lung')).not.toBeInTheDocument();
    expect(screen.getByText('Potential interval increase')).toBeInTheDocument();
  });

  it('labels prior and current actions separately when the comparison anatomy differs', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(
      longitudinalResultWithAnatomy('CHANGED', { priorSide: 'RIGHT' })
    );

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    const priorAction = await screen.findByRole('button', { name: /View Prior Anatomy/i });
    expect(screen.getByRole('button', { name: /View Current Anatomy/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^View Anatomy$/i })).not.toBeInTheDocument();

    await user.click(priorAction);

    expect(await screen.findByText('Right upper lung')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Prior report')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /View Current Anatomy/i }));

    expect(await screen.findByText('Left upper lung')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Current report')).toBeInTheDocument();
    expect(screen.queryByText('Right upper lung')).not.toBeInTheDocument();
  });

  it('offers only the current anatomy action for a NEW comparison with no prior target', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(longitudinalResultWithAnatomy('NEW'));

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    expect(await screen.findByText('NEW')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^View Anatomy$/i }));

    expect(await screen.findByText('Left upper lung')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Current report')).toBeInTheDocument();
    expect(screen.getByText('Comparison: New / not matched in prior')).toBeInTheDocument();
  });

  it('renders a comparison without anatomy targets normally and offers no anatomy action', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(longitudinalResult('INCREASED'));

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    expect(await screen.findByText('INCREASED')).toBeInTheDocument();
    expect(screen.getByText('Potential interval increase')).toBeInTheDocument();
    expect(screen.getAllByText('5 mm').length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /View Anatomy/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /View Prior Anatomy/i })).not.toBeInTheDocument();
  });

  it('sends a longitudinal anatomy target to the same viewer and reports the lung honestly when WebGL is unavailable', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(
      longitudinalResultWithAnatomy('INCREASED')
    );

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));
    await user.click(await screen.findByRole('button', { name: /^View Anatomy$/i }));

    expect(await screen.findByText('viewerKey: respiratory.lung.left')).toBeInTheDocument();
    // The lung is a real, supported 3D target now; jsdom has no WebGL, so it falls back honestly
    // to the "3D view unavailable" state rather than the old "not supported yet" one.
    expect(
      screen.getByText('This browser or environment cannot display the 3D anatomy view.')
    ).toBeInTheDocument();
    expect(screen.getByText('Mapped structures: Lung.')).toBeInTheDocument();

    // Unsupported-in-this-environment 3D must never remove the mapped-structure metadata.
    expect(screen.getByText('Left upper lung')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Current report')).toBeInTheDocument();
    expect(screen.getByText('Comparison: Potential interval increase')).toBeInTheDocument();
    // Also rendered by the comparison row, hence the multi-match query.
    expect(screen.getAllByText('LUNG').length).toBeGreaterThan(0);
    expect(screen.getAllByText('UPPER').length).toBeGreaterThan(0);
  });

  it('keeps QA and longitudinal anatomy selections in the one shared preview', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(reportQaApi.runReportQa).mockResolvedValue(lateralityQaResultWithAnatomyTargets());
    vi.mocked(longitudinalApi.compareReports).mockResolvedValue(
      longitudinalResultWithAnatomy('INCREASED')
    );

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Run QA$/i }));
    await user.click(await screen.findByRole('button', { name: /View Findings anatomy/i }));

    expect(await screen.findByText('Right proximal humerus')).toBeInTheDocument();
    expect(screen.getByText('Conflicting mapped structures')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Compare$/i }));
    await user.click(await screen.findByRole('button', { name: /^View Anatomy$/i }));

    // Same preview, now showing the comparison target and no QA-specific context.
    expect(await screen.findByText('Left upper lung')).toBeInTheDocument();
    expect(screen.queryByText('Right proximal humerus')).not.toBeInTheDocument();
    expect(screen.queryByText('Conflicting mapped structures')).not.toBeInTheDocument();

    // QA candidates remain available and still offer both conflicting sides.
    expect(screen.getByRole('button', { name: /View Findings anatomy/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /View Impression anatomy/i })).toBeInTheDocument();
  });

  it('shows a safe longitudinal API error and retries with the selected ids', async () => {
    const user = userEvent.setup();
    vi.mocked(reportService.get).mockResolvedValue(makeReview());
    vi.mocked(reportService.forPatient).mockResolvedValue(pagedReviews([makeSignedPriorReview()]));
    vi.mocked(longitudinalApi.compareReports)
      .mockRejectedValueOnce({
        response: { status: 500 },
        message: 'Internal longitudinal stack trace should not render',
        stack: 'LongitudinalService.internal.details',
      })
      .mockResolvedValueOnce(longitudinalResult('UNCHANGED'));

    await renderClinicalWorkspaceWithPrior(user);
    await user.click(screen.getByRole('button', { name: /^Compare$/i }));

    expect(await screen.findByText('Longitudinal comparison failed')).toBeInTheDocument();
    expect(screen.getByText('The server could not compare these reports right now. Please retry.')).toBeInTheDocument();
    expect(screen.queryByText(/Internal longitudinal stack trace/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/LongitudinalService\.internal/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Retry/i }));

    expect(longitudinalApi.compareReports).toHaveBeenCalledTimes(2);
    expect(longitudinalApi.compareReports).toHaveBeenNthCalledWith(1, REVIEW_ID, PRIOR_REVIEW_ID);
    expect(longitudinalApi.compareReports).toHaveBeenNthCalledWith(2, REVIEW_ID, PRIOR_REVIEW_ID);
    expect(await screen.findByText('UNCHANGED')).toBeInTheDocument();
  });

  it('does not call real longitudinal services in demo mode', () => {
    renderDemoClinicalWorkspace();

    expect(screen.getByText('Demo supporting data for workspace layout')).toBeInTheDocument();
    expect(reportService.get).not.toHaveBeenCalled();
    expect(reportService.forPatient).not.toHaveBeenCalled();
    expect(longitudinalApi.compareReports).not.toHaveBeenCalled();
    expect(screen.getByText(DEMO_COMPARISON_ISSUE)).toBeInTheDocument();
  });
});

function renderClinicalWorkspace(reviewId: string) {
  render(
    <MemoryRouter initialEntries={[`/clinical-workspace/${reviewId}`]}>
      <Routes>
        <Route path="/clinical-workspace/:reviewId" element={<ClinicalWorkspacePage />} />
      </Routes>
    </MemoryRouter>
  );
}

async function renderClinicalWorkspaceWithPrior(user: ReturnType<typeof userEvent.setup>) {
  renderClinicalWorkspace(REVIEW_ID);
  await screen.findByText('Report review loaded. Run QA manually when ready.');
  await user.click(screen.getByRole('button', { name: /^Prior Studies$/i }));
  const select = await screen.findByLabelText('Prior report');
  await screen.findByRole('option', { name: /44444444/i });
  await user.selectOptions(select, PRIOR_REVIEW_ID);
}

function renderDemoClinicalWorkspace() {
  render(
    <MemoryRouter initialEntries={['/clinical-workspace']}>
      <Routes>
        <Route path="/clinical-workspace" element={<ClinicalWorkspacePage />} />
      </Routes>
    </MemoryRouter>
  );
}

function sectionNamed(name: 'Findings' | 'Comparison' | 'Impression'): HTMLElement {
  const heading = screen.getByRole('heading', { name });
  const section = heading.closest('section');
  expect(section).not.toBeNull();
  return section as HTMLElement;
}

function makeReview(overrides: Partial<ReportReview> = {}): ReportReview {
  return {
    id: REVIEW_ID,
    analysisId: ANALYSIS_ID,
    patientId: PATIENT_ID,
    patientName: 'Asha Menon',
    analysisType: 'IMAGE_ANALYSIS',
    status: 'DRAFT',
    claimedBy: null,
    claimedAt: null,
    signedBy: null,
    signedAt: null,
    reviewAction: null,
    rejectionReason: null,
    draftContent: JSON.stringify({
      findings: [{ description: FINDING_TEXT }],
      impression: IMPRESSION_TEXT,
      urgency: 'ROUTINE',
      recommendations: [],
      icd10Codes: [],
    }),
    finalContent: null,
    amendsReviewId: null,
    createdAt: '2026-09-03T08:00:00.000Z',
    ...overrides,
  };
}

function makePastedReportReview(): ReportReview {
  return makeReview({
    draftContent: REAL_PASTED_REPORT,
    sections: [
      { section: 'FINDINGS', text: REAL_FINDINGS_TEXT },
      { section: 'COMPARISON', text: REAL_COMPARISON_TEXT },
      { section: 'IMPRESSION', text: REAL_IMPRESSION_TEXT },
    ],
  });
}

function makeSignedPriorReview(overrides: Partial<ReportReview> = {}): ReportReview {
  return makeReview({
    id: PRIOR_REVIEW_ID,
    analysisId: '77777777-7777-4777-8777-777777777777',
    status: 'SIGNED',
    signedBy: 'doctor-user-id',
    signedAt: '2026-09-02T08:00:00.000Z',
    draftContent: null,
    finalContent: 'FINDINGS\nPrior report described a 5 mm left upper lobe nodule.\nIMPRESSION\nStable pulmonary nodule.',
    createdAt: '2026-09-02T07:30:00.000Z',
    ...overrides,
  });
}

function worklistSummary(): WorklistSummary {
  return {
    awaitingReview: 1,
    inReview: 0,
    signedToday: 0,
    openEscalations: 0,
    acceptedAllTime: 0,
    editedAllTime: 0,
    rejectedAllTime: 0,
  };
}

function pagedReviews(content: ReportReview[]): PagedResponse<ReportReview> {
  return {
    content,
    page: 0,
    size: 50,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    last: true,
  };
}

function longitudinalFinding(overrides: Partial<LongitudinalStructuredFinding> = {}): LongitudinalStructuredFinding {
  return {
    id: 'finding-current-1',
    findingType: 'NODULE',
    anatomy: 'LUNG',
    anatomyText: 'lung',
    side: 'LEFT',
    region: 'UPPER',
    status: 'PRESENT',
    certainty: 'ASSERTED',
    measurement: 8,
    unit: 'mm',
    sourceSection: 'FINDINGS',
    sourceText: 'Current report describes an 8 mm left upper lobe nodule.',
    ...overrides,
  };
}

function longitudinalResult(changeType: FindingChangeType): LongitudinalResult {
  const priorFinding = longitudinalFinding({
    id: 'finding-prior-1',
    measurement: 5,
    sourceText: 'Prior report described a 5 mm left upper lobe nodule.',
  });
  const currentFinding = longitudinalFinding();
  const comparison: LongitudinalResult['comparisons'][number] = {
    priorFinding,
    currentFinding,
    changeType,
    priorMeasurementMm: 5,
    currentMeasurementMm: 8,
    measurementDeltaMm: 3,
    explanation: 'Deterministic comparison returned by the backend.',
  };

  if (changeType === 'NEW') {
    comparison.priorFinding = null;
    comparison.priorMeasurementMm = null;
    comparison.measurementDeltaMm = null;
  }
  if (changeType === 'RESOLVED') {
    comparison.currentFinding = null;
    comparison.currentMeasurementMm = null;
    comparison.measurementDeltaMm = null;
  }
  if (changeType === 'UNCHANGED') {
    comparison.currentFinding = longitudinalFinding({ measurement: 5 });
    comparison.currentMeasurementMm = 5;
    comparison.measurementDeltaMm = 0;
  }
  if (changeType === 'DECREASED') {
    comparison.currentFinding = longitudinalFinding({ measurement: 3 });
    comparison.currentMeasurementMm = 3;
    comparison.measurementDeltaMm = -2;
  }
  if (changeType === 'INDETERMINATE') {
    comparison.currentFinding = longitudinalFinding({ measurement: null, unit: null });
    comparison.priorFinding = longitudinalFinding({ id: 'finding-prior-1', measurement: null, unit: null });
    comparison.currentMeasurementMm = null;
    comparison.priorMeasurementMm = null;
    comparison.measurementDeltaMm = null;
  }

  return {
    currentReportId: REVIEW_ID,
    priorReportId: PRIOR_REVIEW_ID,
    comparisons: [comparison],
    summary: summaryFor(changeType),
    evaluatedAt: '2026-09-03T08:07:00.000Z',
  };
}

function lungAnatomyTarget(side: 'LEFT' | 'RIGHT' = 'LEFT'): AnatomyTarget {
  return {
    system: 'RESPIRATORY',
    structureCode: 'LUNG',
    displayName: `${side === 'LEFT' ? 'Left' : 'Right'} upper lung`,
    side,
    region: 'UPPER',
    viewerKey: `respiratory.lung.${side.toLowerCase()}`,
    parentStructureCode: null,
    sourceAnatomy: 'LUNG',
  };
}

/** Longitudinal result enriched the way the backend enricher does. */
function longitudinalResultWithAnatomy(
  changeType: FindingChangeType,
  options: { priorSide?: 'LEFT' | 'RIGHT' } = {}
): LongitudinalResult {
  const base = longitudinalResult(changeType);
  const comparison = base.comparisons[0];

  return {
    ...base,
    comparisons: [
      {
        ...comparison,
        currentAnatomyTarget: comparison.currentFinding ? lungAnatomyTarget('LEFT') : null,
        priorAnatomyTarget: comparison.priorFinding
          ? lungAnatomyTarget(options.priorSide ?? 'LEFT')
          : null,
      },
    ],
  };
}

function summaryFor(changeType: FindingChangeType): LongitudinalResult['summary'] {
  const summary = {
    newFindings: 0,
    resolvedFindings: 0,
    increasedFindings: 0,
    decreasedFindings: 0,
    unchangedFindings: 0,
    changedFindings: 0,
    indeterminateFindings: 0,
  };

  if (changeType === 'NEW') summary.newFindings = 1;
  if (changeType === 'RESOLVED') summary.resolvedFindings = 1;
  if (changeType === 'INCREASED') summary.increasedFindings = 1;
  if (changeType === 'DECREASED') summary.decreasedFindings = 1;
  if (changeType === 'UNCHANGED') summary.unchangedFindings = 1;
  if (changeType === 'CHANGED') summary.changedFindings = 1;
  if (changeType === 'INDETERMINATE') summary.indeterminateFindings = 1;

  return summary;
}

function lateralityQaResult(): ReportQaResult {
  return {
    reportId: REVIEW_ID,
    status: 'REVIEW_RECOMMENDED',
    issueCount: 1,
    evaluatedAt: '2026-09-03T08:05:00.000Z',
    issues: [
      {
        id: `${REVIEW_ID}:laterality:0`,
        type: 'LATERALITY_CONFLICT',
        severity: 'HIGH',
        message: 'Potential laterality conflict. Clinician review required.',
        findingText: FINDING_TEXT,
        impressionText: IMPRESSION_TEXT,
        sectionA: 'FINDINGS',
        sectionB: 'IMPRESSION',
        sideA: 'RIGHT',
        sideB: 'LEFT',
        anatomyCode: 'HUMERUS',
        region: 'PROXIMAL',
        confidence: 1,
        detector: 'LateralityRule',
        detectorVersion: '1.0.0',
      },
    ],
  };
}

function anatomyEvidence(
  sourceSection: 'FINDINGS' | 'IMPRESSION',
  side: 'RIGHT' | 'LEFT',
  sourceText: string
): ReportQaEvidence {
  return {
    sourceSection,
    findingType: 'FRACTURE',
    anatomy: 'HUMERUS',
    anatomyText: 'humerus',
    side,
    region: 'PROXIMAL',
    status: 'PRESENT',
    certainty: 'ASSERTED',
    sourceText,
    anatomyTarget: {
      system: 'SKELETAL',
      structureCode: 'HUMERUS',
      displayName: `${side === 'RIGHT' ? 'Right' : 'Left'} proximal humerus`,
      side,
      region: 'PROXIMAL',
      viewerKey: `skeleton.humerus.${side.toLowerCase()}`,
      parentStructureCode: null,
      sourceAnatomy: 'HUMERUS',
    },
  };
}

function lateralityQaResultWithAnatomyTargets(): ReportQaResult {
  const base = lateralityQaResult();
  return {
    ...base,
    issues: [
      {
        ...base.issues[0],
        evidence: [
          anatomyEvidence('FINDINGS', 'RIGHT', FINDING_TEXT),
          anatomyEvidence('IMPRESSION', 'LEFT', IMPRESSION_TEXT),
        ],
      },
    ],
  };
}

function realLateralityQaResultWithAnatomyTargets(): ReportQaResult {
  return {
    reportId: REVIEW_ID,
    status: 'REVIEW_RECOMMENDED',
    issueCount: 1,
    evaluatedAt: '2026-09-03T08:05:00.000Z',
    issues: [
      {
        id: `${REVIEW_ID}:laterality:real-pasted`,
        type: 'LATERALITY_CONFLICT',
        severity: 'HIGH',
        message: 'Potential laterality conflict. Clinician review required.',
        findingText: REAL_FINDINGS_TEXT,
        impressionText: REAL_IMPRESSION_TEXT,
        sectionA: 'FINDINGS',
        sectionB: 'IMPRESSION',
        sideA: 'RIGHT',
        sideB: 'LEFT',
        anatomyCode: 'HUMERUS',
        region: 'PROXIMAL',
        confidence: 0.9,
        detector: 'LateralityRule',
        detectorVersion: '1.0.0',
        evidence: [
          anatomyEvidence('FINDINGS', 'RIGHT', REAL_FINDINGS_TEXT),
          anatomyEvidence('IMPRESSION', 'LEFT', REAL_IMPRESSION_TEXT),
        ],
      },
    ],
  };
}

function noIssuesQaResult(): ReportQaResult {
  return {
    reportId: REVIEW_ID,
    status: 'NO_ISSUES',
    issueCount: 0,
    evaluatedAt: '2026-09-03T08:06:00.000Z',
    issues: [],
  };
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });

  return { promise, resolve, reject };
}
