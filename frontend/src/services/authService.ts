import api from './api';
import type { ApiResponse, AuthResponse, TenantInfo } from '@/types';

export const authService = {
  async login(email: string, password: string, tenantId: string): Promise<AuthResponse> {
    const res = await api.post<ApiResponse<AuthResponse>>('/auth/login', {
      email,
      password,
      tenantId,
    });
    return res.data.data;
  },

  async registerTenant(data: {
    hospitalName: string;
    subdomain: string;
    contactEmail: string;
    phone?: string;
    address?: string;
    adminFirstName: string;
    adminLastName: string;
    adminEmail: string;
    adminPassword: string;
  }): Promise<AuthResponse> {
    const res = await api.post<ApiResponse<AuthResponse>>('/auth/register-tenant', data);
    return res.data.data;
  },

  /**
   * Resolves a hospital by the subdomain their administrator gave them.
   *
   * <p>Replaces a call that listed every hospital on the platform to anyone who loaded the login
   * page.
   */
  async findTenant(subdomain: string): Promise<TenantInfo> {
    const res = await api.get<ApiResponse<TenantInfo>>('/auth/tenants', {
      params: { subdomain },
    });
    return res.data.data;
  },

  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    const res = await api.post<ApiResponse<AuthResponse>>('/auth/refresh', { refreshToken });
    return res.data.data;
  },
};
