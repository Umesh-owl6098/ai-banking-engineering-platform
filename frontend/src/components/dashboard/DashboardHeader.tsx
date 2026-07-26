import { Stack } from "@mui/material";

import ConnectionStatusChip from "../ui/ConnectionStatusChip";
import PageHeader from "../ui/PageHeader";

export default function DashboardHeader({
  simulationRunning,
  investigationStreamState,
  transactionStreamState,
  lastUpdatedAt,
}: {
  simulationRunning: boolean;
  investigationStreamState: "connecting" | "connected" | "disconnected";
  transactionStreamState: "connecting" | "connected" | "disconnected";
  lastUpdatedAt: string | null;
}) {
  const streamState =
    investigationStreamState === "connected"
    && transactionStreamState === "connected"
      ? "connected"
      : investigationStreamState === "connecting"
        || transactionStreamState === "connecting"
        ? "connecting"
        : "disconnected";

  return (
    <PageHeader
      title="Operations Dashboard"
      description="Real-time screening, AI investigations, and analyst workload."
      meta={
        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
          <ConnectionStatusChip
            label={simulationRunning ? "Simulation live" : "System idle"}
            state={simulationRunning ? "connected" : "disconnected"}
          />
          <ConnectionStatusChip label="Live updates" state={streamState} />
          {lastUpdatedAt && (
            <ConnectionStatusChip
              label={`Updated ${new Date(lastUpdatedAt).toLocaleTimeString()}`}
              state="connected"
            />
          )}
        </Stack>
      }
    />
  );
}
