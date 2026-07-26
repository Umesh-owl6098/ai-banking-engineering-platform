import api, { API_BASE_URL } from "../api/api";
import type {
  AgentFinding,
  HumanReviewDecisionRequest,
  InvestigationCase,
  InvestigationCaseCreateRequest,
  InvestigationCreatedNotification,
  InvestigationReport,
  InvestigationReviewContext,
  InvestigationStatusUpdateRequest,
  ExplainabilityResponse,
} from "../types/investigation";

export const INVESTIGATION_PROJECT_ID =
  import.meta.env.VITE_PROJECT_ID ??
  "8c0c0dee-dd8e-4419-bef3-a2e93c10a726";

export async function getProjectInvestigations(
  projectId: string,
  status?: string,
): Promise<InvestigationCase[]> {
  const response = await api.get<InvestigationCase[]>(
    `/projects/${projectId}/investigations`,
    {
      params: status ? { status } : undefined,
    },
  );

  return response.data;
}

export async function getInvestigation(
  investigationId: string,
): Promise<InvestigationCase> {
  const response = await api.get<InvestigationCase>(
    `/investigations/${investigationId}`,
  );

  return response.data;
}

export async function createInvestigation(
  request: InvestigationCaseCreateRequest,
): Promise<InvestigationCase> {
  const response = await api.post<InvestigationCase>(
    "/investigations",
    request,
  );

  return response.data;
}

export function getInvestigationsLiveUrl(): string {
  return `${API_BASE_URL}/investigations/live`;
}

export type { InvestigationCreatedNotification };

export async function getInvestigationFindings(
  investigationId: string,
): Promise<AgentFinding[]> {
  const response = await api.get<AgentFinding[]>(
    `/investigations/${investigationId}/findings`,
  );

  return response.data;
}

export async function getInvestigationExplainability(
  investigationId: string,
): Promise<ExplainabilityResponse[]> {
  const response = await api.get<ExplainabilityResponse[]>(
    `/investigations/${investigationId}/explainability`,
  );

  return response.data;
}

export async function updateInvestigationStatus(
  investigationId: string,
  request: InvestigationStatusUpdateRequest,
): Promise<InvestigationCase> {
  const response = await api.patch<InvestigationCase>(
    `/investigations/${investigationId}/status`,
    request,
  );

  return response.data;
}

export async function getInvestigationReport(
  investigationId: string,
): Promise<InvestigationReport | null> {
  try {
    const response = await api.get<InvestigationReport>(
      `/investigations/${investigationId}/report`,
    );
    return response.data;
  } catch {
    return null;
  }
}

export async function retryInvestigationExecution(
  investigationId: string,
): Promise<InvestigationCase> {
  const response = await api.post<InvestigationCase>(
    `/investigations/${investigationId}/execute`,
  );

  return response.data;
}

export async function getInvestigationReview(
  investigationId: string,
): Promise<InvestigationReviewContext> {
  const response = await api.get<InvestigationReviewContext>(
    `/investigations/${investigationId}/review`,
  );

  return response.data;
}

export async function startInvestigationReview(
  investigationId: string,
): Promise<InvestigationReviewContext> {
  const response = await api.post<InvestigationReviewContext>(
    `/investigations/${investigationId}/review/start`,
  );

  return response.data;
}

async function submitReviewDecision(
  investigationId: string,
  action:
    | "approve"
    | "reject"
    | "escalate"
    | "request-more-investigation",
  request: HumanReviewDecisionRequest,
): Promise<InvestigationReviewContext> {
  const response = await api.post<InvestigationReviewContext>(
    `/investigations/${investigationId}/decisions/${action}`,
    request,
  );

  return response.data;
}

export async function approveInvestigation(
  investigationId: string,
  request: HumanReviewDecisionRequest,
): Promise<InvestigationReviewContext> {
  return submitReviewDecision(investigationId, "approve", request);
}

export async function rejectInvestigation(
  investigationId: string,
  request: HumanReviewDecisionRequest,
): Promise<InvestigationReviewContext> {
  return submitReviewDecision(investigationId, "reject", request);
}

export async function escalateInvestigation(
  investigationId: string,
  request: HumanReviewDecisionRequest,
): Promise<InvestigationReviewContext> {
  return submitReviewDecision(investigationId, "escalate", request);
}

export async function requestMoreInvestigation(
  investigationId: string,
  request: HumanReviewDecisionRequest,
): Promise<InvestigationReviewContext> {
  return submitReviewDecision(
    investigationId,
    "request-more-investigation",
    request,
  );
}
