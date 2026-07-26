import type { ChipProps } from "@mui/material";

export function formatStatusLabel(value: string): string {
  return value.replaceAll("_", " ");
}

export function severityChipColor(
  severity: string,
): ChipProps["color"] {
  switch (severity.toUpperCase()) {
    case "CRITICAL":
      return "error";
    case "WARNING":
    case "HIGH":
      return "warning";
    case "MEDIUM":
      return "info";
    case "LOW":
      return "success";
    default:
      return "default";
  }
}

export function investigationStatusChipColor(
  status: string,
): ChipProps["color"] {
  switch (status) {
    case "EXECUTION_FAILED":
    case "REJECTED":
      return "error";
    case "AWAITING_REVIEW":
    case "ESCALATED":
      return "warning";
    case "ASSIGNED":
      return "info";
    case "IN_REVIEW":
      return "warning";
    case "RUNNING":
    case "REPORT_GENERATED":
    case "NEW":
      return "info";
    case "APPROVED":
    case "CLOSED":
      return "success";
    default:
      return "default";
  }
}

export function screeningStatusChipColor(
  status: string,
): ChipProps["color"] {
  switch (status) {
    case "CRITICAL":
    case "SCREENING_FAILED":
      return "error";
    case "SUSPICIOUS":
      return "warning";
    case "CLEARED":
      return "success";
    case "PROCESSING":
      return "info";
    default:
      return "default";
  }
}

export function executionStatusChipColor(
  status: string,
): ChipProps["color"] {
  switch (status.toUpperCase()) {
    case "FAILED":
      return "error";
    case "RUNNING":
      return "info";
    case "COMPLETED":
      return "success";
    case "WAITING":
    default:
      return "default";
  }
}

export function formatDurationMs(durationMs: number | null): string {
  if (durationMs == null) {
    return "—";
  }

  if (durationMs < 1000) {
    return `${durationMs} ms`;
  }

  if (durationMs < 60_000) {
    return `${(durationMs / 1000).toFixed(1)} s`;
  }

  return `${(durationMs / 60_000).toFixed(1)} min`;
}

export function formatAverageDuration(durationMs: number | null): string {
  return formatDurationMs(durationMs);
}

export function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function roleChipColor(
  role: string,
): ChipProps["color"] {
  switch (role) {
    case "ADMIN":
    case "SUPERVISOR":
      return "primary";
    case "READ_ONLY":
      return "default";
    default:
      return "info";
  }
}
