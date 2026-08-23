import api from './api';
import type { ApiResponse } from '@/types';

// ── Types ────────────────────────────────────────────────────

export interface DashboardSummary {
  totalPatients: number;
  totalFiles: number;
  totalAnalyses: number;
  completedAnalyses: number;
  pendingAnalyses: number;
  failedAnalyses: number;
  totalEstimatedCost: number;
  unreadNotifications: number;
}

export interface DailyCount {
  date: string;   // "2026-08-21"
  count: number;
  completed: number;
  failed: number;
}

export interface DiagnosisBreakdown {
  analysisType: string;
  count: number;
  percentage: number;
}

export interface ModelUsage {
  modelName: string;
  analysisCount: number;
  totalTokens: number;
  totalCost: number;
}

export interface AnalyticsData {
  summary: DashboardSummary;
  analysesPerDay: DailyCount[];
  topDiagnoses: DiagnosisBreakdown[];
  modelUsage: ModelUsage[];
}

// ── Service ──────────────────────────────────────────────────

export const analyticsService = {
  /** Fetch all dashboard analytics in a single call */
  async getAll(days = 30, topN = 10): Promise<AnalyticsData> {
    const res = await api.get<ApiResponse<AnalyticsData>>('/analytics', {
      params: { days, topN },
    });
    return res.data.data;
  },

  async getSummary(): Promise<DashboardSummary> {
    const res = await api.get<ApiResponse<DashboardSummary>>('/analytics/summary');
    return res.data.data;
  },

  async getAnalysesPerDay(days = 30): Promise<DailyCount[]> {
    const res = await api.get<ApiResponse<DailyCount[]>>('/analytics/analyses-per-day', {
      params: { days },
    });
    return res.data.data;
  },

  async getTopDiagnoses(limit = 10): Promise<DiagnosisBreakdown[]> {
    const res = await api.get<ApiResponse<DiagnosisBreakdown[]>>('/analytics/top-diagnoses', {
      params: { limit },
    });
    return res.data.data;
  },

  async getModelUsage(): Promise<ModelUsage[]> {
    const res = await api.get<ApiResponse<ModelUsage[]>>('/analytics/model-usage');
    return res.data.data;
  },
};
