import api from './api';
import type { ApiResponse, PagedResponse } from '@/types';

export type ChatRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface ChatCitation {
  documentId: string;
  title: string;
  documentType: string;
  chunkIndex: number;
  excerpt: string;
  similarityScore?: number;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: ChatRole;
  content: string;
  citations?: ChatCitation[] | null;
  safetyFlags?: string[] | null;
  modelUsed?: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  estimatedCost?: number;
  createdAt: string;
}

export interface ChatSession {
  id: string;
  patientId?: string | null;
  patientName?: string | null;
  patientMrn?: string | null;
  userId: string;
  title: string;
  isArchived: boolean;
  messages?: ChatMessage[];
  messageCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface ExportChatTranscript {
  sessionId: string;
  title: string;
  tenantName?: string;
  patientName?: string | null;
  patientMrn?: string | null;
  exportedAt: string;
  messages: ChatMessage[];
  formattedMarkdown: string;
}

export const chatService = {
  async createSession(data: {
    patientId?: string;
    title?: string;
    initialMessage?: string;
  }): Promise<ChatSession> {
    const res = await api.post<ApiResponse<ChatSession>>('/chat/sessions', data);
    return res.data.data!;
  },

  async listSessions(
    patientId?: string,
    page = 0,
    size = 50
  ): Promise<PagedResponse<ChatSession>> {
    const res = await api.get<ApiResponse<PagedResponse<ChatSession>>>('/chat/sessions', {
      params: { patientId, page, size },
    });
    return res.data.data!;
  },

  async getSession(sessionId: string): Promise<ChatSession> {
    const res = await api.get<ApiResponse<ChatSession>>(`/chat/sessions/${sessionId}`);
    return res.data.data!;
  },

  async deleteSession(sessionId: string): Promise<void> {
    await api.delete(`/chat/sessions/${sessionId}`);
  },

  async sendMessage(
    sessionId: string,
    content: string,
    includeRag = true
  ): Promise<ChatMessage> {
    const res = await api.post<ApiResponse<ChatMessage>>(
      `/chat/sessions/${sessionId}/messages`,
      { content, includeRag }
    );
    return res.data.data!;
  },

  async exportTranscript(sessionId: string): Promise<ExportChatTranscript> {
    const res = await api.get<ApiResponse<ExportChatTranscript>>(
      `/chat/sessions/${sessionId}/export`
    );
    return res.data.data!;
  },
};
