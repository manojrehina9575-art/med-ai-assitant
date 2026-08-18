import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/stores/authStore';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Access tokens now last 15 minutes instead of 24 hours, so the client has to refresh them.
 * Previously a 401 logged the user straight out, which meant the backend's refresh-token rotation
 * — replay detection and all — was never once exercised from the UI.
 *
 * A single shared promise guards the refresh so that N requests failing at the same moment produce
 * one refresh call, not N. Getting this wrong is how rotation turns into a self-inflicted lockout:
 * concurrent refreshes each rotate the token, and the loser's now-revoked token looks like a replay
 * attack, which revokes the whole family.
 */
let refreshInFlight: Promise<string> | null = null;

function logoutAndRedirect() {
  useAuthStore.getState().logout();
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

async function refreshAccessToken(): Promise<string> {
  const { refreshToken, setAuth } = useAuthStore.getState();
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }

  // A bare axios call, not `api`: this must not recurse through these interceptors.
  const response = await axios.post('/api/auth/refresh', { refreshToken });
  const auth = response.data.data;
  setAuth(auth);
  return auth.accessToken;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;

    const isAuthCall = request?.url?.includes('/auth/refresh') || request?.url?.includes('/auth/login');
    if (error.response?.status !== 401 || !request || request._retried || isAuthCall) {
      if (error.response?.status === 401 && isAuthCall) {
        logoutAndRedirect();
      }
      return Promise.reject(error);
    }

    request._retried = true;

    try {
      refreshInFlight = refreshInFlight ?? refreshAccessToken().finally(() => {
        refreshInFlight = null;
      });
      const token = await refreshInFlight;

      request.headers = { ...request.headers, Authorization: `Bearer ${token}` };
      return api(request);
    } catch {
      logoutAndRedirect();
      return Promise.reject(error);
    }
  }
);

export default api;
