import api from './api';
import type { ApiResponse } from '@/types';
import type { ReportQaResult } from '@/types/clinicalWorkspace';

export const reportQaApi = {
  async runReportQa(reviewId: string): Promise<ReportQaResult> {
    const res = await api.post<ApiResponse<ReportQaResult>>(`/reports/${reviewId}/qa`);
    return res.data.data;
  },
};
