import type { AgentActivitySummary } from "./dashboard";

export interface HealthComponentStatus {
  component: string;
  status: string;
  message: string;
}

export interface PlatformHealthSummary {
  overallStatus: string;
  components: HealthComponentStatus[];
}

export interface InvestigationMetricsSummary {
  transactionsProcessedToday: number;
  investigationsCreatedToday: number;
  criticalInvestigations: number;
  awaitingAnalystReview: number;
  closedInvestigations: number;
  failedInvestigations: number;
}

export interface OperationsErrorEntry {
  errorType: string;
  message: string;
  source: string;
  investigationId: string | null;
  occurredAt: string;
}

export interface OperationsCenterResponse {
  platformHealth: PlatformHealthSummary;
  investigationMetrics: InvestigationMetricsSummary;
  agentPerformance: AgentActivitySummary[];
  recentErrors: OperationsErrorEntry[];
  executionFailureTotal: number;
  reportFallbackTotal: number;
  reportFailureTotal: number;
  generatedAt: string;
}

export type ActivityFeedKind =
  | "transaction"
  | "investigation-created"
  | "investigation-execution"
  | "sse-reconnect";

export interface ActivityFeedEntry {
  id: string;
  kind: ActivityFeedKind;
  title: string;
  detail: string;
  status?: string;
  occurredAt: string;
}
