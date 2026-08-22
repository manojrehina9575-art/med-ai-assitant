import api from './api';
import type { PagedResponse } from '@/types';

export interface ToolDefinition {
  name: string;
  description: string;
  requiresConfirmation: boolean;
  inputSchemaJson: string;
}

export interface AgentWorkflowStep {
  id: string;
  stepIndex: number;
  toolName: string;
  actionSummary: string;
  inputPayload: Record<string, any>;
  outputPayload?: Record<string, any>;
  requiresConfirmation: boolean;
  confirmationStatus: 'NOT_REQUIRED' | 'PENDING' | 'APPROVED' | 'REJECTED';
  status: 'PENDING' | 'EXECUTING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AgentWorkflow {
  id: string;
  patientId?: string;
  patientName?: string;
  patientMrn?: string;
  goal: string;
  status: 'PLANNING' | 'AWAITING_APPROVAL' | 'EXECUTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  planSummary?: string;
  finalOutput?: string;
  steps: AgentWorkflowStep[];
  createdAt: string;
  updatedAt: string;
}

export interface Appointment {
  id: string;
  patientId: string;
  doctorId: string;
  appointmentType: string;
  scheduledAt: string;
  durationMinutes: number;
  status: string;
  notes?: string;
  createdAt: string;
}

export interface Prescription {
  id: string;
  patientId: string;
  doctorId: string;
  medications: string; // JSON string or parsed
  diagnosis?: string;
  status: string;
  notes?: string;
  createdAt: string;
}

export interface LabOrder {
  id: string;
  patientId: string;
  doctorId: string;
  testNames: string; // JSON string
  urgency: string;
  status: string;
  clinicalIndication?: string;
  createdAt: string;
}

export const agentService = {
  listTools: async (): Promise<ToolDefinition[]> => {
    const res = await api.get('/agent/tools');
    return res.data.data;
  },

  startWorkflow: async (goal: string, patientId?: string): Promise<AgentWorkflow> => {
    const res = await api.post('/agent/workflows', { goal, patientId });
    return res.data.data;
  },

  getWorkflow: async (id: string): Promise<AgentWorkflow> => {
    const res = await api.get(`/agent/workflows/${id}`);
    return res.data.data;
  },

  listWorkflows: async (patientId?: string, page = 0, size = 20): Promise<PagedResponse<AgentWorkflow>> => {
    const res = await api.get('/agent/workflows', {
      params: { patientId, page, size },
    });
    return res.data.data;
  },

  confirmStep: async (
    workflowId: string,
    stepId: string,
    approved: boolean,
    modifiedInputPayload?: Record<string, any>,
    rejectionReason?: string
  ): Promise<AgentWorkflow> => {
    const res = await api.post(`/agent/workflows/${workflowId}/steps/${stepId}/confirm`, {
      approved,
      modifiedInputPayload,
      rejectionReason,
    });
    return res.data.data;
  },

  getPatientAppointments: async (patientId: string): Promise<Appointment[]> => {
    const res = await api.get('/agent/clinical/appointments', { params: { patientId } });
    return res.data.data;
  },

  getPatientPrescriptions: async (patientId: string): Promise<Prescription[]> => {
    const res = await api.get('/agent/clinical/prescriptions', { params: { patientId } });
    return res.data.data;
  },

  getPatientLabOrders: async (patientId: string): Promise<LabOrder[]> => {
    const res = await api.get('/agent/clinical/lab-orders', { params: { patientId } });
    return res.data.data;
  },
};
