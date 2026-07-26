import {
  Alert,
  Button,
  Chip,
  FormControl,
  InputLabel,
  Link,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import StopIcon from "@mui/icons-material/Stop";
import BoltIcon from "@mui/icons-material/Bolt";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link as RouterLink } from "react-router-dom";

import ConnectionStatusChip from "../components/ui/ConnectionStatusChip";
import EmptyState from "../components/ui/EmptyState";
import { LoadingSpinner } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import PageHeader from "../components/ui/PageHeader";
import StatusChip from "../components/ui/StatusChip";
import TruncatedText from "../components/ui/TruncatedText";
import { useAuth } from "../hooks/useAuth";
import { AUTH_STORAGE_KEY } from "../types/auth";
import {
  generateScenario,
  getSimulationLiveUrl,
  getSimulationStatus,
  runDemoScenario,
  startSimulation,
  stopSimulation,
  type DemoScenarioRunResponse,
  type LiveTransactionEvent,
} from "../services/simulationService";
import { getInvestigation } from "../services/investigationService";
import {
  SIMULATION_SCENARIOS,
  type DemoScenarioKey,
  type SimulationScenario,
  type TransactionScreeningStatus,
} from "../types/simulation";
import {
  getApiErrorMessage,
  getApiErrorMessageForStatus,
} from "../utils/apiError";
import { formatCurrency } from "../utils/statusBadges";

const MAX_ROWS = 100;

const DEMO_SCENARIOS: Array<{
  key: DemoScenarioKey;
  label: string;
  description: string;
}> = [
  {
    key: "structuring",
    label: "Run Structuring Demo",
    description: "6 below-threshold cash deposits for one customer",
  },
  {
    key: "high-risk-wire",
    label: "Run High-Risk Wire Demo",
    description: "Large international wire to a high-risk country",
  },
  {
    key: "money-mule",
    label: "Run Money Mule Demo",
    description: "Inbound deposits followed by rapid outbound transfer",
  },
  {
    key: "normal",
    label: "Run Normal Activity Demo",
    description: "Routine domestic payment with no investigation",
  },
];

const TERMINAL_INVESTIGATION_STATUSES = new Set([
  "AWAITING_REVIEW",
  "EXECUTION_FAILED",
  "APPROVED",
  "REJECTED",
  "CLOSED",
  "ESCALATED",
]);

function screeningBackground(
  status: TransactionScreeningStatus | undefined,
): string {
  switch (status) {
    case "CRITICAL":
      return "error.light";
    case "SUSPICIOUS":
      return "warning.light";
    case "CLEARED":
      return "success.light";
    case "PROCESSING":
    case "SCREENING_FAILED":
    default:
      return "grey.200";
  }
}

function formatLifecycleStatus(
  lifecycleStatus: LiveTransactionEvent["lifecycleStatus"],
): string {
  switch (lifecycleStatus) {
    case "INVESTIGATION_CREATED":
      return "Investigation Created";
    case "PROCESSING":
      return "Processing";
    default:
      return "Screened";
  }
}

function formatRules(rules: string[] | undefined): string {
  if (!rules || rules.length === 0) {
    return "—";
  }

  return rules.map((rule) => rule.replaceAll("_", " ")).join(", ");
}

function parseSseEvent(
  rawEvent: string,
): LiveTransactionEvent | null {
  const dataLines = rawEvent
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trim());

  if (dataLines.length === 0) {
    return null;
  }

  try {
    return JSON.parse(dataLines.join("\n")) as LiveTransactionEvent;
  } catch {
    return null;
  }
}

export default function LiveTransactionsPage() {
  const { token, user, isLoading: isAuthLoading } = useAuth();
  const canControlSimulation =
    user?.role === "ADMIN" || user?.role === "SUPERVISOR";

  const [scenario, setScenario] =
    useState<SimulationScenario>("MIXED");
  const [statusRunning, setStatusRunning] = useState(false);
  const [connectionState, setConnectionState] = useState<
    "connecting" | "connected" | "disconnected"
  >("disconnected");
  const [transactionsReceived, setTransactionsReceived] = useState(0);
  const [rows, setRows] = useState<LiveTransactionEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [demoResult, setDemoResult] =
    useState<DemoScenarioRunResponse | null>(null);
  const [demoRunningKey, setDemoRunningKey] =
    useState<DemoScenarioKey | null>(null);
  const [pipelineStatus, setPipelineStatus] = useState<string | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);

  const upsertTransaction = useCallback((event: LiveTransactionEvent) => {
    const eventId = String(event.transactionId);
    setRows((current) => {
      const existingIndex = current.findIndex(
        (row) => String(row.transactionId) === eventId,
      );

      if (existingIndex >= 0) {
        const updated = [...current];
        updated[existingIndex] = event;
        return updated;
      }

      setTransactionsReceived((count) => count + 1);
      return [event, ...current].slice(0, MAX_ROWS);
    });
  }, []);

  const connectStream = useCallback(async () => {
    abortControllerRef.current?.abort();

    const authToken =
      token ??
      (() => {
        const raw = localStorage.getItem(AUTH_STORAGE_KEY);
        if (!raw) {
          return null;
        }
        try {
          return (JSON.parse(raw) as { token?: string }).token ?? null;
        } catch {
          return null;
        }
      })();

    if (!authToken) {
      setConnectionState("disconnected");
      return;
    }

    const controller = new AbortController();
    abortControllerRef.current = controller;
    setConnectionState("connecting");

    try {
      const response = await fetch(getSimulationLiveUrl(), {
        headers: {
          Authorization: `Bearer ${authToken}`,
          Accept: "text/event-stream",
        },
        signal: controller.signal,
      });

      if (!response.ok || !response.body) {
        let message = "Unable to connect to live transaction stream";
        try {
          const body = (await response.json()) as { message?: string };
          if (body.message) {
            message = body.message;
          }
        } catch {
          // Keep default message when error body is not JSON.
        }
        throw new Error(message);
      }

      setConnectionState("connected");

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split("\n\n");
        buffer = events.pop() ?? "";

        for (const rawEvent of events) {
          const parsed = parseSseEvent(rawEvent);
          if (parsed) {
            upsertTransaction(parsed);
          }
        }
      }
    } catch (caughtError) {
      if (!controller.signal.aborted) {
        setConnectionState("disconnected");
        setError(
          getApiErrorMessage(
            caughtError,
            "Live transaction stream disconnected.",
          ),
        );
      }
    }
  }, [upsertTransaction, token]);

  useEffect(() => {
    if (isAuthLoading) {
      return;
    }

    let isCurrent = true;

    async function loadStatus(): Promise<void> {
      try {
        const status = await getSimulationStatus();
        if (isCurrent) {
          setStatusRunning(status.running);
          setScenario(status.scenario);
        }
      } catch (caughtError) {
        if (isCurrent) {
          setError(
            getApiErrorMessage(
              caughtError,
              "Unable to load simulation status.",
            ),
          );
        }
      }
    }

    void loadStatus();
    void connectStream();

    return () => {
      isCurrent = false;
      abortControllerRef.current?.abort();
    };
  }, [connectStream, isAuthLoading]);

  useEffect(() => {
    if (
      !demoResult?.investigationId
      || TERMINAL_INVESTIGATION_STATUSES.has(
        pipelineStatus ?? demoResult.investigationStatus ?? "",
      )
    ) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void getInvestigation(demoResult.investigationId!)
        .then((investigation) => {
          setPipelineStatus(investigation.status);
        })
        .catch(() => undefined);
    }, 2000);

    return () => window.clearInterval(intervalId);
  }, [demoResult, pipelineStatus]);

  async function handleRunDemo(scenario: DemoScenarioKey): Promise<void> {
    try {
      setDemoRunningKey(scenario);
      setError(null);
      const result = await runDemoScenario(scenario);
      setDemoResult(result);
      setPipelineStatus(result.investigationStatus);
    } catch (caughtError) {
      setError(
        getApiErrorMessageForStatus(
          caughtError,
          "Unable to run demo scenario.",
        ),
      );
    } finally {
      setDemoRunningKey(null);
    }
  }

  async function handleStart(): Promise<void> {
    try {
      setIsSubmitting(true);
      setError(null);
      const status = await startSimulation(scenario);
      setStatusRunning(status.running);
    } catch (caughtError) {
      setError(
        getApiErrorMessage(caughtError, "Unable to start simulation."),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleStop(): Promise<void> {
    try {
      setIsSubmitting(true);
      setError(null);
      const status = await stopSimulation();
      setStatusRunning(status.running);
    } catch (caughtError) {
      setError(
        getApiErrorMessage(caughtError, "Unable to stop simulation."),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleGenerateScenario(): Promise<void> {
    try {
      setIsSubmitting(true);
      setError(null);
      const status = await generateScenario(scenario);
      setScenario(status.scenario);
    } catch (caughtError) {
      setError(
        getApiErrorMessage(
          caughtError,
          "Unable to generate scenario transaction.",
        ),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <PageContainer>
      <Stack spacing={2}>
        <PageHeader
          title="Live Transactions"
          description="Stream simulated banking transactions and run deterministic demo scenarios."
          meta={<ConnectionStatusChip state={connectionState} />}
        />

        {error && (
          <Alert
            severity={
              error.toLowerCase().includes("authentication") ? "warning" : "error"
            }
            action={
              <Button color="inherit" size="small" onClick={() => setError(null)}>
                Dismiss
              </Button>
            }
          >
            {error}
          </Alert>
        )}

        <Paper sx={{ p: 2 }}>
        <Stack
          direction={{ xs: "column", lg: "row" }}
          spacing={2}
          sx={{ alignItems: { lg: "center" } }}
        >
          {canControlSimulation && (
            <>
              <Button
                variant="contained"
                startIcon={<PlayArrowIcon />}
                disabled={statusRunning || isSubmitting}
                onClick={() => void handleStart()}
              >
                Start Simulation
              </Button>
              <Button
                variant="outlined"
                color="error"
                startIcon={<StopIcon />}
                disabled={!statusRunning || isSubmitting}
                onClick={() => void handleStop()}
              >
                Stop Simulation
              </Button>
            </>
          )}

          <FormControl size="small" sx={{ minWidth: 220 }}>
            <InputLabel id="simulation-scenario-label">Scenario</InputLabel>
            <Select
              labelId="simulation-scenario-label"
              label="Scenario"
              value={scenario}
              onChange={(event) =>
                setScenario(event.target.value as SimulationScenario)
              }
              disabled={!canControlSimulation || isSubmitting}
            >
              {SIMULATION_SCENARIOS.map((option) => (
                <MenuItem key={option} value={option}>
                  {option.replaceAll("_", " ")}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {canControlSimulation && (
            <Button
              variant="outlined"
              startIcon={<BoltIcon />}
              disabled={isSubmitting}
              onClick={() => void handleGenerateScenario()}
            >
              Generate Scenario
            </Button>
          )}

          <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
            <ConnectionStatusChip state={connectionState} />
            <Chip
              label={`Received: ${transactionsReceived}`}
              size="small"
              variant="outlined"
            />
            {statusRunning && (
              <Chip label="Simulation running" color="info" size="small" variant="outlined" />
            )}
          </Stack>
        </Stack>
      </Paper>

      {canControlSimulation && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>
            Demo Scenarios
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
            One-click deterministic demos through live screening and investigation.
          </Typography>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={1}
            sx={{ flexWrap: "wrap", mb: 2 }}
          >
            {DEMO_SCENARIOS.map((demo) => (
              <Button
                key={demo.key}
                variant="outlined"
                size="small"
                disabled={Boolean(demoRunningKey) || isSubmitting}
                onClick={() => void handleRunDemo(demo.key)}
                title={demo.description}
              >
                {demoRunningKey === demo.key ? "Running…" : demo.label}
              </Button>
            ))}
          </Stack>

          {demoResult && (
            <Stack spacing={0.75} sx={{ p: 1.5, bgcolor: "grey.50", borderRadius: 1.5 }}>
              <Typography variant="subtitle2">
                {demoResult.scenario.replaceAll("_", " ")}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {demoResult.transactionsGenerated} transaction(s) · {demoResult.screeningSummary}
              </Typography>
              {demoResult.investigationId ? (
                <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                  <StatusChip
                    kind="investigation"
                    value={pipelineStatus ?? demoResult.investigationStatus ?? "NEW"}
                  />
                  <Button
                    component={RouterLink}
                    to={`/investigations/${demoResult.investigationId}`}
                    variant="contained"
                    size="small"
                  >
                    Open Investigation
                  </Button>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No investigation created (expected for normal activity).
                </Typography>
              )}
            </Stack>
          )}
        </Paper>
      )}

      <TableContainer component={Paper} sx={{ maxHeight: 560, overflow: "auto" }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell>Time</TableCell>
              <TableCell>Customer</TableCell>
              <TableCell>Reference</TableCell>
              <TableCell>Amount</TableCell>
              <TableCell>Route</TableCell>
              <TableCell>Channel</TableCell>
              <TableCell>Scenario</TableCell>
              <TableCell>Risk Score</TableCell>
              <TableCell>Flagged</TableCell>
              <TableCell>Screening Status</TableCell>
              <TableCell>Reason</TableCell>
              <TableCell>Triggered Rules</TableCell>
              <TableCell>Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={13}>
                  {connectionState === "connecting" ? (
                    <LoadingSpinner label="Connecting to live stream…" />
                  ) : (
                    <EmptyState
                      title="Waiting for live transactions"
                      description="Start the simulation or run a demo scenario to populate the feed."
                    />
                  )}
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => (
                <TableRow
                  key={row.transactionId}
                  sx={{
                    bgcolor: screeningBackground(row.screeningStatus),
                  }}
                >
                  <TableCell>
                    {new Date(row.createdAt).toLocaleTimeString()}
                  </TableCell>
                  <TableCell>{row.customerName}</TableCell>
                  <TableCell>
                    <TruncatedText
                      value={row.transactionReference}
                      maxWidth={120}
                      copyable={false}
                    />
                  </TableCell>
                  <TableCell>
                    {formatCurrency(row.amount, row.currency)}
                  </TableCell>
                  <TableCell>
                    {row.originCountry} → {row.destinationCountry}
                  </TableCell>
                  <TableCell>{row.channel}</TableCell>
                  <TableCell>
                    {(row.demoScenario ?? row.scenario).replaceAll("_", " ")}
                  </TableCell>
                  <TableCell>{row.riskScore?.toFixed(0) ?? "—"}</TableCell>
                  <TableCell>{row.flagged ? "Yes" : "No"}</TableCell>
                  <TableCell>
                    {row.screeningStatus ? (
                      <StatusChip kind="screening" value={row.screeningStatus} />
                    ) : (
                      "—"
                    )}
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" noWrap sx={{ maxWidth: 180 }}>
                      {row.screeningReason ?? "—"}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" noWrap sx={{ maxWidth: 160 }}>
                      {formatRules(row.triggeredRules)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Stack spacing={0.5}>
                      <Typography variant="body2">
                        {formatLifecycleStatus(row.lifecycleStatus)}
                      </Typography>
                      {row.investigationId && (
                        <Link
                          component={RouterLink}
                          to={`/investigations/${row.investigationId}`}
                          underline="hover"
                          onClick={(event) => event.stopPropagation()}
                        >
                          Open Investigation
                        </Link>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
      </Stack>
    </PageContainer>
  );
}
