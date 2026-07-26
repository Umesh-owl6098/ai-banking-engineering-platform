export interface DistributionEntry {
  label: string;
  value: number;
}

export interface OperationsDashboardKpis {
  transactionsProcessedToday: number;
  clearedTransactions: number;
  suspiciousTransactions: number;
  criticalTransactions: number;
  activeInvestigations: number;
  awaitingHumanReview: number;
  failedInvestigations: number;
  averageInvestigationDurationMs: number | null;
}

export interface AgentActivitySummary {
  agentType: string;
  runningCount: number;
  completedCount: number;
  failedCount: number;
  averageDurationMs: number | null;
}

export interface CriticalAlertGroup {
  groupKey: string;
  severity: string;
  customerName: string;
  scenarioLabel: string;
  screeningReason: string;
  totalAmount: number;
  currency: string;
  relatedTransactionCount: number;
  triggeredRules: string[];
  detectedAt: string;
  investigationId: string | null;
  investigationStatus: string | null;
}

export interface ActiveInvestigationRow {
  investigationId: string;
  reference: string;
  customerName: string;
  severity: string;
  pipelineStage: string;
  progressPercent: number;
  elapsedDurationMs: number;
  status: string;
}

export interface AwaitingReviewRow {
  investigationId: string;
  reference: string;
  customerName: string;
  severity: string;
  finalRecommendation: string;
  confidencePercent: number | null;
  waitingDurationMs: number;
  updatedAt: string;
}

export interface RecentScreenedTransactionRow {
  transactionId: string;
  transactionReference: string;
  customerName: string;
  amount: number;
  currency: string;
  route: string;
  screeningStatus: string;
  screeningReason: string;
  triggeredRules: string[];
  screenedAt: string | null;
  investigationId: string | null;
}

export interface RecentInvestigationRow {
  investigationId: string;
  reference: string;
  source: string;
  customerName: string;
  severity: string;
  status: string;
  createdAt: string;
}

export interface OperationsDashboardResponse {
  kpis: OperationsDashboardKpis;
  investigationsByStatus: DistributionEntry[];
  investigationsBySeverity: DistributionEntry[];
  screeningResults: DistributionEntry[];
  triggeredRuleFrequency: DistributionEntry[];
  agentActivity: AgentActivitySummary[];
  criticalAlerts: CriticalAlertGroup[];
  activeInvestigations: ActiveInvestigationRow[];
  awaitingReview: AwaitingReviewRow[];
  recentTransactions: RecentScreenedTransactionRow[];
  recentInvestigations: RecentInvestigationRow[];
  generatedAt: string;
}
