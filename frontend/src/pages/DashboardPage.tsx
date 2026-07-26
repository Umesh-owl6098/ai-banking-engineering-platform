import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Grid,
  Stack,
} from "@mui/material";
import AccountBalanceWalletOutlinedIcon from "@mui/icons-material/AccountBalanceWalletOutlined";
import AssignmentOutlinedIcon from "@mui/icons-material/AssignmentOutlined";
import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import ErrorOutlineOutlinedIcon from "@mui/icons-material/ErrorOutlineOutlined";
import FactCheckOutlinedIcon from "@mui/icons-material/FactCheckOutlined";
import GppBadOutlinedIcon from "@mui/icons-material/GppBadOutlined";
import HourglassTopOutlinedIcon from "@mui/icons-material/HourglassTopOutlined";
import ReportProblemOutlinedIcon from "@mui/icons-material/ReportProblemOutlined";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";

import ActiveInvestigationsPanel from "../components/dashboard/ActiveInvestigationsPanel";
import AgentActivityPanel from "../components/dashboard/AgentActivityPanel";
import AwaitingReviewPanel from "../components/dashboard/AwaitingReviewPanel";
import DashboardHeader from "../components/dashboard/DashboardHeader";
import DashboardKpiCards from "../components/dashboard/DashboardKpiCards";
import InvestigationSummaryCharts from "../components/dashboard/InvestigationSummaryCharts";
import LiveAlertsFeed from "../components/dashboard/LiveAlertsFeed";
import RecentInvestigationsPanel from "../components/dashboard/RecentInvestigationsPanel";
import RecentTransactionsPanel from "../components/dashboard/RecentTransactionsPanel";
import PageContainer from "../components/ui/PageContainer";
import { useAuth } from "../hooks/useAuth";
import { layout } from "../theme/tokens";
import { useInvestigationLiveStream } from "../hooks/useInvestigationLiveStream";
import { useSimulationLiveStream } from "../hooks/useSimulationLiveStream";
import { getOperationsDashboard } from "../services/dashboardService";
import { INVESTIGATION_PROJECT_ID } from "../services/investigationService";
import { getSimulationStatus } from "../services/simulationService";
import type { OperationsDashboardResponse } from "../types/dashboard";
import type { LiveTransactionEvent } from "../types/simulation";
import { getApiErrorMessageForStatus } from "../utils/apiError";
import {
  applyInvestigationCreated,
  applyInvestigationExecution,
  applyLiveTransactionEvent,
} from "../utils/dashboardLiveUpdates";
import { formatAverageDuration } from "../utils/statusBadges";

export default function DashboardPage() {
  const { isLoading: isAuthLoading } = useAuth();
  const [dashboard, setDashboard] =
    useState<OperationsDashboardResponse | null>(null);
  const [simulationRunning, setSimulationRunning] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    try {
      setLoadError(null);
      const [operationsDashboard, simulationStatus] = await Promise.all([
        getOperationsDashboard(INVESTIGATION_PROJECT_ID),
        getSimulationStatus().catch(() => ({
          running: false,
          scenario: "NORMAL" as const,
          intervalMs: 3000,
          transactionsGenerated: 0,
          startedAt: null,
        })),
      ]);

      setDashboard(operationsDashboard);
      setSimulationRunning(simulationStatus.running);
    } catch (caughtError) {
      setLoadError(
        getApiErrorMessageForStatus(
          caughtError,
          "Unable to load operations dashboard.",
        ),
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isAuthLoading) {
      return;
    }

    void loadDashboard();
  }, [isAuthLoading, loadDashboard]);

  const handleLiveTransaction = useCallback((event: LiveTransactionEvent) => {
    setDashboard((current) =>
      current ? applyLiveTransactionEvent(current, event) : current,
    );
  }, []);

  const { streamState: investigationStreamState } = useInvestigationLiveStream({
    enabled: !isAuthLoading,
    onCreated: (notification) => {
      setDashboard((current) =>
        current ? applyInvestigationCreated(current, notification) : current,
      );
    },
    onExecution: (event) => {
      setDashboard((current) =>
        current ? applyInvestigationExecution(current, event) : current,
      );
    },
    onReconnect: () => {
      void loadDashboard();
    },
  });

  const { streamState: transactionStreamState } = useSimulationLiveStream({
    enabled: !isAuthLoading,
    maxEvents: 200,
    onEvent: handleLiveTransaction,
    onReconnect: () => {
      void loadDashboard();
    },
  });

  useEffect(() => {
    if (
      investigationStreamState === "disconnected"
      || transactionStreamState === "disconnected"
    ) {
      setConnectionError(
        "Live stream disconnected. Showing last loaded data while reconnecting.",
      );
      return;
    }

    if (
      investigationStreamState === "connected"
      && transactionStreamState === "connected"
    ) {
      setConnectionError(null);
    }
  }, [investigationStreamState, transactionStreamState]);

  const kpiCards = useMemo(() => {
    if (!dashboard) {
      return [];
    }

    const { kpis } = dashboard;

    return [
      {
        label: "Transactions Processed Today",
        value: kpis.transactionsProcessedToday,
        icon: <AccountBalanceWalletOutlinedIcon />,
        accent: "#2563eb",
      },
      {
        label: "Cleared Transactions",
        value: kpis.clearedTransactions,
        icon: <CheckCircleOutlinedIcon />,
        accent: "#059669",
      },
      {
        label: "Suspicious Transactions",
        value: kpis.suspiciousTransactions,
        icon: <WarningAmberOutlinedIcon />,
        accent: "#d97706",
      },
      {
        label: "Critical Transactions",
        value: kpis.criticalTransactions,
        icon: <GppBadOutlinedIcon />,
        accent: "#dc2626",
      },
      {
        label: "Active Investigations",
        value: kpis.activeInvestigations,
        icon: <AssignmentOutlinedIcon />,
        accent: "#7c3aed",
      },
      {
        label: "Awaiting Human Review",
        value: kpis.awaitingHumanReview,
        icon: <HourglassTopOutlinedIcon />,
        accent: "#ea580c",
      },
      {
        label: "Failed Investigations",
        value: kpis.failedInvestigations,
        icon: <ErrorOutlineOutlinedIcon />,
        accent: "#b91c1c",
      },
      {
        label: "Avg Investigation Duration",
        value: formatAverageDuration(kpis.averageInvestigationDurationMs),
        icon: <FactCheckOutlinedIcon />,
        accent: "#0891b2",
      },
    ];
  }, [dashboard]);

  const liveAlerts = dashboard?.criticalAlerts ?? [];

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!dashboard && loadError) {
    return (
      <Stack spacing={2} sx={{ py: 4 }}>
        <Alert severity="error">{loadError}</Alert>
        <Box>
          <Button variant="contained" onClick={() => void loadDashboard()}>
            Retry
          </Button>
        </Box>
      </Stack>
    );
  }

  if (!dashboard) {
    return (
      <Alert severity="info">
        No dashboard data is available yet. Start a simulation or run a demo
        scenario to populate the operations view.
      </Alert>
    );
  }

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
      <DashboardHeader
        simulationRunning={simulationRunning}
        investigationStreamState={investigationStreamState}
        transactionStreamState={transactionStreamState}
        lastUpdatedAt={dashboard.generatedAt}
      />

      {loadError && (
        <Alert
          severity="warning"
          action={
            <Button color="inherit" size="small" onClick={() => void loadDashboard()}>
              Retry
            </Button>
          }
        >
          {loadError}
        </Alert>
      )}

      {connectionError && (
        <Alert
          severity="warning"
          icon={<ReportProblemOutlinedIcon fontSize="inherit" />}
        >
          {connectionError}
        </Alert>
      )}

      <DashboardKpiCards items={kpiCards} />

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, xl: 5 }}>
          <LiveAlertsFeed
            alerts={liveAlerts}
            streamConnected={transactionStreamState === "connected"}
          />
        </Grid>
        <Grid size={{ xs: 12, xl: 7 }}>
          <ActiveInvestigationsPanel
            rows={dashboard.activeInvestigations}
            streamConnected={investigationStreamState === "connected"}
          />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 6 }}>
          <AwaitingReviewPanel rows={dashboard.awaitingReview} />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <AgentActivityPanel rows={dashboard.agentActivity} />
        </Grid>
      </Grid>

      <InvestigationSummaryCharts
        investigationsByStatus={dashboard.investigationsByStatus}
        investigationsBySeverity={dashboard.investigationsBySeverity}
        screeningResults={dashboard.screeningResults}
        triggeredRuleFrequency={dashboard.triggeredRuleFrequency}
      />

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <RecentInvestigationsPanel rows={dashboard.recentInvestigations} />
        </Grid>
        <Grid size={{ xs: 12, lg: 5 }}>
          <RecentTransactionsPanel transactions={dashboard.recentTransactions} />
        </Grid>
      </Grid>
      </Stack>
    </PageContainer>
  );
}
