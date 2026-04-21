import api from './api';
import type { ApiResponse, PagedResponse } from '@/types';

export interface Finding {
  region: string;
  description: string;
  severity: string;
  confidence: number;
}

export interface AnalysisResult {
  findings: Finding[];
  impression: string;
  icd10Codes: string[];
  recommendations: string[];
  urgency: string;
}

export interface AnalysisResponse {
  id: string;
  patientId: string;
  medicalFileId: string;
  requestedBy: string;
  analysisType: string;
  clinicalNotes: string | null;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  urgency: string | null;
  result: AnalysisResult | null;
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

export const analysisService = {
  async requestAnalysis(patientId: string, medicalFileId: string, clinicalNotes?: string): Promise<AnalysisResponse> {
    const res = await api.post<ApiResponse<AnalysisResponse>>('/analysis', {
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
