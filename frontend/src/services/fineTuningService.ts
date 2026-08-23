import api from './api';

export interface AiModelRegistryItem {
  id: string;
  tenantId?: string;
  modelId: string;
  displayName: string;
  baseModel: string;
  adapterType: string;
  status: 'REGISTERED' | 'TRAINING' | 'READY' | 'DEPLOYED' | 'ARCHIVED' | string;
  loraRank?: number;
  loraAlpha?: number;
  trainingLoss?: number;
  trainingSamplesCount: number;
  endpointUrl?: string;
  description?: string;
  active: boolean;
  createdAt: string;
}

export interface ModelRegisterRequest {
  modelId: string;
  displayName: string;
  baseModel: string;
  adapterType?: string;
  status?: string;
  loraRank?: number;
  loraAlpha?: number;
  trainingLoss?: number;
  trainingSamplesCount?: number;
  endpointUrl?: string;
  description?: string;
  tenantPrivate?: boolean;
}

export interface DatasetExportSummary {
  totalRecordsScanned: number;
  eligibleRecordsCount: number;
  consentSkippedCount: number;
  totalPhiEntitiesRedacted: number;
  format: string;
  jsonlContent: string;
  exportedAt: string;
}

export interface AbExperiment {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  modelAId: string;
  modelBId: string;
  trafficSplitPercent: number;
  modality: string;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | string;
  startDate: string;
  endDate?: string;
}

export interface ExperimentMetricsSummary {
  experiment: AbExperiment;
  totalEvaluations: number;
  variantACount: number;
  variantAAvgRating: number;
  variantAAccuracyRate: number;
  variantAAvgLatencyMs: number;
  variantBCount: number;
  variantBAvgRating: number;
  variantBAccuracyRate: number;
  variantBAvgLatencyMs: number;
  winner: string;
}

export interface EvaluationRequest {
  experimentId: string;
  assignedVariant: string;
  modelUsed: string;
  latencyMs?: number;
  tokenCount?: number;
  userRating: number;
  accurate?: boolean;
  feedbackNotes?: string;
}

export const fineTuningService = {
  // Model Registry
  listModels: async (): Promise<AiModelRegistryItem[]> => {
    const res = await api.get<AiModelRegistryItem[]>('/models');
    return res.data;
  },

  registerModel: async (req: ModelRegisterRequest): Promise<AiModelRegistryItem> => {
    const res = await api.post<AiModelRegistryItem>('/models', req);
    return res.data;
  },

  updateModelStatus: async (id: string, status: string): Promise<AiModelRegistryItem> => {
    const res = await api.patch<AiModelRegistryItem>(`/models/${id}/status`, { status });
    return res.data;
  },

  // Dataset Export
  previewDataset: async (format = 'OPENAI_JSONL', modality = 'ALL', limit = 50): Promise<DatasetExportSummary> => {
    const res = await api.get<DatasetExportSummary>(`/finetuning/dataset/preview?format=${format}&modality=${modality}&limit=${limit}`);
    return res.data;
  },

  downloadDatasetUrl: (format = 'OPENAI_JSONL', modality = 'ALL', limit = 500): string => {
    return `/api/finetuning/dataset/download?format=${format}&modality=${modality}&limit=${limit}`;
  },

  // A/B Testing
  listExperiments: async (): Promise<AbExperiment[]> => {
    const res = await api.get<AbExperiment[]>('/finetuning/experiments');
    return res.data;
  },

  createExperiment: async (req: Partial<AbExperiment>): Promise<AbExperiment> => {
    const res = await api.post<AbExperiment>('/finetuning/experiments', req);
    return res.data;
  },

  getExperimentSummary: async (id: string): Promise<ExperimentMetricsSummary> => {
    const res = await api.get<ExperimentMetricsSummary>(`/finetuning/experiments/${id}/summary`);
    return res.data;
  },

  recordEvaluation: async (req: EvaluationRequest): Promise<void> => {
    await api.post('/finetuning/experiments/evaluations', req);
  },

  updateExperimentStatus: async (id: string, status: string): Promise<AbExperiment> => {
    const res = await api.patch<AbExperiment>(`/finetuning/experiments/${id}/status`, { status });
    return res.data;
  },
};
