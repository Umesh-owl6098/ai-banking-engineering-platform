import { describe, expect, it } from "vitest";

import {
  KNOWLEDGE_CHAT_AGENT_NAME,
  resolveKnowledgeChatAgentId,
} from "./agentService";
import type { AiAgent } from "../types/agent";

describe("resolveKnowledgeChatAgentId", () => {
  const agents: AiAgent[] = [
    {
      id: "b8f3a2c1-4d5e-6f70-8192-a3b4c5d6e7f8",
      name: KNOWLEDGE_CHAT_AGENT_NAME,
      model: "gpt-4.1-mini",
      active: true,
    },
    {
      id: "other-agent-id",
      name: "Investigation Agent",
      model: "gpt-4.1-mini",
      active: true,
    },
  ];

  it("prefers the named knowledge chat agent", () => {
    expect(resolveKnowledgeChatAgentId(agents)).toBe(
      "b8f3a2c1-4d5e-6f70-8192-a3b4c5d6e7f8",
    );
  });

  it("returns null when no active agents exist", () => {
    expect(
      resolveKnowledgeChatAgentId(
        agents.map((agent) => ({ ...agent, active: false })),
      ),
    ).toBeNull();
  });
});
