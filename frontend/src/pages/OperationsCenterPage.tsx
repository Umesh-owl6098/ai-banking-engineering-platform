import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Grid, Stack } from "@mui/material";

import AgentPerformancePanel from "../components/operations/AgentPerformancePanel";
import ErrorMonitoringPanel from "../components/operations/ErrorMonitoringPanel";
import InvestigationMetricsPanel from "../components/operations/InvestigationMetricsPanel";
import LiveActivityFeedPanel from "../components/operations/LiveActivityFeedPanel";
import PlatformHealthPanel from "../components/operations/PlatformHealthPanel";
import ConnectionStatusChip from "../components/ui/ConnectionStatusChip";
import ErrorState from "../components/ui/ErrorState";
import { LoadingSpinner } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import PageHeader from "../components/ui/PageHeader";
import { useAuth } from "../hooks/useAuth";
import { useInvestigationLiveStream } from "../hooks/useInvestigationLiveStream";
import { useSimulationLiveStream } from "../hooks/useSimulationLiveStream";
import { getOperationsCenter } from "../services/operationsCenterService";
import { INVESTIGATION_PROJECT_ID } from "../services/investigationService";
import type { ActivityFeedEntry, OperationsCenterResponse } from "../types/operationsCenter";
import {
  activityFromInvestigationCreated,
  activityFromInvestigationExecution,
  activityFromSseReconnect,
  activityFromTransaction,
  prependActivityFeedEntry,
} from "../utils/operationsCenterFeed";
import { layout } from "../theme/tokens";

export default function OperationsCenterPage() {
  const { isLoading: isAuthLoading } = useAuth();
  const [center, setCenter] = useState<OperationsCenterResponse | null>(null);
  const [activityFeed, setActivityFeed] = useState<ActivityFeedEntry[]>([]);
  const [sseReconnectCount, setSseReconnectCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [connectionWarning, setConnectionWarning] = useState<string | null>(null);

  const loadCenter = useCallback(async () => {
    try {
      setLoadError(null);
      const response = await getOperationsCenter(INVESTIGATION_PROJECT_ID);
      setCenter(response);
    } catch (caughtError) {
      setLoadError(caughtError);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isAuthLoading) {
      return;
    }

    void loadCenter();
  }, [isAuthLoading, loadCenter]);

  const handleReconnect = useCallback(
    (stream: string) => {
      setSseReconnectCount((current) => current + 1);
      setActivityFeed((current) =>
        prependActivityFeedEntry(current, activityFromSseReconnect(stream)),
      );
      void loadCenter();
    },
    [loadCenter],
  );

  const { streamState: investigationStreamState } = useInvestigationLiveStream({
    enabled: !isAuthLoading,
    onCreated: (notification) => {
      setActivityFeed((current) =>
        prependActivityFeedEntry(
          current,
          activityFromInvestigationCreated(notification),
        ),
      );
      void loadCenter();
    },
    onExecution: (event) => {
      setActivityFeed((current) =>
        prependActivityFeedEntry(
          current,
          activityFromInvestigationExecution(event),
        ),
      );

      if (
        event.eventType === "EXECUTION_FAILED"
        || event.eventType === "REPORT_GENERATED"
        || event.eventType === "INVESTIGATION_READY_FOR_REVIEW"
      ) {
        void loadCenter();
      }
    },
    onReconnect: () => handleReconnect("Investigation"),
  });

  const { streamState: transactionStreamState } = useSimulationLiveStream({
    enabled: !isAuthLoading,
    onEvent: (event) => {
      setActivityFeed((current) =>
        prependActivityFeedEntry(current, activityFromTransaction(event)),
      );
      void loadCenter();
    },
    onReconnect: () => handleReconnect("Simulation"),
  });

  useEffect(() => {
    const disconnected =
      investigationStreamState === "disconnected"
      || transactionStreamState === "disconnected";
    const connecting =
      investigationStreamState === "connecting"
      || transactionStreamState === "connecting";

    if (disconnected) {
      setConnectionWarning(
        "Live stream disconnected. Activity feed may be stale until reconnection.",
      );
      return;
    }

    if (connecting) {
      setConnectionWarning("Reconnecting to live streams…");
      return;
    }

    setConnectionWarning(null);
  }, [investigationStreamState, transactionStreamState]);

  if (isLoading) {
    return (
      <PageContainer>
        <LoadingSpinner label="Loading operations center…" />
      </PageContainer>
    );
  }

  if (loadError != null && !center) {
    return (
      <PageContainer>
        <ErrorState
          error={loadError}
          fallback="Unable to load operations center."
          onRetry={() => {
            setIsLoading(true);
            void loadCenter();
          }}
        />
      </PageContainer>
    );
  }

  if (!center) {
    return null;
  }

  const streamsConnected =
    investigationStreamState === "connected"
    && transactionStreamState === "connected";

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
        <PageHeader
          title="Operations Center"
          description="Real-time platform health, investigation metrics, agent performance, and live activity."
          meta={
            <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
              <ConnectionStatusChip
                label={
                  streamsConnected
                    ? "Live streams connected"
                    : investigationStreamState === "connecting"
                      || transactionStreamState === "connecting"
                      ? "Live streams reconnecting"
                      : "Live streams disconnected"
                }
                state={
                  streamsConnected
                    ? "connected"
                    : investigationStreamState === "connecting"
                      || transactionStreamState === "connecting"
                      ? "connecting"
                      : "disconnected"
                }
              />
              <ConnectionStatusChip
                label={`Updated ${new Date(center.generatedAt).toLocaleTimeString()}`}
                state="connected"
              />
            </Stack>
          }
          actions={
            <Button variant="outlined" size="small" onClick={() => void loadCenter()}>
              Refresh
            </Button>
          }
        />

        {loadError != null && (
          <ErrorState
            error={loadError}
            fallback="Unable to refresh operations center."
            onRetry={() => void loadCenter()}
          />
        )}

        {connectionWarning && (
          <Alert severity="warning">{connectionWarning}</Alert>
        )}

        <PlatformHealthPanel
          health={center.platformHealth}
          investigationStreamState={investigationStreamState}
          transactionStreamState={transactionStreamState}
        />

        <InvestigationMetricsPanel metrics={center.investigationMetrics} />

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 7 }}>
            <AgentPerformancePanel rows={center.agentPerformance} />
          </Grid>
          <Grid size={{ xs: 12, lg: 5 }}>
            <ErrorMonitoringPanel
              recentErrors={center.recentErrors}
              executionFailureTotal={center.executionFailureTotal}
              reportFallbackTotal={center.reportFallbackTotal}
              reportFailureTotal={center.reportFailureTotal}
              sseReconnectCount={sseReconnectCount}
            />
          </Grid>
        </Grid>

        <LiveActivityFeedPanel entries={activityFeed} />
      </Stack>
    </PageContainer>
  );
}
