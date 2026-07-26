import api from "../api/api";
import type { OperationsDashboardResponse } from "../types/dashboard";

export async function getOperationsDashboard(
  projectId?: string,
): Promise<OperationsDashboardResponse> {
  const response = await api.get<OperationsDashboardResponse>(
    "/dashboard/operations",
    {
      params: projectId ? { projectId } : undefined,
    },
  );
  return response.data;
}
