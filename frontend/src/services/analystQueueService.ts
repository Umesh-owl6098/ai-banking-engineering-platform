import api from "../api/api";
import type {
  AnalystQueueResponse,
  AssignableAnalyst,
  AssignInvestigationRequest,
} from "../types/analystQueue";
import type { InvestigationCase } from "../types/investigation";

export async function getAnalystQueue(
  projectId?: string,
): Promise<AnalystQueueResponse> {
  const response = await api.get<AnalystQueueResponse>("/analyst-queue", {
    params: projectId ? { projectId } : undefined,
  });
  return response.data;
}

export async function getAssignableAnalysts(): Promise<AssignableAnalyst[]> {
  const response = await api.get<AssignableAnalyst[]>("/analysts");
  return response.data;
}

export async function assignInvestigation(
  investigationId: string,
  request: AssignInvestigationRequest,
): Promise<InvestigationCase> {
  const response = await api.post<InvestigationCase>(
    `/investigations/${investigationId}/assign`,
    request,
  );
  return response.data;
}

export async function unassignInvestigation(
  investigationId: string,
): Promise<InvestigationCase> {
  const response = await api.post<InvestigationCase>(
    `/investigations/${investigationId}/unassign`,
  );
  return response.data;
}

export async function claimInvestigation(
  investigationId: string,
): Promise<InvestigationCase> {
  const response = await api.post<InvestigationCase>(
    `/investigations/${investigationId}/claim`,
  );
  return response.data;
}
