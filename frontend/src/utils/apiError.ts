import axios, { type AxiosError } from "axios";

type ApiErrorBody = {
  message?: unknown;
  error?: unknown;
  details?: unknown;
  validationErrors?: unknown;
};

function getReadableMessage(value: unknown): string | null {
  if (typeof value === "string" && value.trim()) {
    return value.trim();
  }

  if (Array.isArray(value)) {
    const messages = value
      .map(getReadableMessage)
      .filter((message): message is string => Boolean(message));

    return messages.length > 0 ? messages.join(" ") : null;
  }

  if (value && typeof value === "object") {
    const messages = Object.values(value)
      .map(getReadableMessage)
      .filter((message): message is string => Boolean(message));

    return messages.length > 0 ? messages.join(" ") : null;
  }

  return null;
}

function extractApiErrorMessage(
  responseBody: ApiErrorBody | string | undefined,
): string | null {
  if (typeof responseBody === "string") {
    return getReadableMessage(responseBody);
  }

  return (
    getReadableMessage(responseBody?.message) ??
    getReadableMessage(responseBody?.error) ??
    getReadableMessage(responseBody?.details) ??
    getReadableMessage(responseBody?.validationErrors) ??
    null
  );
}

export function getApiErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const responseBody = error.response?.data;

    if (import.meta.env.DEV) {
      console.error("API request failed", {
        status: error.response?.status,
        response: responseBody,
      });
    }

    return extractApiErrorMessage(responseBody) ?? fallback;
  }

  return fallback;
}

function requestHadAuthorizationHeader(error: AxiosError): boolean {
  const headers = error.config?.headers;
  if (!headers) {
    return false;
  }

  const authorization = headers.Authorization ?? headers.authorization;
  return typeof authorization === "string" && authorization.startsWith("Bearer ");
}

export type ApiErrorPresentation = {
  message: string;
  hint?: string;
  severity: "error" | "warning";
  showRetry: boolean;
  redirectToLogin: boolean;
};

export function getApiErrorPresentation(
  error: unknown,
  fallback: string,
): ApiErrorPresentation {
  if (!axios.isAxiosError<ApiErrorBody>(error)) {
    return {
      message: fallback,
      severity: "error",
      showRetry: true,
      redirectToLogin: false,
    };
  }

  const status = error.response?.status;
  const responseBody = error.response?.data;

  if (import.meta.env.DEV) {
    console.error("API request failed", {
      status,
      url: error.config?.url,
      response: responseBody,
    });
  }

  const backendMessage = extractApiErrorMessage(responseBody);
  const tokenWasSent = requestHadAuthorizationHeader(error);

  if (status === 401) {
    if (tokenWasSent) {
      return {
        message: fallback,
        hint:
          "The request was authenticated, but the backend rejected it. Restart the backend to pick up the latest APIs, then retry.",
        severity: "error",
        showRetry: true,
        redirectToLogin: false,
      };
    }

    return {
      message: backendMessage ?? "Your session has expired.",
      hint: "Please sign in again to continue.",
      severity: "warning",
      showRetry: false,
      redirectToLogin: true,
    };
  }

  if (status === 403) {
    return {
      message: backendMessage ?? fallback,
      severity: "error",
      showRetry: false,
      redirectToLogin: false,
    };
  }

  if (status === 404 || (status !== undefined && status >= 500)) {
    return {
      message: fallback,
      hint:
        status === 404
          ? "The backend endpoint was not found. Confirm the frontend is pointing at the correct backend."
          : "A server error occurred. Please try again.",
      severity: "error",
      showRetry: true,
      redirectToLogin: false,
    };
  }

  if (backendMessage) {
    return {
      message: backendMessage,
      severity: "error",
      showRetry: status === 409,
      redirectToLogin: false,
    };
  }

  if (status === 409) {
    return {
      message: "This action conflicts with the current state.",
      severity: "error",
      showRetry: false,
      redirectToLogin: false,
    };
  }

  return {
    message: fallback,
    severity: "error",
    showRetry: true,
    redirectToLogin: false,
  };
}

export function getApiErrorMessageForStatus(
  error: unknown,
  fallback: string,
): string {
  return getApiErrorPresentation(error, fallback).message;
}

export function getLoginErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;

    if (status === 404) {
      return "Login endpoint unavailable";
    }

    if (status === 401) {
      return "Invalid username or password";
    }

    if (status === 500) {
      return "Server error";
    }

    if (!error.response) {
      return "Unable to reach the server";
    }
  }

  return getApiErrorMessage(error, "Unable to sign in.");
}
