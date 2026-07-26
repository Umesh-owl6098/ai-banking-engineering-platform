import type {
  AuthUser,
  LoginRequest,
  LoginResponse,
} from "../types/auth";

import api from "../api/api";

export async function login(
  request: LoginRequest,
): Promise<LoginResponse> {
  const response = await api.post<LoginResponse>(
    "/auth/login",
    request,
  );

  return response.data;
}

export async function getCurrentUser(): Promise<AuthUser> {
  const response = await api.get<AuthUser>("/auth/me");
  return response.data;
}
