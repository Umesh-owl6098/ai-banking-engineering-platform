import api from "../api/api";
import type { AiAgent } from "../types/agent";

export const KNOWLEDGE_CHAT_AGENT_NAME = "Knowledge Chat Assistant";

export async function getProjectAgents(
  projectId: string,
): Promise<AiAgent[]> {
  const response = await api.get<AiAgent[]>(
    `/projects/${projectId}/agents`,
  );

  return response.data;
}

export function resolveKnowledgeChatAgentId(
  agents: AiAgent[],
): string | null {
  const configuredAgentId = import.meta.env.VITE_AGENT_ID;

  if (configuredAgentId) {
    const configuredAgent = agents.find(
      (agent) => agent.id === configuredAgentId,
    );

    if (configuredAgent?.active) {
      return configuredAgent.id;
    }
  }

  const namedAgent = agents.find(
    (agent) =>
      agent.active && agent.name === KNOWLEDGE_CHAT_AGENT_NAME,
  );

  if (namedAgent) {
    return namedAgent.id;
  }

  const firstActiveAgent = agents.find((agent) => agent.active);

  return firstActiveAgent?.id ?? null;
}
