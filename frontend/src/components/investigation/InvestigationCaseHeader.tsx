import { Card, CardContent, Chip, Grid, Stack, Typography } from "@mui/material";

import type { InvestigationCase } from "../../types/investigation";
import StatusChip from "../ui/StatusChip";
import TruncatedText from "../ui/TruncatedText";
import SummaryField from "./SummaryField";

function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }

  return new Date(value).toLocaleString();
}

function formatRules(rules: string[] | null | undefined): string {
  if (!rules || rules.length === 0) {
    return "—";
  }

  return rules.map((rule) => rule.replaceAll("_", " ")).join(", ");
}

export default function InvestigationCaseHeader({
  investigation,
  totalExecutionDuration,
}: {
  investigation: InvestigationCase;
  totalExecutionDuration: string | null;
}) {
  return (
    <Card variant="outlined" sx={{ borderColor: "divider" }}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Stack spacing={2}>
          <Stack spacing={1}>
            <Typography variant="h5" component="h1">
              Investigation Command Center
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {investigation.title}
            </Typography>
            <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
              <StatusChip kind="investigation" value={investigation.status} />
              <StatusChip kind="severity" value={investigation.priority} />
              {investigation.autoCreated && (
                <Chip label="Auto-created" color="info" size="small" variant="outlined" />
              )}
              {investigation.screeningStatus && (
                <StatusChip
                  kind="screening"
                  value={investigation.screeningStatus}
                  variant="outlined"
                />
              )}
            </Stack>
          </Stack>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 4 }}>
              <SummaryField
                label="Investigation Reference"
                value={
                  <TruncatedText
                    value={investigation.id}
                    maxWidth="100%"
                    monospace
                  />
                }
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <SummaryField label="Created" value={formatDate(investigation.createdAt)} />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <SummaryField
                label="Total Execution Duration"
                value={totalExecutionDuration ?? "—"}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <SummaryField
                label="Screening Reason"
                value={investigation.screeningReason}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <SummaryField
                label="Triggered Screening Rules"
                value={formatRules(investigation.screeningTriggeredRules)}
              />
            </Grid>
          </Grid>
        </Stack>
      </CardContent>
    </Card>
  );
}
