export enum AuthType {
  NONE = 'NONE',
  BASIC = 'BASIC',
  BEARER_TOKEN = 'BEARER_TOKEN',
  API_KEY = 'API_KEY',
  OAUTH2 = 'OAUTH2',
  CERTIFICATE = 'CERTIFICATE',
}

export interface FeedAuthentication {
  feedOnestopId: string;
  authType: AuthType;
  primaryCredential?: string;
  secondaryCredential?: string;
  authParameters?: string;
  isActive: boolean;
  expiresAt?: string;
  lastAuthSuccess?: string;
  lastAuthFailure?: string;
  failureCount: number;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface FeedAuthenticationRequest {
  authType: AuthType;
  primaryCredential?: string;
  secondaryCredential?: string;
  authParameters?: Record<string, unknown>;
  expiresAt?: string;
  notes?: string;
}

export interface AuthenticationTestResult {
  success: boolean;
  message: string;
  responseCode?: number;
  responseTime?: number;
  error?: string;
}

export interface AuthenticationStatistics {
  total: number;
  active: number;
  expired: number;
  locked: number;
  noAuth: number;
  byType: Record<AuthType, number>;
}

export interface AuthenticationFormData {
  authType: AuthType;
  username?: string;
  password?: string;
  token?: string;
  apiKey?: string;
  apiKeyHeader?: string;
  certificatePath?: string;
  clientSecret?: string;
  accessToken?: string;
  refreshToken?: string;
  tokenUrl?: string;
  expiresAt?: Date;
  notes?: string;
}
