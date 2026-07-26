import type { AgentFinding, InvestigationCase } from "../types/investigation";
import type { InvestigationExecutionEvent } from "../types/investigationExecution";
import type { LiveTransactionEvent } from "../types/simulation";

export interface DashboardKpis {
  transactionsToday: number;
  screenedTransactions: number;
  activeInvestigations: number;
  criticalInvestigations: number;
  awaitingReview: number;
  averageInvestigationTimeMs: number | null;
  aiSuccessRate: number | null;
}

export interface AiActivityStage {
  label: string;
  stageStatus: "WAITING" | "RUNNING" | "COMPLETED" | "FAILED";
  startedAt: string | null;
  completedAt: string | null;
  durationMs: number | null;
}

export interface AiActivityRow {
  investigationId: string;
  reference: string;
  customerName: string;
  caseStatus: string;
  stages: Record<string, AiActivityStage>;
  lastUpdatedAt: string;
}

const ACTIVE_STATUSES = new Set([
  "NEW",
  "RUNNING",
  "INVESTIGATING",
  "REPORT_GENERATED",
]);

const COMPLETED_STATUSES = new Set([
  "AWAITING_REVIEW",
  "APPROVED",
  "REJECTED",
  "CLOSED",
  "ESCALATED",
]);

const SCREENED_STATUSES = new Set([
  "CLEARED",
  "SUSPICIOUS",
  "CRITICAL",
  "SCREENING_FAILED",
]);

const ACTIVITY_STAGE_KEYS = [
  "SUPERVISOR",
  "FRAUD",
  "KYC",
  "AML",
  "COMPLIANCE",
] as const;

function isToday(value: string): boolean {
  const date = new Date(value);
  const now = new Date();
  return (
    date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate()
  );
}

function createStage(label: string): AiActivityStage {
  return {
    label,
    stageStatus: "WAITING",
    startedAt: null,
    completedAt: null,
    durationMs: null,
  };
}

export function createAiActivityRow(
  investigationId: string,
  reference: string,
  customerName: string,
  caseStatus: string,
): AiActivityRow {
  return {
    investigationId,
    reference,
    customerName,
    caseStatus,
    stages: {
      SUPERVISOR: createStage("Supervisor"),
      FRAUD: createStage("Fraud"),
      KYC: createStage("KYC"),
      AML: createStage("AML"),
      COMPLIANCE: createStage("Compliance"),
    },
    lastUpdatedAt: new Date().toISOString(),
  };
}

export function applyAiActivityEvent(
  row: AiActivityRow,
  event: InvestigationExecutionEvent,
): AiActivityRow {
  const next: AiActivityRow = {
    ...row,
    caseStatus: event.caseStatus,
    lastUpdatedAt:
      event.completedAt ?? event.startedAt ?? row.lastUpdatedAt,
    stages: { ...row.stages },
  };

  const patchStage = (
    key: string,
    patch: Partial<AiActivityStage>,
  ) => {
    const current = next.stages[key] ?? createStage(key);
    next.stages[key] = { ...current, ...patch };
  };

  switch (event.eventType) {
    case "SUPERVISOR_STARTED":
      patchStage("SUPERVISOR", {
        stageStatus: "RUNNING",
        startedAt: event.startedAt,
      });
      break;
    case "SUPERVISOR_COMPLETED":
      patchStage("SUPERVISOR", {
        stageStatus: "COMPLETED",
        startedAt: event.startedAt,
        completedAt: event.completedAt,
        durationMs: event.durationMs,
      });
      break;
    case "AGENT_STARTED":
      if (event.agentType) {
        patchStage(event.agentType, {
          stageStatus: "RUNNING",
          startedAt: event.startedAt,
        });
      }
      break;
    case "AGENT_COMPLETED":
      if (event.agentType) {
        patchStage(event.agentType, {
          stageStatus: "COMPLETED",
          startedAt: event.startedAt,
          completedAt: event.completedAt,
          durationMs: event.durationMs,
        });
      }
      break;
    case "AGENT_FAILED":
      if (event.agentType) {
        patchStage(event.agentType, {
          stageStatus: "FAILED",
          startedAt: event.startedAt,
          completedAt: event.completedAt,
          durationMs: event.durationMs,
        });
      }
      break;
    default:
      break;
  }

  return next;
}

export function computeDashboardKpis(
  investigations: InvestigationCase[],
  liveEvents: LiveTransactionEvent[],
  transactionsGenerated: number,
): DashboardKpis {
  const todayEvents = liveEvents.filter((event) => isToday(event.createdAt));
  const transactionsToday = Math.max(
    todayEvents.length,
    transactionsGenerated,
  );

  const screenedFromEvents = liveEvents.filter(
    (event) =>
      event.screeningStatus
      && SCREENED_STATUSES.has(event.screeningStatus),
  ).length;
  const screenedFromInvestigations = investigations.filter(
    (item) => item.screeningStatus,
  ).length;
  const screenedTransactions = Math.max(
    screenedFromEvents,
    screenedFromInvestigations,
  );

  const activeInvestigations = investigations.filter((item) =>
    ACTIVE_STATUSES.has(item.status),
  ).length;

  const criticalInvestigations = investigations.filter(
    (item) =>
      item.priority === "CRITICAL"
      || item.screeningStatus === "CRITICAL",
  ).length;

  const awaitingReview = investigations.filter(
    (item) => item.status === "AWAITING_REVIEW",
  ).length;

  const completedInvestigations = investigations.filter((item) =>
    COMPLETED_STATUSES.has(item.status),
  );
  const durations = completedInvestigations
    .map(
      (item) =>
        new Date(item.updatedAt).getTime()
        - new Date(item.createdAt).getTime(),
    )
    .filter((duration) => duration >= 0);
  const averageInvestigationTimeMs =
    durations.length > 0
      ? durations.reduce((sum, value) => sum + value, 0) / durations.length
      : null;

  const autoCreated = investigations.filter((item) => item.autoCreated);
  const successfulAutoCreated = autoCreated.filter((item) =>
    ["AWAITING_REVIEW", "APPROVED", "REJECTED", "CLOSED"].includes(
      item.status,
    ),
  ).length;
  const aiSuccessRate =
    autoCreated.length > 0
      ? Math.round((successfulAutoCreated / autoCreated.length) * 100)
      : null;

  return {
    transactionsToday,
    screenedTransactions,
    activeInvestigations,
    criticalInvestigations,
    awaitingReview,
    averageInvestigationTimeMs,
    aiSuccessRate,
  };
}

export function buildDistribution(
  items: string[],
): { label: string; value: number }[] {
  const counts = new Map<string, number>();

  for (const item of items) {
    const label = item.replaceAll("_", " ");
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }

  return [...counts.entries()]
    .map(([label, value]) => ({ label, value }))
    .sort((left, right) => right.value - left.value);
}

export function averageFindingConfidence(
  findings: AgentFinding[],
): number | null {
  const completed = findings.filter(
    (finding) =>
      finding.status === "COMPLETE" && finding.confidence != null,
  );

  if (completed.length === 0) {
    return null;
  }

  const total = completed.reduce(
    (sum, finding) => sum + (finding.confidence ?? 0),
    0,
  );
  return Math.round((total / completed.length) * 100);
}

export function formatDurationMs(durationMs: number | null): string {
  if (durationMs == null) {
    return "—";
  }

  if (durationMs < 1000) {
    return `${durationMs} ms`;
  }

  return `${(durationMs / 1000).toFixed(1)} s`;
}

export function formatAverageDuration(durationMs: number | null): string {
  if (durationMs == null) {
    return "—";
  }

  if (durationMs < 60_000) {
    return `${(durationMs / 1000).toFixed(1)} s`;
  }

  return `${(durationMs / 60_000).toFixed(1)} min`;
}

export function isSuspiciousAlert(event: LiveTransactionEvent): boolean {
  return (
    event.screeningStatus === "SUSPICIOUS"
    || event.screeningStatus === "CRITICAL"
  );
}

export function alertSeverity(
  status: LiveTransactionEvent["screeningStatus"],
): "CRITICAL" | "HIGH" | "MEDIUM" {
  if (status === "CRITICAL") {
    return "CRITICAL";
  }

  return "HIGH";
}

export { ACTIVITY_STAGE_KEYS };
