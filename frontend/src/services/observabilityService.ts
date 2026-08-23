import api from './api';

export interface ObservabilitySummary {
  systemStatus: string;
  uptimeHours: number;
  cacheItemCount: number;
  cacheHitRatePercent: number;
  todayRequestsCount: number;
  todayTokensTotal: number;
  todaySpendUsd: number;
  averageLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  errorRatePercent: number;
}

export interface SystemTelemetry {
  jvmUsedMemoryMb: number;
  jvmMaxMemoryMb: number;
  availableProcessors: number;
  activeThreads: number;
  cacheEntries: number;
  cacheHitRate: number;
}

export const observabilityService = {
  getSummary: async (): Promise<ObservabilitySummary> => {
    const res = await api.get<ObservabilitySummary>('/observability/summary');
    return res.data;
  },

  getTelemetry: async (): Promise<SystemTelemetry> => {
    const res = await api.get<SystemTelemetry>('/observability/telemetry');
    return res.data;
  },
};
