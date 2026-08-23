import { create } from 'zustand';
import type { UserRole } from '@/types';

/**
 * Session state, held in memory only.
 *
 * <p>This store used to be wrapped in zustand's `persist`, which defaults to localStorage, and it
 * held both the access token and the refresh token. Any script running on the origin could read
 * them — one XSS through a dependency or a mis-escaped patient name was full account takeover plus
 * a seven-day refresh credential that outlived the session.
 *
 * <p>Nothing is persisted now. The refresh token lives in an httpOnly cookie the browser sends
 * automatically and JavaScript cannot see; the access token exists only in this object, and a page
 * reload discards it. `bootstrapSession` in `services/api` trades the cookie for a fresh access
 * token on load, so the user still stays signed in across reloads — the token simply never touches
 * disk.
 */
interface AuthState {
  accessToken: string | null;
  userId: string | null;
  tenantId: string | null;
  email: string | null;
  fullName: string | null;
  role: UserRole | null;
  tenantName: string | null;
  isAuthenticated: boolean;
  /** False until the initial refresh attempt settles, so guards do not redirect prematurely. */
  isBootstrapped: boolean;
  setAuth: (data: {
    accessToken: string;
    userId: string;
    tenantId: string;
    email: string;
    fullName: string;
    role: UserRole;
    tenantName: string;
  }) => void;
  setBootstrapped: () => void;
  clear: () => void;
}

const empty = {
  accessToken: null,
  userId: null,
  tenantId: null,
  email: null,
  fullName: null,
  role: null,
  tenantName: null,
  isAuthenticated: false,
};

export const useAuthStore = create<AuthState>()((set) => ({
  ...empty,
  isBootstrapped: false,
  setAuth: (data) => set({ ...data, isAuthenticated: true, isBootstrapped: true }),
  setBootstrapped: () => set({ isBootstrapped: true }),
  clear: () => set({ ...empty, isBootstrapped: true }),
}));
