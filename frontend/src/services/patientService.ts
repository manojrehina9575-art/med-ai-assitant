import api from './api';
import type { ApiResponse, PagedResponse, Patient } from '@/types';

export const patientService = {
  async list(page = 0, size = 20, search?: string, active?: boolean): Promise<PagedResponse<Patient>> {
    const params: Record<string, unknown> = { page, size };
    if (search) params.search = search;
    if (active !== undefined) params.active = active;
    const res = await api.get<ApiResponse<PagedResponse<Patient>>>('/patients', { params });
    return res.data.data;
  },

  async get(id: string): Promise<Patient> {
    const res = await api.get<ApiResponse<Patient>>(`/patients/${id}`);
    return res.data.data;
  },

  async create(data: {
    medicalRecordNumber: string;
    firstName: string;
    lastName: string;
    dateOfBirth: string;
    gender: string;
    bloodGroup?: string;
    phone?: string;
    email?: string;
    address?: string;
    emergencyContactName?: string;
    emergencyContactPhone?: string;
    medicalHistory?: string[];
    allergies?: string[];
  }): Promise<Patient> {
    const res = await api.post<ApiResponse<Patient>>('/patients', data);
    return res.data.data;
  },

  async update(id: string, data: Partial<Patient>): Promise<Patient> {
    const res = await api.put<ApiResponse<Patient>>(`/patients/${id}`, data);
    return res.data.data;
  },

  async delete(id: string, permanent = false): Promise<void> {
    await api.delete(`/patients/${id}`, {
      params: permanent ? { permanent: true } : undefined,
    });
  },
};

