import api from "../api/api";
import type { OperationsCenterResponse } from "../types/operationsCenter";

export async function getOperationsCenter(
  projectId?: string,
): Promise<OperationsCenterResponse> {
  const response = await api.get<OperationsCenterResponse>(
    "/operations/center",
    {
      params: projectId ? { projectId } : undefined,
    },
  );
  return response.data;
}
