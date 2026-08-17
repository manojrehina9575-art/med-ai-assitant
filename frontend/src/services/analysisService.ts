import api from './api';
import type { ApiResponse, PagedResponse } from '@/types';

// Normalized structured types
export interface NormalizedFinding {
  region: string;
  description: string;
  severity: string;
  confidence: number;
}

export interface NormalizedIcd10 {
  code: string;
  description: string;
}

export interface ImageAnalysisResult {
  findings: NormalizedFinding[];
  impression: string;
  icd10Codes: NormalizedIcd10[];
  recommendations: string[];
  urgency: string;
}

export interface BloodReportParameter {
  name: string;
  value: number;
  unit: string;
  referenceRange?: string;
  flag: string;
}

export interface BloodReportResult {
  testName: string;
  parameters: BloodReportParameter[];
  interpretation: string;
  flags: string[];
}

export interface DiagnosisRecommendation {
  diagnosis: string;
  icd10Code: string;
  confidence: number;
  supportingEvidence: string[];
}

export interface CombinedAnalysisResult {
  overallAssessment: string;
  clinicalCorrelation: string;
  diagnoses: DiagnosisRecommendation[];
  criticalFindings: string[];
  recommendations: string[];
  urgency: string;
  confidenceScore: number;
}

export type AnalysisType = 'IMAGE_ANALYSIS' | 'BLOOD_REPORT' | 'COMBINED';

export interface AnalysisResponse {
  id: string;
  patientId: string;
  medicalFileId: string;
  requestedBy: string;
  analysisType: AnalysisType;
  clinicalNotes: string | null;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  urgency: string | null;
  rawResult?: string | null;
  result?: string | null;
  errorMessage: string | null;
  modelUsed: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  estimatedCost: number | null;
  processingStartedAt: string | null;
  processingCompletedAt: string | null;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Parses raw AI JSON result into a robust normalized structure
 */
export function parseImageResult(a: AnalysisResponse | { rawResult?: string | null; result?: string | null }): ImageAnalysisResult | null {
  const jsonStr = a.rawResult || a.result;
  if (!jsonStr) return null;
  try {
    const raw = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
    const findings: NormalizedFinding[] = (raw.findings || []).map((f: any) => ({
      region: f.region || 'General / Unspecified',
      description: f.description || '',
      severity: (f.severity || 'NORMAL').toUpperCase(),
      confidence: typeof f.confidence === 'number' ? f.confidence : 0.9,
    }));

    // Normalize ICD-10
    const rawCodes = raw.icd10_codes || raw.icd10Codes || [];
    const icd10Codes: NormalizedIcd10[] = rawCodes.map((c: any) => {
      if (typeof c === 'string') {
        const parts = c.split('-');
        return {
          code: parts[0]?.trim() || c,
          description: parts[1]?.trim() || 'Clinical condition',
        };
      }
      return {
        code: c.code || 'UNSPECIFIED',
        description: c.description || 'Clinical condition',
      };
    });

    return {
      findings,
      impression: raw.impression || raw.overall_impression || raw.interpretation || 'No clinical impression recorded.',
      icd10Codes,
      recommendations: raw.recommendations || [],
      urgency: (raw.urgency || 'ROUTINE').toUpperCase(),
    };
  } catch (e) {
    console.warn('Failed to parse image analysis result JSON:', e);
    return null;
  }
}

export function parseResult<T = any>(a: AnalysisResponse | { rawResult?: string | null; result?: string | null }): T | null {
  const jsonStr = a.rawResult || a.result;
  if (!jsonStr) return null;
  try {
    return typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
  } catch {
    return null;
  }
}

export const analysisService = {
  async requestImageAnalysis(patientId: string, medicalFileId: string, clinicalNotes?: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis', {
      patientId,
      medicalFileId,
      clinicalNotes,
    });
    return res.data.data!;
  },

  async createAnalysis(params: { patientId: string; medicalFileId: string; analysisType?: string; clinicalNotes?: string }): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis', params);
    return res.data.data!;
  },

  async requestBloodReport(patientId: string, medicalFileId: string, clinicalNotes?: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis/blood-report', {
      patientId,
      medicalFileId,
      clinicalNotes,
    });
    return res.data.data!;
  },

  async requestCombined(patientId: string, medicalFileId: string, clinicalNotes?: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis/combined', {
      patientId,
      medicalFileId,
      clinicalNotes,
    });
    return res.data.data!;
  },

  async get(id: string): Promise<AnalysisResponse> {
    const res = await api.get<ApiResponse<AnalysisResponse>>(`/analysis/${id}`);
    return res.data.data!;
  },

  async getAnalysis(id: string): Promise<AnalysisResponse> {
    const res = await api.get<ApiResponse<AnalysisResponse>>(`/analysis/${id}`);
    return res.data.data!;
  },

  async listByPatient(patientId: string, page = 0, size = 20): Promise<PagedResponse<AnalysisResponse>> {
    const res = await api.get<ApiResponse<PagedResponse<AnalysisResponse>>>(`/analysis/patient/${patientId}`, {
      params: { page, size },
    });
    return res.data.data!;
  },

  async getPatientAnalyses(patientId: string, page = 0, size = 20): Promise<PagedResponse<AnalysisResponse>> {
    const res = await api.get<ApiResponse<PagedResponse<AnalysisResponse>>>(`/analysis/patient/${patientId}`, {
      params: { page, size },
    });
    return res.data.data!;
  },

  async listAll(page = 0, size = 20): Promise<PagedResponse<AnalysisResponse>> {
    const res = await api.get<ApiResponse<PagedResponse<AnalysisResponse>>>('/analysis', {
      params: { page, size },
    });
    return res.data.data!;
  },

  async retry(id: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>(`/analysis/${id}/retry`);
    return res.data.data!;
  },

  async retryAnalysis(id: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>(`/analysis/${id}/retry`);
    return res.data.data!;
  },
};
