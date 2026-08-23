import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/stores/authStore';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  // The refresh token is an httpOnly cookie now, so the browser has to be allowed to send it.
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Access tokens last 15 minutes, so the client has to refresh them.
 *
 * The refresh token itself is never in JavaScript's hands: it is an httpOnly cookie scoped to
 * /api/auth, which the browser attaches to the refresh call and to nothing else. That is why
 * `refreshAccessToken` sends no body — there is nothing to send, which is the point.
 *
 * A single shared promise guards the refresh so that N requests failing at the same moment produce
 * one refresh call, not N. Getting this wrong is how rotation turns into a self-inflicted lockout:
 * concurrent refreshes each rotate the token, and the loser's now-revoked token looks like a replay
 * attack, which revokes the whole family.
 */
let refreshInFlight: Promise<string> | null = null;

function clearSessionAndRedirect() {
  useAuthStore.getState().clear();
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

async function refreshAccessToken(): Promise<string> {
  // A bare axios call, not `api`: this must not recurse through these interceptors.
  const response = await axios.post('/api/auth/refresh', null, { withCredentials: true });
  const auth = response.data.data;
  useAuthStore.getState().setAuth(auth);
  return auth.accessToken;
}

function startRefresh(): Promise<string> {
  refreshInFlight =
    refreshInFlight ??
    refreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

/**
 * Restores the session on page load.
 *
 * Nothing survives a reload in memory, so the app asks the server whether the refresh cookie is
 * still good. Success gives a fresh access token and the user's profile; failure just means they
 * are signed out. Either way the store is marked bootstrapped, which is what stops the route
 * guards redirecting to /login during the round trip.
 */
export async function bootstrapSession(): Promise<void> {
  try {
    await startRefresh();
  } catch {
    useAuthStore.getState().clear();
  } finally {
    useAuthStore.getState().setBootstrapped();
  }
}

/** Revokes the session server-side, then clears local state. */
export async function logout(): Promise<void> {
  try {
    await axios.post('/api/auth/logout', null, { withCredentials: true });
  } catch {
    // A failed revoke must not trap the user in a session they asked to leave.
  }
  clearSessionAndRedirect();
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;

    const isAuthCall =
      request?.url?.includes('/auth/refresh') ||
      request?.url?.includes('/auth/login') ||
      request?.url?.includes('/auth/logout');

    if (error.response?.status !== 401 || !request || request._retried || isAuthCall) {
      if (error.response?.status === 401 && isAuthCall) {
        clearSessionAndRedirect();
      }
      return Promise.reject(error);
    }

    request._retried = true;

    try {
      const token = await startRefresh();
      request.headers = { ...request.headers, Authorization: `Bearer ${token}` };
      return api(request);
    } catch {
      clearSessionAndRedirect();
      return Promise.reject(error);
    }
  }
);

export default api;
