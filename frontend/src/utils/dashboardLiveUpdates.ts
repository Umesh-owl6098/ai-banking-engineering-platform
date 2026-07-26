import type {
  AwaitingReviewRow,
  CriticalAlertGroup,
  OperationsDashboardResponse,
  RecentInvestigationRow,
  RecentScreenedTransactionRow,
} from "../types/dashboard";
import type { InvestigationCreatedNotification } from "../types/investigation";
import type { InvestigationExecutionEvent } from "../types/investigationExecution";
import type { LiveTransactionEvent } from "../types/simulation";

function isToday(value: string): boolean {
  const date = new Date(value);
  const now = new Date();
  return (
    date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate()
  );
}

function alertGroupKey(event: LiveTransactionEvent): string {
  return event.scenarioGroupId
    ?? `${event.customerName}:${event.screeningStatus}`;
}

export function liveEventToAlertGroup(
  event: LiveTransactionEvent,
): CriticalAlertGroup {
  return {
    groupKey: alertGroupKey(event),
    severity: event.screeningStatus,
    customerName: event.customerName,
    scenarioLabel: event.demoScenario ?? event.scenario,
    screeningReason: event.screeningReason,
    totalAmount: event.amount,
    currency: event.currency,
    relatedTransactionCount: 1,
    triggeredRules: event.triggeredRules,
    detectedAt: event.screenedAt ?? event.createdAt,
    investigationId: event.investigationId,
    investigationStatus: event.investigationId ? "NEW" : null,
  };
}

export function mergeAlertGroups(
  existing: CriticalAlertGroup[],
  incoming: CriticalAlertGroup,
): CriticalAlertGroup[] {
  const index = existing.findIndex(
    (alert) => alert.groupKey === incoming.groupKey,
  );

  if (index < 0) {
    return [incoming, ...existing].slice(0, 12);
  }

  const current = existing[index];
  const merged: CriticalAlertGroup = {
    ...incoming,
    relatedTransactionCount:
      current.relatedTransactionCount + incoming.relatedTransactionCount,
    totalAmount: current.totalAmount + incoming.totalAmount,
    triggeredRules: [
      ...new Set([...current.triggeredRules, ...incoming.triggeredRules]),
    ],
    investigationId: incoming.investigationId ?? current.investigationId,
    investigationStatus:
      incoming.investigationStatus ?? current.investigationStatus,
    detectedAt:
      new Date(incoming.detectedAt).getTime()
      >= new Date(current.detectedAt).getTime()
        ? incoming.detectedAt
        : current.detectedAt,
  };

  const next = [...existing];
  next.splice(index, 1);
  return [merged, ...next].slice(0, 12);
}

export function liveEventToRecentTransaction(
  event: LiveTransactionEvent,
): RecentScreenedTransactionRow {
  return {
    transactionId: String(event.transactionId),
    transactionReference: event.transactionReference,
    customerName: event.customerName,
    amount: event.amount,
    currency: event.currency,
    route: `${event.originCountry} → ${event.destinationCountry}`,
    screeningStatus: event.screeningStatus,
    screeningReason: event.screeningReason,
    triggeredRules: event.triggeredRules,
    screenedAt: event.screenedAt,
    investigationId: event.investigationId,
  };
}

export function upsertRecentTransaction(
  existing: RecentScreenedTransactionRow[],
  incoming: RecentScreenedTransactionRow,
): RecentScreenedTransactionRow[] {
  const filtered = existing.filter(
    (row) => row.transactionId !== incoming.transactionId,
  );
  return [incoming, ...filtered].slice(0, 15);
}

export function applyLiveTransactionEvent(
  dashboard: OperationsDashboardResponse,
  event: LiveTransactionEvent,
): OperationsDashboardResponse {
  const screenedAt = event.screenedAt ?? event.createdAt;
  const kpis = { ...dashboard.kpis };

  if (isToday(screenedAt)) {
    kpis.transactionsProcessedToday += 1;
    if (event.screeningStatus === "CLEARED") {
      kpis.clearedTransactions += 1;
    }
    if (event.screeningStatus === "SUSPICIOUS") {
      kpis.suspiciousTransactions += 1;
    }
    if (event.screeningStatus === "CRITICAL") {
      kpis.criticalTransactions += 1;
    }
  }

  let criticalAlerts = dashboard.criticalAlerts;
  if (
    event.screeningStatus === "SUSPICIOUS"
    || event.screeningStatus === "CRITICAL"
  ) {
    criticalAlerts = mergeAlertGroups(
      dashboard.criticalAlerts,
      liveEventToAlertGroup(event),
    );
  }

  const recentTransactions = upsertRecentTransaction(
    dashboard.recentTransactions,
    liveEventToRecentTransaction(event),
  );

  return {
    ...dashboard,
    kpis,
    criticalAlerts,
    recentTransactions,
    generatedAt: new Date().toISOString(),
  };
}

export function applyInvestigationCreated(
  dashboard: OperationsDashboardResponse,
  notification: InvestigationCreatedNotification,
): OperationsDashboardResponse {
  const kpis = { ...dashboard.kpis };
  if (["NEW", "RUNNING", "REPORT_GENERATED"].includes(notification.status)) {
    kpis.activeInvestigations += 1;
  }

  const activeInvestigations = [
    {
      investigationId: notification.investigationId,
      reference: notification.title,
      customerName: "Loading…",
      severity: notification.priority,
      pipelineStage: "SUPERVISOR",
      progressPercent: 5,
      elapsedDurationMs: 0,
      status: notification.status,
    },
    ...dashboard.activeInvestigations.filter(
      (row) => row.investigationId !== notification.investigationId,
    ),
  ].slice(0, 12);

  const recentInvestigations: RecentInvestigationRow[] = [
    {
      investigationId: notification.investigationId,
      reference: notification.title,
      source: notification.autoCreated ? "Auto-screening" : "Manual",
      customerName: "Loading…",
      severity: notification.priority,
      status: notification.status,
      createdAt: notification.createdAt,
    },
    ...dashboard.recentInvestigations.filter(
      (row) => row.investigationId !== notification.investigationId,
    ),
  ].slice(0, 12);

  const criticalAlerts = dashboard.criticalAlerts.map((alert) =>
    alert.investigationId == null
      && notification.transactionId
      && alert.groupKey.includes(notification.investigationId)
      ? {
          ...alert,
          investigationId: notification.investigationId,
          investigationStatus: notification.status,
        }
      : alert,
  );

  return {
    ...dashboard,
    kpis,
    activeInvestigations,
    recentInvestigations,
    criticalAlerts,
    generatedAt: new Date().toISOString(),
  };
}

export function applyInvestigationExecution(
  dashboard: OperationsDashboardResponse,
  event: InvestigationExecutionEvent,
): OperationsDashboardResponse {
  let kpis = { ...dashboard.kpis };
  let activeInvestigations = [...dashboard.activeInvestigations];
  let awaitingReview = [...dashboard.awaitingReview];

  const activeIndex = activeInvestigations.findIndex(
    (row) => row.investigationId === event.investigationId,
  );

  if (activeIndex >= 0) {
    const current = activeInvestigations[activeIndex];
    const completedStages = Object.values(current).length;
    activeInvestigations[activeIndex] = {
      ...current,
      status: event.caseStatus,
      pipelineStage: event.stage ?? current.pipelineStage,
      progressPercent: Math.min(
        100,
        Math.round(((completedStages + 1) * 100) / 6),
      ),
      elapsedDurationMs:
        event.durationMs != null
          ? event.durationMs
          : current.elapsedDurationMs,
    };
  }

  if (event.caseStatus === "AWAITING_REVIEW") {
    kpis.activeInvestigations = Math.max(0, kpis.activeInvestigations - 1);
    kpis.awaitingHumanReview += 1;

    activeInvestigations = activeInvestigations.filter(
      (row) => row.investigationId !== event.investigationId,
    );

    const awaitingRow: AwaitingReviewRow = {
      investigationId: event.investigationId,
      reference: event.investigationId.slice(0, 8),
      customerName: "—",
      severity: "HIGH",
      finalRecommendation: "Pending review",
      confidencePercent: null,
      waitingDurationMs: 0,
      updatedAt: event.completedAt ?? new Date().toISOString(),
    };

    awaitingReview = [
      awaitingRow,
      ...awaitingReview.filter(
        (row) => row.investigationId !== event.investigationId,
      ),
    ].slice(0, 12);
  }

  if (event.caseStatus === "EXECUTION_FAILED") {
    kpis.activeInvestigations = Math.max(0, kpis.activeInvestigations - 1);
    kpis.failedInvestigations += 1;
    activeInvestigations = activeInvestigations.filter(
      (row) => row.investigationId !== event.investigationId,
    );
  }

  const recentInvestigations = dashboard.recentInvestigations.map((row) =>
    row.investigationId === event.investigationId
      ? { ...row, status: event.caseStatus }
      : row,
  );

  const criticalAlerts = dashboard.criticalAlerts.map((alert) =>
    alert.investigationId === event.investigationId
      ? { ...alert, investigationStatus: event.caseStatus }
      : alert,
  );

  return {
    ...dashboard,
    kpis,
    activeInvestigations,
    awaitingReview,
    recentInvestigations,
    criticalAlerts,
    generatedAt: new Date().toISOString(),
  };
}

export function isSuspiciousLiveEvent(event: LiveTransactionEvent): boolean {
  return (
    event.screeningStatus === "SUSPICIOUS"
    || event.screeningStatus === "CRITICAL"
  );
}
