export type Role =
  | "ADMIN"
  | "SUPERVISOR"
  | "FRAUD_ANALYST"
  | "COMPLIANCE_ANALYST"
  | "READ_ONLY";

export interface AuthUser {
  id: string;
  username: string;
  role: Role;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  userId: string;
  username: string;
  role: Role;
}

export const AUTH_STORAGE_KEY = "banking.auth.session";
