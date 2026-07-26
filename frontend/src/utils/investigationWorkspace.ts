import type {
  AgentFinding,
  ParsedStructuredFinding,
} from "../types/investigation";

const AGENT_TYPES = ["FRAUD", "KYC", "AML", "COMPLIANCE"] as const;

export type WorkspaceAgentType = (typeof AGENT_TYPES)[number];

export function parseStructuredFinding(
  structuredJson: string | Record<string, unknown> | null,
): ParsedStructuredFinding {
  if (!structuredJson) {
    return {};
  }

  if (typeof structuredJson === "object") {
    return structuredJson as ParsedStructuredFinding;
  }

  try {
    return JSON.parse(structuredJson) as ParsedStructuredFinding;
  } catch {
    return {};
  }
}

export function latestFindingByAgent(
  findings: AgentFinding[],
  agentType: WorkspaceAgentType,
): AgentFinding | null {
  return (
    findings
      .filter((finding) => finding.agentType === agentType)
      .filter(
        (finding) =>
          finding.status === "COMPLETE"
          || finding.status === "COMPLETED"
          || finding.completedAt != null,
      )
      .sort(
        (left, right) =>
          new Date(right.createdAt).getTime()
          - new Date(left.createdAt).getTime(),
      )[0] ?? null
  );
}

export function scoreForFinding(
  agentType: WorkspaceAgentType,
  structured: ParsedStructuredFinding,
): number | null {
  switch (agentType) {
    case "FRAUD":
      return structured.fraudScore ?? null;
    case "KYC":
      return structured.kycScore ?? null;
    case "AML":
      return structured.amlScore ?? null;
    case "COMPLIANCE":
      return structured.overallScore ?? null;
    default:
      return null;
  }
}

export function indicatorsForFinding(
  agentType: WorkspaceAgentType,
  structured: ParsedStructuredFinding,
) {
  if (agentType === "COMPLIANCE") {
    return structured.contributingFindings ?? [];
  }

  return structured.triggeredIndicators ?? [];
}

export { AGENT_TYPES };
