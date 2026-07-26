export type InvestigationExecutionEventType =
  | "INVESTIGATION_CREATED"
  | "SUPERVISOR_STARTED"
  | "SUPERVISOR_COMPLETED"
  | "AGENT_STARTED"
  | "AGENT_COMPLETED"
  | "AGENT_FAILED"
  | "REPORT_GENERATION_STARTED"
  | "REPORT_GENERATED"
  | "INVESTIGATION_READY_FOR_REVIEW"
  | "EXECUTION_FAILED";

export type InvestigationExecutionStageStatus =
  | "WAITING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED";

export interface InvestigationExecutionEvent {
  eventType: InvestigationExecutionEventType;
  investigationId: string;
  caseStatus: string;
  stage: string;
  stageStatus: InvestigationExecutionStageStatus;
  agentType: string | null;
  plannedAgents: string[] | null;
  startedAt: string | null;
  completedAt: string | null;
  durationMs: number | null;
  message: string | null;
  sequence: number;
}

export type InvestigationLiveEvent =
  | { kind: "created"; data: import("./investigation").InvestigationCreatedNotification }
  | { kind: "execution"; data: InvestigationExecutionEvent };

export interface ExecutionTimelineStage {
  id: string;
  label: string;
  stageStatus: InvestigationExecutionStageStatus;
  startedAt: string | null;
  completedAt: string | null;
  durationMs: number | null;
  summary?: string | null;
}
