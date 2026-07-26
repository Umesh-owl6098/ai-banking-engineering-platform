import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Card,
  CardContent,
  Chip,
  Divider,
  Stack,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";

import type { ExplainabilityResponse } from "../../types/investigation";

function formatPercent(value: number | null | undefined): string {
  if (value == null) {
    return "—";
  }

  return `${Math.round(value * 100)}%`;
}

function KeyValueList({
  values,
}: {
  values: Record<string, unknown>;
}) {
  const entries = Object.entries(values);

  if (entries.length === 0) {
    return (
      <Typography color="text.secondary">
        No related fields available.
      </Typography>
    );
  }

  return (
    <Stack spacing={0.5}>
      {entries.map(([key, value]) => (
        <Typography key={key} variant="body2">
          <strong>{key}:</strong> {String(value)}
        </Typography>
      ))}
    </Stack>
  );
}

export default function FindingExplainabilityCard({
  response,
}: {
  response: ExplainabilityResponse;
}) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" gutterBottom>
              {response.agentType}
            </Typography>
            <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
              <Chip label={`Total Score: ${response.totalScore}`} />
              <Chip label={`Risk: ${response.riskLevel}`} color="warning" />
              <Chip
                label={`Recommendation: ${response.recommendation}`}
                color="error"
                variant="outlined"
              />
              <Chip
                label={`Confidence: ${formatPercent(response.confidence)}`}
                variant="outlined"
              />
            </Stack>
          </Box>

          <Divider />

          <Stack spacing={1}>
            {response.triggeredRules.map((rule) => (
              <Accordion key={rule.ruleCode} disableGutters>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography sx={{ fontWeight: 600 }}>
                    {rule.displayName} (+{rule.scoreContribution})
                  </Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <Stack spacing={1.5}>
                    <Typography>{rule.explanation}</Typography>
                    <Box>
                      <Typography variant="subtitle2">
                        Rule Description
                      </Typography>
                      <Typography color="text.secondary">
                        {rule.description}
                      </Typography>
                    </Box>
                    <Box>
                      <Typography variant="subtitle2">
                        Actual Value
                      </Typography>
                      <KeyValueList values={rule.evidenceValues} />
                    </Box>
                    <Box>
                      <Typography variant="subtitle2">Threshold</Typography>
                      <KeyValueList values={rule.thresholds} />
                    </Box>
                    <Box>
                      <Typography variant="subtitle2">
                        Related Fields
                      </Typography>
                      <KeyValueList values={rule.relatedFields} />
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Confidence contribution:{" "}
                      {formatPercent(rule.confidenceContribution)}
                    </Typography>
                    {rule.supportingEvidence.length > 0 && (
                      <Box>
                        <Typography variant="subtitle2">
                          Citations
                        </Typography>
                        <Stack spacing={1} sx={{ mt: 1 }}>
                          {rule.supportingEvidence.map((evidence) => (
                            <Box key={evidence.citationId}>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {evidence.documentName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {evidence.excerpt ?? "No excerpt available."}
                              </Typography>
                            </Box>
                          ))}
                        </Stack>
                      </Box>
                    )}
                  </Stack>
                </AccordionDetails>
              </Accordion>
            ))}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}
