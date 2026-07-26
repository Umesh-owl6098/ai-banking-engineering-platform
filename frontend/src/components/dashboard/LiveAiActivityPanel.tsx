import {
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import {
  ACTIVITY_STAGE_KEYS,
  formatDurationMs,
  type AiActivityRow,
} from "../../utils/dashboardMetrics";
import { stageStatusLabel } from "../../utils/executionTimeline";

function stageChipColor(
  status: AiActivityRow["stages"][string]["stageStatus"],
): "default" | "info" | "success" | "error" {
  switch (status) {
    case "RUNNING":
      return "info";
    case "COMPLETED":
      return "success";
    case "FAILED":
      return "error";
    default:
      return "default";
  }
}

const STAGE_LABELS: Record<string, string> = {
  SUPERVISOR: "Supervisor",
  FRAUD: "Fraud",
  KYC: "KYC",
  AML: "AML",
  COMPLIANCE: "Compliance",
};

export default function LiveAiActivityPanel({
  rows,
  streamConnected,
}: {
  rows: AiActivityRow[];
  streamConnected: boolean;
}) {
  const activeRows = rows
    .filter(
      (row) =>
        row.caseStatus === "RUNNING"
        || row.caseStatus === "NEW"
        || row.caseStatus === "REPORT_GENERATED"
        || Object.values(row.stages).some(
          (stage) => stage.stageStatus === "RUNNING",
        ),
    )
    .slice(0, 8);

  return (
    <Card sx={{ height: "100%" }}>
      <CardContent>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", mb: 2 }}
        >
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Live AI Activity
          </Typography>
          <Chip
            size="small"
            label={streamConnected ? "Live" : "Reconnecting"}
            color={streamConnected ? "success" : "warning"}
            variant="outlined"
          />
        </Stack>

        {activeRows.length === 0 ? (
          <Typography color="text.secondary">
            No investigations are currently executing. Activity appears here
            when auto-investigations run through the AI pipeline.
          </Typography>
        ) : (
          <TableContainer sx={{ maxHeight: 420 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>Investigation</TableCell>
                  {ACTIVITY_STAGE_KEYS.map((key) => (
                    <TableCell key={key} align="center">
                      {STAGE_LABELS[key]}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {activeRows.map((row) => (
                  <TableRow key={row.investigationId} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {row.reference}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {row.customerName}
                      </Typography>
                    </TableCell>
                    {ACTIVITY_STAGE_KEYS.map((key) => {
                      const stage = row.stages[key];
                      return (
                        <TableCell key={key} align="center">
                          <Stack spacing={0.5} sx={{ alignItems: "center" }}>
                            <Chip
                              size="small"
                              label={stageStatusLabel(stage.stageStatus)}
                              color={stageChipColor(stage.stageStatus)}
                              variant="outlined"
                            />
                            <Typography variant="caption" color="text.secondary">
                              {formatDurationMs(stage.durationMs)}
                            </Typography>
                          </Stack>
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
}
