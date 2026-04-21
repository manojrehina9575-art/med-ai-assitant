export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  errors?: Record<string, string>;
  timestamp: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export type UserRole = 'HOSPITAL_ADMIN' | 'DOCTOR' | 'LAB_TECH' | 'PATIENT';
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type FileType = 'XRAY' | 'CT_SCAN' | 'ULTRASOUND' | 'MRI' | 'BLOOD_REPORT' | 'LAB_REPORT' | 'PRESCRIPTION' | 'DISCHARGE_SUMMARY' | 'OTHER';
export type UploadStatus = 'UPLOADING' | 'UPLOADED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  tenantId: string;
  email: string;
  fullName: string;
  role: UserRole;
  tenantName: string;
}

export interface TenantInfo {
  id: string;
  name: string;
  subdomain: string;
}

export interface Patient {
  id: string;
  tenantId: string;
  medicalRecordNumber: string;
  firstName: string;
  lastName: string;
  fullName: string;
  dateOfBirth: string;
  gender: Gender;
  bloodGroup?: string;
  phone?: string;
  email?: string;
  address?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  medicalHistory?: string[];
  allergies?: string[];
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MedicalFile {
  id: string;
  tenantId: string;
  patientId: string;
  uploadedBy: string;
  fileName: string;
  originalFileName: string;
  fileType: FileType;
  mimeType: string;
  fileSizeBytes: number;
  description?: string;
  uploadStatus: UploadStatus;
  metadata: Record<string, unknown>;
  createdAt: string;
}
