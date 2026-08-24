import api from './api';
import type { ApiResponse, PagedResponse } from '@/types';

export type ReviewStatus = 'DRAFT' | 'IN_REVIEW' | 'SIGNED' | 'REJECTED' | 'AMENDED';
export type ReviewAction = 'ACCEPTED' | 'EDITED' | 'REJECTED';

export interface ReportReview {
  id: string;
  analysisId: string;
  patientId: string;
  patientName: string | null;
  analysisType: string | null;
  status: ReviewStatus;
  claimedBy: string | null;
  claimedAt: string | null;
  signedBy: string | null;
  signedAt: string | null;
  reviewAction: ReviewAction | null;
  rejectionReason: string | null;
  /** The model's output, frozen when the review opened. Raw JSON from the analysis. */
  draftContent: string | null;
  /** What the clinician signed. Narrative text, not JSON. */
  finalContent: string | null;
  amendsReviewId: string | null;
  createdAt: string;
}

export interface WorklistSummary {
  awaitingReview: number;
  inReview: number;
  signedToday: number;
  openEscalations: number;
  acceptedAllTime: number;
  editedAllTime: number;
  rejectedAllTime: number;
}

export interface CriticalEscalation {
  id: string;
  analysisId: string;
  patientId: string;
  patientName: string | null;
  urgency: 'URGENT' | 'CRITICAL';
  findingSummary: string;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'CLOSED';
  escalationLevel: number;
  lastNotifiedAt: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  actionTaken: string | null;
  createdAt: string;
}

/** The shape the analysis services write into draftContent. */
export interface DraftFinding {
  region?: string;
  description?: string;
  severity?: string;
  confidence?: number;
}

export interface ParsedDraft {
  findings: DraftFinding[];
  impression: string;
  icd10Codes: string[];
  recommendations: string[];
  urgency: string;
}

/**
 * Parses the frozen draft JSON.
 *
 * Returns null rather than a half-populated object when the shape is unreadable: a reviewer must
 * not be shown something that looks like "no findings" when it is really "we could not read this".
 */
export function parseDraft(draftContent: string | null): ParsedDraft | null {
  if (!draftContent) return null;
  try {
    const raw = JSON.parse(draftContent);
    return {
      findings: Array.isArray(raw.findings) ? raw.findings : [],
      impression: raw.impression ?? raw.interpretation ?? '',
      icd10Codes: (raw.icd10Codes ?? raw.icd10_codes ?? []).map((c: unknown) =>
        typeof c === 'string' ? c : String((c as { code?: string })?.code ?? '')
      ),
      recommendations: Array.isArray(raw.recommendations) ? raw.recommendations : [],
      urgency: (raw.urgency ?? 'ROUTINE').toUpperCase(),
    };
  } catch {
    return null;
  }
}

/**
 * Renders a draft as the narrative a clinician edits.
 *
 * The stored draft is JSON; asking a radiologist to correct JSON would be hostile, and the backend
 * stores the signed report as text precisely so the signed artefact is prose a human wrote.
 */
export function draftAsNarrative(draft: ParsedDraft | null, raw: string | null): string {
  if (!draft) return raw ?? '';

  const lines: string[] = [];

  if (draft.findings.length > 0) {
    lines.push('FINDINGS');
    draft.findings.forEach((f) => {
      const region = f.region ? `${f.region}: ` : '';
      const severity = f.severity ? ` (${f.severity})` : '';
      lines.push(`- ${region}${f.description ?? ''}${severity}`);
    });
    lines.push('');
  }

  if (draft.impression) {
    lines.push('IMPRESSION');
    lines.push(draft.impression);
    lines.push('');
  }

  if (draft.recommendations.length > 0) {
    lines.push('RECOMMENDATIONS');
    draft.recommendations.forEach((r) => lines.push(`- ${r}`));
    lines.push('');
  }

  if (draft.icd10Codes.length > 0) {
    lines.push(`ICD-10: ${draft.icd10Codes.join(', ')}`);
  }

  return lines.join('\n').trim();
}

export const reportService = {
  async worklist(page = 0, size = 20): Promise<PagedResponse<ReportReview>> {
    const res = await api.get<ApiResponse<PagedResponse<ReportReview>>>('/reports/worklist', {
      params: { page, size },
    });
    return res.data.data;
  },

  async summary(): Promise<WorklistSummary> {
    const res = await api.get<ApiResponse<WorklistSummary>>('/reports/summary');
    return res.data.data;
  },

  async get(reviewId: string): Promise<ReportReview> {
    const res = await api.get<ApiResponse<ReportReview>>(`/reports/${reviewId}`);
    return res.data.data;
  },

  async forPatient(patientId: string, page = 0, size = 20): Promise<PagedResponse<ReportReview>> {
    const res = await api.get<ApiResponse<PagedResponse<ReportReview>>>(
      `/reports/patient/${patientId}`,
      { params: { page, size } }
    );
    return res.data.data;
  },

  async claim(reviewId: string): Promise<ReportReview> {
    const res = await api.post<ApiResponse<ReportReview>>(`/reports/${reviewId}/claim`);
    return res.data.data;
  },

  /**
   * ACCEPTED signs the draft unchanged; EDITED requires the corrected narrative; REJECTED
   * requires a reason. The distinction is the training label as well as the clinical decision.
   */
  async sign(
    reviewId: string,
    action: ReviewAction,
    opts: { finalContent?: string; rejectionReason?: string } = {}
  ): Promise<ReportReview> {
    const res = await api.post<ApiResponse<ReportReview>>(`/reports/${reviewId}/sign`, {
      action,
      finalContent: opts.finalContent ?? null,
      rejectionReason: opts.rejectionReason ?? null,
    });
    return res.data.data;
  },

  async amend(reviewId: string, correctedContent: string): Promise<ReportReview> {
    const res = await api.post<ApiResponse<ReportReview>>(`/reports/${reviewId}/amend`, {
      correctedContent,
    });
    return res.data.data;
  },

  async criticalResults(): Promise<CriticalEscalation[]> {
    const res = await api.get<ApiResponse<CriticalEscalation[]>>('/reports/critical');
    return res.data.data;
  },

  async acknowledgeCritical(escalationId: string, actionTaken: string): Promise<CriticalEscalation> {
    const res = await api.post<ApiResponse<CriticalEscalation>>(
      `/reports/critical/${escalationId}/acknowledge`,
      { actionTaken }
    );
    return res.data.data;
  },
};
