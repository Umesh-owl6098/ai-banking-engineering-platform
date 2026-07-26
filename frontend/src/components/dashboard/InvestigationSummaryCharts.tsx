import { Card, CardContent, Grid, Typography } from "@mui/material";

import type { DistributionEntry } from "../../types/dashboard";
import DistributionChart from "./DistributionChart";

export default function InvestigationSummaryCharts({
  investigationsByStatus,
  investigationsBySeverity,
  screeningResults,
  triggeredRuleFrequency,
}: {
  investigationsByStatus: DistributionEntry[];
  investigationsBySeverity: DistributionEntry[];
  screeningResults: DistributionEntry[];
  triggeredRuleFrequency: DistributionEntry[];
}) {
  return (
    <Card>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
          Investigation Distribution
        </Typography>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <DistributionChart
              title="By Status"
              data={investigationsByStatus}
              color="#2563eb"
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <DistributionChart
              title="By Severity"
              data={investigationsBySeverity}
              color="#d97706"
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <DistributionChart
              title="Screening Results (Today)"
              data={screeningResults}
              color="#dc2626"
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <DistributionChart
              title="Triggered Rule Frequency"
              data={triggeredRuleFrequency}
              color="#7c3aed"
            />
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
}
