import axios from "axios";

import { AUTH_STORAGE_KEY } from "../types/auth";

export const API_BASE_URL =
  import.meta.env.VITE_API_URL ??
  "http://localhost:8080/api";

function readStoredToken(): string | null {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const session = JSON.parse(raw) as { token?: string };
    return session.token ?? null;
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function getAuthHeaders(
  extraHeaders: Record<string, string> = {},
): Record<string, string> {
  const token = readStoredToken();
  return {
    ...extraHeaders,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

function clearSessionAndRedirectToLogin(): void {
  localStorage.removeItem(AUTH_STORAGE_KEY);
  if (window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
}

async function isSessionStillValid(): Promise<boolean> {
  const token = readStoredToken();
  if (!token) {
    return false;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/auth/me`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    return response.ok;
  } catch {
    return false;
  }
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 30000,
});

api.interceptors.request.use((config) => {
  const token = readStoredToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const requestUrl = error.config?.url ?? "";
    const isLoginRequest = requestUrl.includes("/auth/login");

    if (error.response?.status === 401 && !isLoginRequest) {
      const tokenWasSent = Boolean(error.config?.headers?.Authorization);
      if (!tokenWasSent || !(await isSessionStillValid())) {
        clearSessionAndRedirectToLogin();
      }
    }

    return Promise.reject(error);
  },
);

export default api;
