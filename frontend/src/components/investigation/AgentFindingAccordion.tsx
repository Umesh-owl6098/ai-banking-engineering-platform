import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";

import type {
  AgentFinding,
  ExplainabilityResponse,
} from "../../types/investigation";
import {
  indicatorsForFinding,
  parseStructuredFinding,
  scoreForFinding,
  type WorkspaceAgentType,
} from "../../utils/investigationWorkspace";
import SummaryField from "./SummaryField";

function formatPercent(value: number | null | undefined): string {
  if (value == null) {
    return "—";
  }

  return `${Math.round(value * 100)}%`;
}

function riskColor(
  riskLevel: string | null | undefined,
): "default" | "success" | "warning" | "error" {
  switch (riskLevel) {
    case "LOW":
      return "success";
    case "MEDIUM":
      return "warning";
    case "HIGH":
    case "CRITICAL":
      return "error";
    default:
      return "default";
  }
}

export default function AgentFindingAccordion({
  agentType,
  finding,
  explainability,
}: {
  agentType: WorkspaceAgentType;
  finding: AgentFinding | null;
  explainability?: ExplainabilityResponse | null;
}) {
  const structured = parseStructuredFinding(finding?.structuredJson ?? null);
  const score = scoreForFinding(agentType, structured);
  const indicators = indicatorsForFinding(agentType, structured);

  return (
    <Accordion defaultExpanded={Boolean(finding)} disableGutters>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={1}
          sx={{ alignItems: { sm: "center" }, width: "100%", pr: 1 }}
        >
          <Typography sx={{ flexGrow: 1, fontWeight: 600 }}>
            {agentType}
          </Typography>
          {finding ? (
            <>
              <Chip
                size="small"
                label={finding.riskLevel ?? "Unknown risk"}
                color={riskColor(finding.riskLevel)}
              />
              {score != null && (
                <Chip size="small" variant="outlined" label={`Score ${score}`} />
              )}
            </>
          ) : (
            <Chip size="small" label="Not completed" variant="outlined" />
          )}
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        {!finding ? (
          <Typography color="text.secondary">
            No completed {agentType} finding is available for this investigation yet.
          </Typography>
        ) : (
          <Stack spacing={2}>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 4 }}>
                <SummaryField label="Score" value={score ?? "—"} />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <SummaryField
                  label="Recommendation"
                  value={structured.recommendation ?? "—"}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <SummaryField
                  label="Confidence"
                  value={formatPercent(finding.confidence)}
                />
              </Grid>
            </Grid>

            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Summary
              </Typography>
              <Typography color="text.secondary">
                {finding.summary ?? "No summary provided."}
              </Typography>
            </Box>

            {explainability && explainability.triggeredRules.length > 0 && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Rule Contributions
                </Typography>
                <List dense disablePadding>
                  {explainability.triggeredRules.map((rule) => (
                    <ListItem
                      key={rule.ruleCode}
                      disableGutters
                      sx={{ alignItems: "flex-start" }}
                    >
                      <ListItemText
                        primary={`${rule.displayName} (+${rule.scoreContribution})`}
                        secondary={rule.explanation}
                      />
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}

            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Triggered Indicators
              </Typography>
              {indicators.length > 0 ? (
                <List dense disablePadding>
                  {indicators.map((indicator, index) => (
                    <ListItem
                      key={`${indicator.type ?? "indicator"}-${index}`}
                      disableGutters
                      sx={{ alignItems: "flex-start" }}
                    >
                      <ListItemText
                        primary={indicator.type ?? "Indicator"}
                        secondary={
                          indicator.explanation ??
                          (indicator.scoreContribution != null
                            ? `Score contribution: ${indicator.scoreContribution}`
                            : undefined)
                        }
                      />
                    </ListItem>
                  ))}
                </List>
              ) : (
                <Typography color="text.secondary">
                  No indicators were recorded.
                </Typography>
              )}
            </Box>

            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Evidence
              </Typography>
              {finding.citations.length > 0 ? (
                <Stack spacing={1}>
                  {finding.citations.map((citation) => (
                    <Card key={citation.id} variant="outlined">
                      <CardContent sx={{ py: 1.5, "&:last-child": { pb: 1.5 } }}>
                        <Stack
                          direction="row"
                          spacing={1}
                          sx={{ alignItems: "center", mb: 1 }}
                        >
                          <DescriptionOutlinedIcon fontSize="small" />
                          <Typography sx={{ flexGrow: 1 }}>
                            {citation.fileName} · chunk {citation.chunkIndex}
                          </Typography>
                          {citation.similarity != null && (
                            <Chip
                              size="small"
                              label={`${Math.round(citation.similarity * 100)}% match`}
                            />
                          )}
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {citation.contentPreview ?? "No preview available."}
                        </Typography>
                      </CardContent>
                    </Card>
                  ))}
                </Stack>
              ) : (
                <Typography color="text.secondary">
                  No policy evidence citations were attached.
                </Typography>
              )}
            </Box>
          </Stack>
        )}
      </AccordionDetails>
    </Accordion>
  );
}
