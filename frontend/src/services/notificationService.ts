import api from './api';
import type { ApiResponse } from '@/types';

// ── Types ────────────────────────────────────────────────────

export interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  isRead: boolean;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  createdAt: string;
}

export interface NotificationList {
  notifications: Notification[];
  unreadCount: number;
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

// ── Service ──────────────────────────────────────────────────

export const notificationService = {
  async list(page = 0, size = 20): Promise<NotificationList> {
    const res = await api.get<ApiResponse<NotificationList>>('/notifications', {
      params: { page, size },
    });
    return res.data.data;
  },

  async getUnreadCount(): Promise<number> {
    const res = await api.get<ApiResponse<{ count: number }>>('/notifications/unread-count');
    return res.data.data.count;
  },

  async markRead(id: string): Promise<void> {
    await api.post(`/notifications/${id}/read`);
  },

  async markAllRead(): Promise<number> {
    const res = await api.post<ApiResponse<{ markedRead: number }>>('/notifications/read-all');
    return res.data.data.markedRead;
  },
};
