import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useNavigate } from "react-router-dom";

import type { CriticalAlertGroup } from "../../types/dashboard";
import { formatCurrency, severityChipColor } from "../../utils/statusBadges";

export default function LiveAlertsFeed({
  alerts,
  streamConnected,
}: {
  alerts: CriticalAlertGroup[];
  streamConnected: boolean;
}) {
  const navigate = useNavigate();

  return (
    <Card sx={{ height: "100%" }}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 1.5 }}>
          <Typography variant="subtitle1" sx={{ flexGrow: 1, fontWeight: 700 }}>
            Live Critical Alerts
          </Typography>
          <Chip
            size="small"
            label={streamConnected ? "Live" : "Reconnecting"}
            color={streamConnected ? "success" : "warning"}
            variant="outlined"
          />
        </Stack>

        <Stack spacing={1.25} sx={{ maxHeight: 420, overflow: "auto" }}>
          {alerts.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              No suspicious or critical alerts yet. Alerts appear automatically
              when screening flags activity.
            </Typography>
          ) : (
            alerts.map((alert) => (
              <Box
                key={alert.groupKey}
                sx={{
                  p: 1.25,
                  borderRadius: 1.5,
                  border: "1px solid",
                  borderColor: "divider",
                  bgcolor:
                    alert.severity === "CRITICAL" ? "error.50" : "warning.50",
                }}
              >
                <Stack spacing={0.75}>
                  <Stack
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: "center", flexWrap: "wrap" }}
                  >
                    <Chip
                      label={alert.severity}
                      size="small"
                      color={severityChipColor(alert.severity)}
                    />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {alert.customerName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {new Date(alert.detectedAt).toLocaleTimeString()}
                    </Typography>
                  </Stack>
                  <Typography variant="body2">
                    {alert.scenarioLabel.replaceAll("_", " ")} ·{" "}
                    {alert.triggeredRules.length > 0
                      ? alert.triggeredRules.join(", ")
                      : alert.screeningReason}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {formatCurrency(alert.totalAmount, alert.currency)} ·{" "}
                    {alert.relatedTransactionCount} related transaction
                    {alert.relatedTransactionCount === 1 ? "" : "s"}
                    {alert.investigationStatus
                      ? ` · Investigation ${alert.investigationStatus}`
                      : ""}
                  </Typography>
                  <Stack direction="row" sx={{ justifyContent: "flex-end" }}>
                    <Button
                      size="small"
                      variant="contained"
                      endIcon={<OpenInNewIcon />}
                      disabled={!alert.investigationId}
                      onClick={() => {
                        if (alert.investigationId) {
                          navigate(
                            `/investigations/${alert.investigationId}`,
                          );
                        }
                      }}
                    >
                      Open Investigation
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
