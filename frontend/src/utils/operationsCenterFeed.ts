import type { InvestigationCreatedNotification } from "../types/investigation";
import type { InvestigationExecutionEvent } from "../types/investigationExecution";
import type { ActivityFeedEntry } from "../types/operationsCenter";
import type { LiveTransactionEvent } from "../types/simulation";
import { formatStatusLabel } from "./statusBadges";

const MAX_FEED_ENTRIES = 50;

export function prependActivityFeedEntry(
  current: ActivityFeedEntry[],
  entry: ActivityFeedEntry,
): ActivityFeedEntry[] {
  return [entry, ...current.filter((item) => item.id !== entry.id)].slice(
    0,
    MAX_FEED_ENTRIES,
  );
}

export function activityFromTransaction(
  event: LiveTransactionEvent,
): ActivityFeedEntry {
  return {
    id: `tx-${event.transactionId}-${event.screenedAt ?? event.createdAt}`,
    kind: "transaction",
    title: "Transaction screened",
    detail: `${event.customerName} · ${event.transactionReference} · ${event.screeningStatus}`,
    status: event.screeningStatus,
    occurredAt: event.screenedAt ?? event.createdAt,
  };
}

export function activityFromInvestigationCreated(
  notification: InvestigationCreatedNotification,
): ActivityFeedEntry {
  return {
    id: `inv-created-${notification.investigationId}`,
    kind: "investigation-created",
    title: "Investigation created",
    detail: `${notification.title} · ${notification.priority} · ${formatStatusLabel(notification.status)}`,
    status: notification.status,
    occurredAt: notification.createdAt,
  };
}

export function activityFromInvestigationExecution(
  event: InvestigationExecutionEvent,
): ActivityFeedEntry {
  const label = formatStatusLabel(event.eventType.replaceAll("_", " "));
  return {
    id: `inv-exec-${event.investigationId}-${event.sequence}`,
    kind: "investigation-execution",
    title: label,
    detail: `${event.agentType ? `${event.agentType} · ` : ""}${event.message ?? event.stage ?? event.caseStatus}`,
    status: event.stageStatus ?? event.caseStatus,
    occurredAt: event.completedAt ?? event.startedAt ?? new Date().toISOString(),
  };
}

export function activityFromSseReconnect(stream: string): ActivityFeedEntry {
  const now = new Date().toISOString();
  return {
    id: `sse-reconnect-${stream}-${now}`,
    kind: "sse-reconnect",
    title: "SSE reconnect",
    detail: `${stream} live stream reconnected`,
    occurredAt: now,
  };
}
