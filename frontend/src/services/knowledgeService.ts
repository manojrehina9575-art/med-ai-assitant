import api from './api';
import type { ApiResponse, PagedResponse } from '@/types';

export type DocumentType = 'CLINICAL_PROTOCOL' | 'GUIDELINE' | 'DRUG_FORMULARY' | 'SOP' | 'JOURNAL';
export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED';

export interface KnowledgeDocument {
  id: string;
  title: string;
  documentType: DocumentType;
  source?: string;
  fileName?: string;
  fileSizeBytes?: number;
  totalChunks: number;
  status: DocumentStatus;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Citation {
  documentId: string;
  title: string;
  documentType: string;
  chunkIndex: number;
  excerpt: string;
  similarityScore: number;
}

export interface RagResponse {
  query: string;
  answer: string;
  citations: Citation[];
  suggestedFollowUps: string[];
  totalSourcesRetrieved: number;
}

export const knowledgeService = {
  async uploadDocument(
    file: File,
    title: string,
    documentType: DocumentType = 'GUIDELINE',
    source?: string
  ): Promise<KnowledgeDocument> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    formData.append('documentType', documentType);
    if (source) formData.append('source', source);

    const res = await api.post<ApiResponse<KnowledgeDocument>>('/knowledge/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data.data!;
  },

  async listDocuments(
    documentType?: DocumentType,
    page = 0,
    size = 20
  ): Promise<PagedResponse<KnowledgeDocument>> {
    const res = await api.get<ApiResponse<PagedResponse<KnowledgeDocument>>>('/knowledge', {
      params: { documentType, page, size },
    });
    return res.data.data!;
  },

  async getDocument(id: string): Promise<KnowledgeDocument> {
    const res = await api.get<ApiResponse<KnowledgeDocument>>(`/knowledge/${id}`);
    return res.data.data!;
  },

  async deleteDocument(id: string): Promise<void> {
    await api.delete(`/knowledge/${id}`);
  },

  async queryKnowledgeBase(
    query: string,
    topK = 4,
    documentType?: string
  ): Promise<RagResponse> {
    const res = await api.post<ApiResponse<RagResponse>>('/knowledge/query', {
      query,
      topK,
      documentType,
    });
    return res.data.data!;
  },
};
