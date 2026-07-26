import {
  Box,
  Chip,
  Grid,
  Stack,
  Typography,
} from "@mui/material";
import CheckCircleOutlineOutlinedIcon from "@mui/icons-material/CheckCircleOutlineOutlined";
import ErrorOutlineOutlinedIcon from "@mui/icons-material/ErrorOutlineOutlined";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";

import SurfaceCard from "../ui/SurfaceCard";
import type { PlatformHealthSummary } from "../../types/operationsCenter";

function healthColor(
  status: string,
): "success" | "warning" | "error" | "default" {
  if (status === "UP") {
    return "success";
  }
  if (status === "DEGRADED") {
    return "warning";
  }
  if (status === "DOWN") {
    return "error";
  }
  return "default";
}

function healthIcon(status: string) {
  if (status === "UP") {
    return <CheckCircleOutlineOutlinedIcon fontSize="small" />;
  }
  if (status === "DEGRADED") {
    return <WarningAmberOutlinedIcon fontSize="small" />;
  }
  return <ErrorOutlineOutlinedIcon fontSize="small" />;
}

export default function PlatformHealthPanel({
  health,
  investigationStreamState,
  transactionStreamState,
}: {
  health: PlatformHealthSummary;
  investigationStreamState: "connecting" | "connected" | "disconnected";
  transactionStreamState: "connecting" | "connected" | "disconnected";
}) {
  const liveStreamConnected =
    investigationStreamState === "connected"
    && transactionStreamState === "connected";
  const liveStreamStatus = liveStreamConnected
    ? "UP"
    : investigationStreamState === "connecting"
      || transactionStreamState === "connecting"
      ? "DEGRADED"
      : "DOWN";
  const liveStreamMessage = liveStreamConnected
    ? "Browser connected to investigation and simulation streams"
    : investigationStreamState === "connecting"
      || transactionStreamState === "connecting"
      ? "Reconnecting to live streams"
      : "Live stream disconnected";

  const components = health.components.map((component) =>
    component.component === "SSE"
      ? {
          ...component,
          status: liveStreamStatus,
          message: liveStreamMessage,
        }
      : component,
  );

  const overallStatus = components.some((item) => item.status === "DOWN")
    ? "DOWN"
    : components.some((item) => item.status === "DEGRADED")
      ? "DEGRADED"
      : health.overallStatus;

  return (
    <SurfaceCard title="Platform Health">
      <Stack spacing={1.5}>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
          <Typography variant="body2" color="text.secondary">
            Overall
          </Typography>
          <Chip
            size="small"
            color={healthColor(overallStatus)}
            label={overallStatus}
            icon={healthIcon(overallStatus)}
          />
        </Stack>

        <Grid container spacing={1.5}>
          {components.map((component) => (
            <Grid key={component.component} size={{ xs: 12, sm: 6, md: 4 }}>
              <Box
                sx={{
                  p: 1.5,
                  border: 1,
                  borderColor: "divider",
                  borderRadius: 1.5,
                  height: "100%",
                }}
              >
                <Stack spacing={0.75}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                    <Typography variant="subtitle2">
                      {component.component}
                    </Typography>
                    <Chip
                      size="small"
                      color={healthColor(component.status)}
                      label={component.status}
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {component.message}
                  </Typography>
                </Stack>
              </Box>
            </Grid>
          ))}
        </Grid>
      </Stack>
    </SurfaceCard>
  );
}
