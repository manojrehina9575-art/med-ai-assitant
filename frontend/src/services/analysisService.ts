import api from './api';
import type { ApiResponse, PagedResponse } from '@/types';

// Image analysis result shape
export interface Finding {
  region: string;
  description: string;
  severity: string;
  confidence: number;
}

export interface ImageAnalysisResult {
  findings: Finding[];
  impression: string;
  icd10Codes: string[];
  recommendations: string[];
  urgency: string;
}

// Blood report result shape
export interface BloodReportParameter {
  name: string;
  value: number;
  unit: string;
  referenceRange: string;
  flag: 'NORMAL' | 'HIGH' | 'LOW' | 'CRITICAL_HIGH' | 'CRITICAL_LOW';
}

export interface BloodReportResult {
  testName: string;
  parameters: BloodReportParameter[];
  interpretation: string;
  flags: string[];
}

// Combined analysis result shape
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
  rawResult: string | null;
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

export function parseResult(a: AnalysisResponse): ImageAnalysisResult | BloodReportResult | CombinedAnalysisResult | null {
  if (!a.rawResult) return null;
  try {
    return JSON.parse(a.rawResult);
  } catch {
    return null;
  }
}

export const analysisService = {
  async requestImageAnalysis(patientId: string, medicalFileId: string, clinicalNotes?: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis', {
      patientId, medicalFileId, clinicalNotes,
    });
    return res.data.data!;
  },

  async requestBloodReport(patientId: string, medicalFileId: string, clinicalNotes?: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis/blood-report', {
      patientId, medicalFileId, clinicalNotes,
    });
    return res.data.data!;
  },

  async requestCombined(patientId: string, medicalFileId: string, clinicalNotes?: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis/combined', {
      patientId, medicalFileId, clinicalNotes,
    });
    return res.data.data!;
  },

  async get(id: string): Promise<AnalysisResponse> {
    const res = await api.get<ApiResponse<AnalysisResponse>>(`/analysis/${id}`);
    return res.data.data!;
  },

  async listByPatient(patientId: string, page = 0, size = 20): Promise<PagedResponse<AnalysisResponse>> {
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
};
