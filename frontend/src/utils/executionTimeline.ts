import type {
  ExecutionTimelineStage,
  InvestigationExecutionEvent,
  InvestigationExecutionStageStatus,
} from "../types/investigationExecution";
import type {
  AgentFinding,
  InvestigationCase,
  InvestigationReport,
  InvestigationTimelineEntry,
} from "../types/investigation";
import {
  AGENT_TYPES,
  latestFindingByAgent,
  parseStructuredFinding,
  scoreForFinding,
  type WorkspaceAgentType,
} from "./investigationWorkspace";

const DEFAULT_STAGES: ExecutionTimelineStage[] = [
  {
    id: "CREATED",
    label: "Investigation Created",
    stageStatus: "WAITING",
    startedAt: null,
    completedAt: null,
    durationMs: null,
  },
  {
    id: "SUPERVISOR",
    label: "Supervisor",
    stageStatus: "WAITING",
    startedAt: null,
    completedAt: null,
    durationMs: null,
  },
  {
    id: "REPORT",
    label: "Report",
    stageStatus: "WAITING",
    startedAt: null,
    completedAt: null,
    durationMs: null,
  },
  {
    id: "REVIEW",
    label: "Human Review",
    stageStatus: "WAITING",
    startedAt: null,
    completedAt: null,
    durationMs: null,
  },
];

function agentStageId(agentType: string): string {
  return `AGENT_${agentType}`;
}

function agentStageLabel(agentType: string): string {
  const labels: Record<string, string> = {
    FRAUD: "Fraud",
    KYC: "KYC",
    AML: "AML",
    COMPLIANCE: "Compliance",
  };

  return labels[agentType] ?? agentType.replaceAll("_", " ");
}

function createAgentStage(agentType: string): ExecutionTimelineStage {
  return {
    id: agentStageId(agentType),
    label: agentStageLabel(agentType),
    stageStatus: "WAITING",
    startedAt: null,
    completedAt: null,
    durationMs: null,
  };
}

function upsertStage(
  stages: ExecutionTimelineStage[],
  stage: ExecutionTimelineStage,
): ExecutionTimelineStage[] {
  const index = stages.findIndex((item) => item.id === stage.id);
  if (index >= 0) {
    const updated = [...stages];
    updated[index] = stage;
    return updated;
  }

  return [...stages, stage];
}

function updateStage(
  stages: ExecutionTimelineStage[],
  stageId: string,
  patch: Partial<ExecutionTimelineStage>,
): ExecutionTimelineStage[] {
  return stages.map((stage) =>
    stage.id === stageId ? { ...stage, ...patch } : stage,
  );
}

function insertAgentStages(
  stages: ExecutionTimelineStage[],
  plannedAgents: string[],
): ExecutionTimelineStage[] {
  const supervisorIndex = stages.findIndex(
    (stage) => stage.id === "SUPERVISOR",
  );
  const reportIndex = stages.findIndex((stage) => stage.id === "REPORT");
  const insertAt = reportIndex >= 0 ? reportIndex : stages.length;

  const withoutAgents = stages.filter(
    (stage) => !stage.id.startsWith("AGENT_"),
  );
  const agentStages = plannedAgents.map(createAgentStage);
  const head = withoutAgents.slice(
    0,
    supervisorIndex >= 0 ? supervisorIndex + 1 : insertAt,
  );
  const tail = withoutAgents.slice(
    supervisorIndex >= 0 ? supervisorIndex + 1 : insertAt,
  );

  return [...head, ...agentStages, ...tail];
}

export function createInitialExecutionTimeline(): ExecutionTimelineStage[] {
  return DEFAULT_STAGES.map((stage) => ({ ...stage }));
}

export function applyExecutionEvent(
  stages: ExecutionTimelineStage[],
  event: InvestigationExecutionEvent,
): ExecutionTimelineStage[] {
  let nextStages = stages;

  switch (event.eventType) {
    case "INVESTIGATION_CREATED":
      nextStages = updateStage(nextStages, "CREATED", {
        stageStatus: "COMPLETED",
        startedAt: event.startedAt,
        completedAt: event.completedAt,
        durationMs: event.durationMs,
        summary: event.message,
      });
      break;
    case "SUPERVISOR_STARTED":
      nextStages = updateStage(nextStages, "SUPERVISOR", {
        stageStatus: "RUNNING",
        startedAt: event.startedAt,
        summary: event.message,
      });
      break;
    case "SUPERVISOR_COMPLETED":
      if (event.plannedAgents && event.plannedAgents.length > 0) {
        nextStages = insertAgentStages(nextStages, event.plannedAgents);
      }
      nextStages = updateStage(nextStages, "SUPERVISOR", {
        stageStatus: "COMPLETED",
        startedAt: event.startedAt,
        completedAt: event.completedAt,
        durationMs: event.durationMs,
        summary: event.message,
      });
      break;
    case "AGENT_STARTED":
      if (event.agentType) {
        nextStages = upsertStage(nextStages, createAgentStage(event.agentType));
        nextStages = updateStage(nextStages, agentStageId(event.agentType), {
          stageStatus: "RUNNING",
          startedAt: event.startedAt,
          summary: event.message,
        });
      }
      break;
    case "AGENT_COMPLETED":
      if (event.agentType) {
        nextStages = updateStage(nextStages, agentStageId(event.agentType), {
          stageStatus: "COMPLETED",
          startedAt: event.startedAt,
          completedAt: event.completedAt,
          durationMs: event.durationMs,
          summary: event.message,
        });
      }
      break;
    case "AGENT_FAILED":
      if (event.agentType) {
        nextStages = updateStage(nextStages, agentStageId(event.agentType), {
          stageStatus: "FAILED",
          startedAt: event.startedAt,
          completedAt: event.completedAt,
          durationMs: event.durationMs,
          summary: event.message,
        });
      }
      break;
    case "REPORT_GENERATION_STARTED":
      nextStages = updateStage(nextStages, "REPORT", {
        stageStatus: "RUNNING",
        startedAt: event.startedAt,
        summary: event.message,
      });
      break;
    case "REPORT_GENERATED":
      nextStages = updateStage(nextStages, "REPORT", {
        stageStatus: "COMPLETED",
        startedAt: event.startedAt,
        completedAt: event.completedAt,
        durationMs: event.durationMs,
        summary: event.message,
      });
      break;
    case "INVESTIGATION_READY_FOR_REVIEW":
      nextStages = updateStage(nextStages, "REVIEW", {
        stageStatus: "COMPLETED",
        startedAt: event.startedAt,
        completedAt: event.completedAt,
        durationMs: event.durationMs,
        summary: event.message,
      });
      break;
    case "EXECUTION_FAILED":
      if (event.stage === "REPORT_GENERATION") {
        nextStages = updateStage(nextStages, "REPORT", {
          stageStatus: "FAILED",
          startedAt: event.startedAt,
          completedAt: event.completedAt,
          durationMs: event.durationMs,
          summary: event.message,
        });
      } else if (event.stage === "AGENT_EXECUTION") {
        nextStages = updateStage(nextStages, "SUPERVISOR", {
          stageStatus: "FAILED",
          startedAt: event.startedAt,
          completedAt: event.completedAt,
          durationMs: event.durationMs,
          summary: event.message,
        });
      }
      break;
    default:
      break;
  }

  return nextStages;
}

function durationBetween(
  startedAt: string | null | undefined,
  completedAt: string | null | undefined,
): number | null {
  if (!startedAt || !completedAt) {
    return null;
  }

  const duration = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  return duration >= 0 ? duration : null;
}

function agentFindingSummary(
  agentType: WorkspaceAgentType,
  finding: AgentFinding,
): string {
  const structured = parseStructuredFinding(finding.structuredJson ?? null);
  const score = scoreForFinding(agentType, structured);
  const parts = [
    finding.riskLevel,
    score != null ? `Score ${score}` : null,
    structured.recommendation ?? finding.summary,
  ].filter(Boolean);

  return parts.join(" · ");
}

function mapFindingStatus(
  status: string,
): InvestigationExecutionStageStatus {
  switch (status) {
    case "COMPLETE":
    case "COMPLETED":
      return "COMPLETED";
    case "RUNNING":
    case "IN_PROGRESS":
      return "RUNNING";
    case "FAILED":
    case "PARSE_FAILED":
      return "FAILED";
    default:
      return "WAITING";
  }
}

const EXECUTION_STARTED_STATUSES = new Set([
  "RUNNING",
  "REPORT_GENERATED",
  "AWAITING_REVIEW",
  "APPROVED",
  "REJECTED",
  "CLOSED",
  "ESCALATED",
  "INVESTIGATING",
]);

const REPORT_COMPLETE_STATUSES = new Set([
  "REPORT_GENERATED",
  "AWAITING_REVIEW",
  "APPROVED",
  "REJECTED",
  "CLOSED",
  "ESCALATED",
]);

const REVIEW_DECIDED_STATUSES = new Set([
  "APPROVED",
  "REJECTED",
  "CLOSED",
  "ESCALATED",
]);

const EXECUTION_FAILED_STATUS = "EXECUTION_FAILED";

const AUDIT_TIMELINE_STAGE_MAPPINGS: Array<{
  label: string;
  stageId: string;
  agentType?: WorkspaceAgentType;
}> = [
  { label: "Supervisor Planned", stageId: "SUPERVISOR" },
  { label: "Fraud Completed", stageId: "AGENT_FRAUD", agentType: "FRAUD" },
  { label: "KYC Completed", stageId: "AGENT_KYC", agentType: "KYC" },
  { label: "AML Completed", stageId: "AGENT_AML", agentType: "AML" },
  {
    label: "Compliance Completed",
    stageId: "AGENT_COMPLIANCE",
    agentType: "COMPLIANCE",
  },
  { label: "AI Report Generated", stageId: "REPORT" },
];

function findingForAgent(
  findings: AgentFinding[],
  agentType: WorkspaceAgentType,
): AgentFinding | null {
  return latestFindingByAgent(findings, agentType);
}

function applyAuditTimelineEntry(
  stages: ExecutionTimelineStage[],
  entry: InvestigationTimelineEntry,
  findings: AgentFinding[],
): ExecutionTimelineStage[] {
  const mapping = AUDIT_TIMELINE_STAGE_MAPPINGS.find(
    (item) => item.label === entry.label,
  );

  if (!mapping) {
    return stages;
  }

  if (mapping.agentType) {
    stages = upsertStage(stages, createAgentStage(mapping.agentType));
  }

  const finding = mapping.agentType
    ? findingForAgent(findings, mapping.agentType)
    : null;

  return updateStage(stages, mapping.stageId, {
    stageStatus: "COMPLETED",
    startedAt:
      finding?.startedAt
      ?? finding?.createdAt
      ?? entry.occurredAt,
    completedAt: finding?.completedAt ?? entry.occurredAt,
    durationMs: durationBetween(
      finding?.startedAt ?? finding?.createdAt ?? entry.occurredAt,
      finding?.completedAt ?? entry.occurredAt,
    ),
    summary:
      finding && mapping.agentType
        ? agentFindingSummary(mapping.agentType, finding)
        : entry.label,
  });
}

export function hydrateExecutionTimelineFromPersisted(
  investigation: InvestigationCase,
  findings: AgentFinding[],
  report: InvestigationReport | null,
  auditTimeline: InvestigationTimelineEntry[] = [],
): ExecutionTimelineStage[] {
  let stages = createInitialExecutionTimeline();

  stages = updateStage(stages, "CREATED", {
    stageStatus: "COMPLETED",
    startedAt: investigation.createdAt,
    completedAt: investigation.createdAt,
    durationMs: 0,
    summary: investigation.autoCreated
      ? "Auto-created from transaction screening"
      : "Investigation opened",
  });

  const completedAgents = AGENT_TYPES.filter(
    (agentType) => findingForAgent(findings, agentType) != null,
  );

  const executionStarted =
    EXECUTION_STARTED_STATUSES.has(investigation.status)
    || completedAgents.length > 0
    || auditTimeline.some((entry) =>
      AUDIT_TIMELINE_STAGE_MAPPINGS.some(
        (mapping) => mapping.label === entry.label,
      ),
    );

  if (executionStarted) {
    stages = insertAgentStages(
      stages,
      completedAgents.length > 0 ? completedAgents : [...AGENT_TYPES],
    );
  }

  for (const entry of auditTimeline) {
    stages = applyAuditTimelineEntry(stages, entry, findings);
  }

  if (investigation.status === "RUNNING" && completedAgents.length === 0) {
    stages = updateStage(stages, "SUPERVISOR", {
      stageStatus: auditTimeline.some(
        (entry) => entry.label === "Supervisor Planned",
      )
        ? "COMPLETED"
        : "RUNNING",
      startedAt: investigation.createdAt,
      summary: "Planning agent execution",
    });
  } else if (executionStarted) {
    const firstFindingStartedAt = completedAgents
      .map((agentType) => findingForAgent(findings, agentType)?.startedAt)
      .find(Boolean);
    const supervisorCompletedAt =
      auditTimeline.find((entry) => entry.label === "Supervisor Planned")
        ?.occurredAt
      ?? firstFindingStartedAt
      ?? investigation.updatedAt;

    stages = updateStage(stages, "SUPERVISOR", {
      stageStatus: "COMPLETED",
      startedAt: investigation.createdAt,
      completedAt: supervisorCompletedAt,
      durationMs: durationBetween(
        investigation.createdAt,
        supervisorCompletedAt,
      ),
      summary:
        completedAgents.length > 0
          ? `Planned ${completedAgents.length} specialist agents`
          : "Supervisor planning completed",
    });
  }

  for (const agentType of AGENT_TYPES) {
    const finding = findingForAgent(findings, agentType);
    if (!finding) {
      if (
        executionStarted
        && !auditTimeline.some(
          (entry) =>
            entry.label
            === AUDIT_TIMELINE_STAGE_MAPPINGS.find(
              (mapping) => mapping.agentType === agentType,
            )?.label,
        )
      ) {
        continue;
      }
      continue;
    }

    stages = upsertStage(stages, createAgentStage(agentType));
    stages = updateStage(stages, agentStageId(agentType), {
      stageStatus: mapFindingStatus(finding.status),
      startedAt: finding.startedAt ?? finding.createdAt,
      completedAt: finding.completedAt,
      durationMs: durationBetween(
        finding.startedAt ?? finding.createdAt,
        finding.completedAt,
      ),
      summary: agentFindingSummary(agentType, finding),
    });
  }

  const reportComplete =
    report != null || REPORT_COMPLETE_STATUSES.has(investigation.status);

  if (reportComplete) {
    const reportEntry = auditTimeline.find(
      (entry) => entry.label === "AI Report Generated",
    );
    stages = updateStage(stages, "REPORT", {
      stageStatus: "COMPLETED",
      startedAt:
        report?.metadata.generatedAt
        ?? reportEntry?.occurredAt
        ?? investigation.updatedAt,
      completedAt:
        report?.metadata.generatedAt
        ?? reportEntry?.occurredAt
        ?? investigation.updatedAt,
      durationMs: report?.metadata.generationDurationMs ?? null,
      summary: report
        ? `${report.metadata.generationMode} report generated`
        : "Investigation report generated",
    });
  } else if (investigation.status === "RUNNING" && completedAgents.length === AGENT_TYPES.length) {
    stages = updateStage(stages, "REPORT", {
      stageStatus: "RUNNING",
      startedAt: investigation.updatedAt,
      summary: "Generating investigation report",
    });
  }

  if (investigation.status === "AWAITING_REVIEW") {
    stages = updateStage(stages, "REVIEW", {
      stageStatus: "RUNNING",
      startedAt: investigation.updatedAt,
      summary: "Awaiting Review",
    });
  } else if (investigation.status === EXECUTION_FAILED_STATUS) {
    stages = updateStage(stages, "REVIEW", {
      stageStatus: "FAILED",
      startedAt:
        investigation.executionFailureAt ?? investigation.updatedAt,
      completedAt:
        investigation.executionFailureAt ?? investigation.updatedAt,
      summary:
        investigation.executionFailureMessage
        ?? "Investigation execution failed",
    });
  } else if (REVIEW_DECIDED_STATUSES.has(investigation.status)) {
    const decisionEntry = auditTimeline.find(
      (entry) => entry.label === "Decision Recorded",
    );
    stages = updateStage(stages, "REVIEW", {
      stageStatus: "COMPLETED",
      startedAt: decisionEntry?.occurredAt ?? investigation.updatedAt,
      completedAt: decisionEntry?.occurredAt ?? investigation.updatedAt,
      summary: `Review outcome: ${investigation.status}`,
    });
  } else if (
    auditTimeline.some((entry) => entry.label === "Human Review Started")
  ) {
    stages = updateStage(stages, "REVIEW", {
      stageStatus: "RUNNING",
      summary: "Human review in progress",
    });
  }

  return stages;
}

export function computeTotalExecutionDuration(
  investigation: InvestigationCase,
  stages: ExecutionTimelineStage[],
): string | null {
  const timestamps = stages
    .flatMap((stage) => [stage.startedAt, stage.completedAt])
    .filter((value): value is string => Boolean(value))
    .map((value) => new Date(value).getTime());

  if (timestamps.length === 0) {
    return null;
  }

  const start = new Date(investigation.createdAt).getTime();
  const end = Math.max(...timestamps, new Date(investigation.updatedAt).getTime());
  const durationMs = end - start;

  return formatStageDuration(durationMs);
}

export function formatStageDuration(
  durationMs: number | null | undefined,
): string | null {
  if (durationMs == null) {
    return null;
  }

  if (durationMs < 1000) {
    return `${durationMs} ms`;
  }

  return `${(durationMs / 1000).toFixed(1)} s`;
}

export function stageStatusLabel(
  status: InvestigationExecutionStageStatus,
): string {
  switch (status) {
    case "RUNNING":
      return "Running";
    case "COMPLETED":
      return "Completed";
    case "FAILED":
      return "Failed";
    default:
      return "Waiting";
  }
}

export function stageStatusColor(
  status: InvestigationExecutionStageStatus,
): "default" | "info" | "success" | "error" | "warning" {
  switch (status) {
    case "RUNNING":
      return "info";
    case "COMPLETED":
      return "success";
    case "FAILED":
      return "error";
    default:
      return "default";
  }
}
