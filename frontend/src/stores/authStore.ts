import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserRole } from '@/types';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  tenantId: string | null;
  email: string | null;
  fullName: string | null;
  role: UserRole | null;
  tenantName: string | null;
  isAuthenticated: boolean;
  setAuth: (data: {
    accessToken: string;
    refreshToken: string;
    userId: string;
    tenantId: string;
    email: string;
    fullName: string;
    role: UserRole;
    tenantName: string;
  }) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      tenantId: null,
      email: null,
      fullName: null,
      role: null,
      tenantName: null,
      isAuthenticated: false,
      setAuth: (data) =>
        set({
          ...data,
          isAuthenticated: true,
        }),
      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userId: null,
          tenantId: null,
          email: null,
          fullName: null,
          role: null,
          tenantName: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'medai-auth',
    }
  )
);
