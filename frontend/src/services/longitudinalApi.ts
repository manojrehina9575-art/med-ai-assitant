import api from './api';
import type { ApiResponse } from '@/types';
import type { LongitudinalResult } from '@/types/clinicalWorkspace';

export const longitudinalApi = {
  async compareReports(currentReviewId: string, priorReviewId: string): Promise<LongitudinalResult> {
    const res = await api.post<ApiResponse<LongitudinalResult>>(
      `/reports/${currentReviewId}/longitudinal/${priorReviewId}`
    );
    return res.data.data;
  },
};
