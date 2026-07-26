export type SimulationScenario =
  | "NORMAL"
  | "LARGE_TRANSFER"
  | "STRUCTURING"
  | "RAPID_MOVEMENT"
  | "HIGH_RISK_COUNTRY"
  | "PEP_ACTIVITY"
  | "NEW_ACCOUNT_ACTIVITY"
  | "MIXED";

export type TransactionScreeningStatus =
  | "PROCESSING"
  | "CLEARED"
  | "SUSPICIOUS"
  | "CRITICAL"
  | "SCREENING_FAILED";

export interface SimulationStatus {
  running: boolean;
  scenario: SimulationScenario;
  intervalMs: number;
  transactionsGenerated: number;
  startedAt: string | null;
}

export interface LiveTransactionEvent {
  transactionId: string;
  transactionReference: string;
  customerName: string;
  amount: number;
  currency: string;
  channel: string;
  transactionType: string;
  originCountry: string;
  destinationCountry: string;
  riskScore: number;
  flagged: boolean;
  scenario: SimulationScenario;
  demoScenario?: string | null;
  scenarioGroupId?: string | null;
  createdAt: string;
  screeningStatus: TransactionScreeningStatus;
  screeningReason: string;
  triggeredRules: string[];
  screenedAt: string | null;
  investigationId: string | null;
  lifecycleStatus: "PROCESSING" | "SCREENED" | "INVESTIGATION_CREATED";
}

export interface DemoTransactionResult {
  transactionId: string;
  transactionReference: string;
  screeningStatus: TransactionScreeningStatus;
  triggeredRules: string[];
  amount: number;
  currency: string;
}

export interface DemoScenarioRunResponse {
  scenario: string;
  scenarioGroupId: string;
  transactionsGenerated: number;
  transactions: DemoTransactionResult[];
  investigationId: string | null;
  investigationStatus: string | null;
  screeningSummary: string;
}

export type DemoScenarioKey =
  | "structuring"
  | "high-risk-wire"
  | "money-mule"
  | "normal";

export const SIMULATION_SCENARIOS: SimulationScenario[] = [
  "NORMAL",
  "LARGE_TRANSFER",
  "STRUCTURING",
  "RAPID_MOVEMENT",
  "HIGH_RISK_COUNTRY",
  "PEP_ACTIVITY",
  "NEW_ACCOUNT_ACTIVITY",
  "MIXED",
];
