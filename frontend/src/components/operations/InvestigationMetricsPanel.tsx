import { Grid, Typography } from "@mui/material";

import SurfaceCard from "../ui/SurfaceCard";
import type { InvestigationMetricsSummary } from "../../types/operationsCenter";

function MetricTile({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: "default" | "warning" | "error";
}) {
  const color =
    tone === "error"
      ? "error.main"
      : tone === "warning"
        ? "warning.dark"
        : "text.primary";

  return (
    <Grid size={{ xs: 6, md: 4 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
        {label}
      </Typography>
      <Typography variant="h6" sx={{ fontWeight: 700, color }}>
        {value.toLocaleString()}
      </Typography>
    </Grid>
  );
}

export default function InvestigationMetricsPanel({
  metrics,
}: {
  metrics: InvestigationMetricsSummary;
}) {
  return (
    <SurfaceCard title="Investigation Metrics">
      <Grid container spacing={2}>
        <MetricTile
          label="Transactions processed today"
          value={metrics.transactionsProcessedToday}
        />
        <MetricTile
          label="Investigations created today"
          value={metrics.investigationsCreatedToday}
        />
        <MetricTile
          label="Critical investigations"
          value={metrics.criticalInvestigations}
          tone="error"
        />
        <MetricTile
          label="Awaiting analyst review"
          value={metrics.awaitingAnalystReview}
          tone="warning"
        />
        <MetricTile
          label="Closed investigations"
          value={metrics.closedInvestigations}
        />
        <MetricTile
          label="Failed investigations"
          value={metrics.failedInvestigations}
          tone="error"
        />
      </Grid>
    </SurfaceCard>
  );
}
