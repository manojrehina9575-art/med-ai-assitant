import api from './api';
import type { ApiResponse, PagedResponse, MedicalFile, FileType } from '@/types';

export const fileService = {
  async upload(
    patientId: string,
    file: File,
    fileType: FileType,
    description?: string
  ): Promise<MedicalFile> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('fileType', fileType);
    if (description) formData.append('description', description);

    const res = await api.post<ApiResponse<MedicalFile>>(
      `/patients/${patientId}/files`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return res.data.data;
  },

  async list(patientId: string, page = 0, size = 20): Promise<PagedResponse<MedicalFile>> {
    const res = await api.get<ApiResponse<PagedResponse<MedicalFile>>>(
      `/patients/${patientId}/files`,
      { params: { page, size } }
    );
    return res.data.data;
  },

  getDownloadUrl(patientId: string, fileId: string): string {
    return `/api/patients/${patientId}/files/${fileId}/download`;
  },

  async delete(patientId: string, fileId: string): Promise<void> {
    await api.delete(`/patients/${patientId}/files/${fileId}`);
  },
};
