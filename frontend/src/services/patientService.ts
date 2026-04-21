import api from './api';
import type { ApiResponse, PagedResponse, Patient } from '@/types';

export const patientService = {
  async list(page = 0, size = 20, search?: string): Promise<PagedResponse<Patient>> {
    const params: Record<string, unknown> = { page, size };
    if (search) params.search = search;
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
  }): Promise<Patient> {
    const res = await api.post<ApiResponse<Patient>>('/patients', data);
    return res.data.data;
  },

  async update(id: string, data: Partial<Patient>): Promise<Patient> {
    const res = await api.put<ApiResponse<Patient>>(`/patients/${id}`, data);
    return res.data.data;
  },

  async delete(id: string): Promise<void> {
    await api.delete(`/patients/${id}`);
  },
};
