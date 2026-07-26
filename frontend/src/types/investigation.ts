export interface InvestigationCase {
  id: string;
  projectId: string;
  conversationId: string | null;
  customerId: string | null;
  transactionId: string | null;
  caseType: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  analystId: string | null;
  autoCreated: boolean;
  screeningStatus: string | null;
  screeningReason: string | null;
  screeningTriggeredRules: string[];
  createdAt: string;
  updatedAt: string;
  executionFailureStage?: string | null;
  executionFailureMessage?: string | null;
  executionFailureAt?: string | null;
  assignedAnalystId?: string | null;
  assignedAnalystUsername?: string | null;
  assignedAt?: string | null;
  reviewStartedAt?: string | null;
  assignmentNotes?: string | null;
}

export interface InvestigationCreatedNotification {
  investigationId: string;
  projectId: string;
  customerId: string | null;
  transactionId: string | null;
  title: string;
  status: string;
  priority: string;
  autoCreated: boolean;
  screeningStatus: string | null;
  screeningReason: string | null;
  screeningTriggeredRules: string[];
  createdAt: string;
}

export interface InvestigationCaseCreateRequest {
  projectId: string;
  customerId?: string;
  transactionId?: string;
  caseType: string;
  title: string;
  description: string;
  priority: string;
}

export interface InvestigationStatusUpdateRequest {
  status: string;
}

export interface AgentFindingCitation {
  id: string;
  fileName: string;
  chunkIndex: number;
  similarity: number | null;
  contentPreview: string | null;
  createdAt: string;
}

export interface AgentFinding {
  id: string;
  agentType: string;
  status: string;
  riskLevel: string | null;
  confidence: number | null;
  summary: string | null;
  structuredJson: string | Record<string, unknown> | null;
  ragQuery: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  citations: AgentFindingCitation[];
}

export interface ParsedStructuredFinding {
  recommendation?: string;
  fraudScore?: number;
  kycScore?: number;
  amlScore?: number;
  overallScore?: number;
  riskLevel?: string;
  triggeredIndicators?: Array<{
    type?: string;
    explanation?: string;
    scoreContribution?: number;
  }>;
  contributingFindings?: Array<{
    type?: string;
    explanation?: string;
    scoreContribution?: number;
  }>;
}

export interface InvestigationReportSection {
  title: string;
  narrative: string;
  deterministicFacts: Record<string, unknown>;
}

export interface InvestigationReportMetadata {
  promptVersion: string;
  generatedAt: string;
  modelName: string;
  generationDurationMs: number;
  generationMode: string;
}

export interface InvestigationReport {
  id: string;
  investigationId: string;
  metadata: InvestigationReportMetadata;
  executiveSummary: string;
  investigationOverview: InvestigationReportSection;
  customerRiskProfile: InvestigationReportSection;
  fraudAnalysis: InvestigationReportSection;
  kycAnalysis: InvestigationReportSection;
  amlAnalysis: InvestigationReportSection;
  complianceAssessment: InvestigationReportSection;
  supportingEvidence: InvestigationReportSection[];
  analystRecommendation: string;
  confidenceExplanation: string;
  limitations: string;
}

export interface HumanReviewDecision {
  id: string;
  investigationId: string;
  reviewerId: string;
  decision: string;
  action: string;
  decisionReason: string | null;
  comments: string | null;
  additionalNotes: string | null;
  decisionAt: string;
}

export interface InvestigationTimelineEntry {
  sequence: number;
  label: string;
  eventType: string;
  occurredAt: string;
  actor: string | null;
  payload: Record<string, unknown>;
}

export interface InvestigationReviewSummary {
  reviewStatus: string;
  reviewUser: string | null;
  decision: string | null;
  reviewStartedAt: string | null;
  decisionAt: string | null;
}

export interface InvestigationReviewContext {
  investigation: InvestigationCase;
  report: InvestigationReport | null;
  reviewSummary: InvestigationReviewSummary;
  decisions: HumanReviewDecision[];
  timeline: InvestigationTimelineEntry[];
}

export interface HumanReviewDecisionRequest {
  decisionReason: string;
  comments?: string;
  additionalNotes?: string;
}

export interface ExplainabilityEvidence {
  citationId: string;
  documentName: string;
  chunkIndex: number;
  similarity: number | null;
  excerpt: string | null;
  relevanceExplanation: string;
}

export interface ExplainabilityRule {
  ruleCode: string;
  displayName: string;
  scoreContribution: number;
  explanation: string;
  description: string;
  evidenceValues: Record<string, unknown>;
  thresholds: Record<string, unknown>;
  relatedFields: Record<string, unknown>;
  confidenceContribution: number;
  supportingEvidence: ExplainabilityEvidence[];
}

export interface ExplainabilityResponse {
  findingId: string;
  investigationId: string;
  agentType: string;
  totalScore: number;
  riskLevel: string;
  recommendation: string;
  confidence: number;
  summary: string;
  triggeredRules: ExplainabilityRule[];
  relatedCustomerFields: Record<string, unknown>;
  relatedTransactionFields: Record<string, unknown>;
  supportingEvidence: ExplainabilityEvidence[];
}
