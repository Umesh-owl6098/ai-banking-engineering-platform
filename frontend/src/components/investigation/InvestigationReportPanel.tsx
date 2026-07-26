import { Alert, Box, Card, CardContent, Chip, Divider, Stack, Typography } from "@mui/material";

import type { InvestigationReport } from "../../types/investigation";

function ReportSection({
  title,
  narrative,
}: {
  title: string;
  narrative: string;
}) {
  return (
    <Box>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }} gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>
        {narrative}
      </Typography>
    </Box>
  );
}

export default function InvestigationReportPanel({
  report,
}: {
  report: InvestigationReport | null;
}) {
  if (!report) {
    return (
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Investigation Report
          </Typography>
          <Typography color="text.secondary">
            No investigation report is available yet.
          </Typography>
        </CardContent>
      </Card>
    );
  }

  const isLlm = report.metadata.generationMode === "LLM";

  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={1}
            sx={{ alignItems: { sm: "center" }, justifyContent: "space-between" }}
          >
            <Typography variant="h6">Investigation Report</Typography>
            <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
              <Chip
                label={
                  isLlm
                    ? "OpenAI Generated"
                    : "Deterministic Fallback"
                }
                color={isLlm ? "primary" : "default"}
                variant={isLlm ? "filled" : "outlined"}
              />
              <Chip
                label={`Prompt ${report.metadata.promptVersion}`}
                size="small"
                variant="outlined"
              />
              <Chip
                label={report.metadata.modelName || "No model"}
                size="small"
                variant="outlined"
              />
            </Stack>
          </Stack>

          {!isLlm && (
            <Alert severity="info">
              This report was generated using the deterministic fallback because
              OpenAI was unavailable or not configured.
            </Alert>
          )}

          <ReportSection
            title="Executive Summary"
            narrative={report.executiveSummary}
          />
          <Divider />
          <ReportSection
            title="Fraud Analysis"
            narrative={report.fraudAnalysis.narrative}
          />
          <ReportSection
            title="KYC Analysis"
            narrative={report.kycAnalysis.narrative}
          />
          <ReportSection
            title="AML Analysis"
            narrative={report.amlAnalysis.narrative}
          />
          <ReportSection
            title="Compliance Analysis"
            narrative={report.complianceAssessment.narrative}
          />
          <Divider />
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            Supporting Evidence
          </Typography>
          {report.supportingEvidence.length === 0 ? (
            <Typography color="text.secondary">
              No supporting documentation was retrieved.
            </Typography>
          ) : (
            report.supportingEvidence.map((item) => (
              <ReportSection
                key={`${item.title}-${item.narrative.slice(0, 20)}`}
                title={item.title}
                narrative={item.narrative}
              />
            ))
          )}
          <Divider />
          <ReportSection
            title="Final Recommendation"
            narrative={report.analystRecommendation}
          />
        </Stack>
      </CardContent>
    </Card>
  );
}
