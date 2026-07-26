import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  LinearProgress,
  Stack,
  Typography,
} from "@mui/material";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useNavigate } from "react-router-dom";

import type { ActiveInvestigationRow } from "../../types/dashboard";
import {
  formatDurationMs,
  investigationStatusChipColor,
  severityChipColor,
} from "../../utils/statusBadges";

export default function ActiveInvestigationsPanel({
  rows,
  streamConnected,
}: {
  rows: ActiveInvestigationRow[];
  streamConnected: boolean;
}) {
  const navigate = useNavigate();

  return (
    <Card sx={{ height: "100%" }}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 1.5 }}>
          <Typography variant="subtitle1" sx={{ flexGrow: 1, fontWeight: 700 }}>
            Active AI Investigations
          </Typography>
          <Chip
            size="small"
            label={streamConnected ? "Live" : "Reconnecting"}
            color={streamConnected ? "success" : "warning"}
            variant="outlined"
          />
        </Stack>

        <Stack spacing={1.25} sx={{ maxHeight: 360, overflow: "auto" }}>
          {rows.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              No investigations are currently running.
            </Typography>
          ) : (
            rows.map((row) => (
              <Box
                key={row.investigationId}
                sx={{
                  p: 1.25,
                  borderRadius: 1.5,
                  border: "1px solid",
                  borderColor: "divider",
                }}
              >
                <Stack spacing={1}>
                  <Stack
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: "center", flexWrap: "wrap" }}
                  >
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {row.reference}
                    </Typography>
                    <Chip
                      size="small"
                      label={row.severity}
                      color={severityChipColor(row.severity)}
                    />
                    <Chip
                      size="small"
                      label={row.status}
                      color={investigationStatusChipColor(row.status)}
                      variant="outlined"
                    />
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    {row.customerName} · Stage {row.pipelineStage} ·{" "}
                    {formatDurationMs(row.elapsedDurationMs)} elapsed
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={row.progressPercent}
                    sx={{ height: 6, borderRadius: 999 }}
                  />
                  <Stack direction="row" sx={{ justifyContent: "flex-end" }}>
                    <Button
                      size="small"
                      endIcon={<OpenInNewIcon />}
                      onClick={() =>
                        navigate(`/investigations/${row.investigationId}`)
                      }
                    >
                      Open Command Center
                    </Button>
                  </Stack>
                </Stack>
              </Box>
            ))
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
