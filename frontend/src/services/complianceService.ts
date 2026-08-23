import api from './api';

export interface PatientConsent {
  id: string;
  tenantId: string;
  patientId: string;
  patientName: string;
  purpose: 'AI_ANALYSIS' | 'RESEARCH_USE' | 'DATA_SHARING' | 'MODEL_TRAINING' | string;
  status: 'GRANTED' | 'REVOKED' | 'EXPIRED';
  signerName: string;
  signerRelationship: string;
  grantedAt: string;
  expiresAt?: string;
  revokedAt?: string;
  signatureHash?: string;
  notes?: string;
}

export interface ConsentRequest {
  patientId: string;
  purpose: string;
  signerName: string;
  signerRelationship?: string;
  expiresAt?: string;
  signatureHash?: string;
  notes?: string;
}

export interface RedactionResult {
  originalText: string;
  redactedText: string;
  totalRedactionsCount: number;
  redactionsByType: Record<string, number>;
  tokenMap: Record<string, string>;
}

export interface DataRetentionPolicy {
  id?: string;
  tenantId?: string;
  auditLogRetentionDays: number;
  analysisRetentionDays: number;
  chatSessionRetentionDays: number;
  softDeletePurgeDays: number;
  autoPurgeEnabled: boolean;
  lastPurgeAt?: string;
}

export interface RetentionPurgeLog {
  id: string;
  tenantId: string;
  entityType: string;
  recordsPurgedCount: number;
  status: string;
  errorDetails?: string;
  executedAt: string;
}

export interface PurgeSummary {
  tenantId: string;
  auditLogsPurged: number;
  chatMessagesPurged: number;
  executedAt: string;
}

export const complianceService = {
  // Consents
  getAllConsents: async (): Promise<PatientConsent[]> => {
    const res = await api.get<PatientConsent[]>('/compliance/consents');
    return res.data;
  },

  getPatientConsents: async (patientId: string): Promise<PatientConsent[]> => {
    const res = await api.get<PatientConsent[]>(`/compliance/consents/patient/${patientId}`);
    return res.data;
  },

  grantConsent: async (req: ConsentRequest): Promise<PatientConsent> => {
    const res = await api.post<PatientConsent>('/compliance/consents', req);
    return res.data;
  },

  revokeConsent: async (consentId: string, reason?: string): Promise<PatientConsent> => {
    const res = await api.post<PatientConsent>(`/compliance/consents/${consentId}/revoke`, { reason });
    return res.data;
  },

  verifyConsent: async (patientId: string, purpose: string): Promise<boolean> => {
    const res = await api.get<{ hasValidConsent: boolean }>(`/compliance/consents/patient/${patientId}/verify?purpose=${purpose}`);
    return res.data.hasValidConsent;
  },

  // PHI Redaction Sandbox
  testRedaction: async (text: string): Promise<RedactionResult> => {
    const res = await api.post<RedactionResult>('/compliance/phi/sandbox', { text });
    return res.data;
  },

  restoreRedacted: async (redactedText: string, tokenMap: Record<string, string>): Promise<string> => {
    const res = await api.post<{ restoredText: string }>('/compliance/phi/restore', { redactedText, tokenMap });
    return res.data.restoredText;
  },

  // Data Retention
  getRetentionPolicy: async (): Promise<DataRetentionPolicy> => {
    const res = await api.get<DataRetentionPolicy>('/compliance/retention/policy');
    return res.data;
  },

  updateRetentionPolicy: async (policy: DataRetentionPolicy): Promise<DataRetentionPolicy> => {
    const res = await api.put<DataRetentionPolicy>('/compliance/retention/policy', policy);
    return res.data;
  },

  executePurge: async (): Promise<PurgeSummary> => {
    const res = await api.post<PurgeSummary>('/compliance/retention/purge');
    return res.data;
  },

  getPurgeLogs: async (): Promise<RetentionPurgeLog[]> => {
    const res = await api.get<RetentionPurgeLog[]>('/compliance/retention/logs');
    return res.data;
  },
};
