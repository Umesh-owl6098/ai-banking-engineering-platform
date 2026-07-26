import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  FormControl,
  InputLabel,
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
  TextField,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useNavigate } from "react-router-dom";

import ConnectionStatusChip from "../components/ui/ConnectionStatusChip";
import EmptyState from "../components/ui/EmptyState";
import ErrorState from "../components/ui/ErrorState";
import { LoadingSpinner } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import PageHeader from "../components/ui/PageHeader";
import StatusChip from "../components/ui/StatusChip";
import TruncatedText from "../components/ui/TruncatedText";

import { useAuth } from "../hooks/useAuth";
import { useInvestigationLiveStream } from "../hooks/useInvestigationLiveStream";
import {
  getProjectInvestigations,
  INVESTIGATION_PROJECT_ID,
  retryInvestigationExecution,
  type InvestigationCreatedNotification,
} from "../services/investigationService";
import {
  getCustomer,
  getTransaction,
} from "../services/mockBankingService";
import type { InvestigationCase } from "../types/investigation";
import type { InvestigationExecutionEvent } from "../types/investigationExecution";
import type {
  MockCustomer,
  MockTransaction,
} from "../types/mockBanking";
import { getApiErrorMessageForStatus } from "../utils/apiError";
import { layout } from "../theme/tokens";

interface InvestigationRow {
  investigation: InvestigationCase;
  customer?: MockCustomer;
  transaction?: MockTransaction;
}

const IN_PROGRESS_STATUSES = new Set([
  "NEW",
  "RUNNING",
  "REPORT_GENERATED",
]);

function pipelineLabel(status: string): string {
  switch (status) {
    case "NEW":
      return "Queued";
    case "RUNNING":
      return "Agents running";
    case "REPORT_GENERATED":
      return "Report ready";
    case "AWAITING_REVIEW":
      return "Awaiting review";
    case "EXECUTION_FAILED":
      return "Failed";
    default:
      return status.replaceAll("_", " ");
  }
}

export default function InvestigationsPage() {
  const navigate = useNavigate();
  const {
    isLoading: isAuthLoading,
    canCreateInvestigation,
    canExecuteInvestigation,
  } = useAuth();
  const [rows, setRows] = useState<InvestigationRow[]>([]);
  const [status, setStatus] = useState("");
  const [priority, setPriority] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [customerSearch, setCustomerSearch] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<string | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);

  const enrichInvestigationRow = useCallback(
    async (
      investigation: InvestigationCase,
    ): Promise<InvestigationRow> => {
      const [customer, transaction] = await Promise.all([
        investigation.customerId
          ? getCustomer(investigation.customerId).catch(() => undefined)
          : Promise.resolve(undefined),
        investigation.transactionId
          ? getTransaction(investigation.transactionId).catch(() => undefined)
          : Promise.resolve(undefined),
      ]);

      return {
        investigation,
        customer,
        transaction,
      };
    },
    [],
  );

  const upsertInvestigation = useCallback(
    async (notification: InvestigationCreatedNotification) => {
      const investigation: InvestigationCase = {
        id: notification.investigationId,
        projectId: notification.projectId,
        conversationId: null,
        customerId: notification.customerId,
        transactionId: notification.transactionId,
        caseType: "FRAUD",
        title: notification.title,
        description: notification.screeningReason ?? notification.title,
        status: notification.status,
        priority: notification.priority,
        analystId: "system",
        autoCreated: notification.autoCreated,
        screeningStatus: notification.screeningStatus,
        screeningReason: notification.screeningReason,
        screeningTriggeredRules: notification.screeningTriggeredRules,
        createdAt: notification.createdAt,
        updatedAt: notification.createdAt,
      };

      try {
        const row = await enrichInvestigationRow(investigation);

        setRows((current) => {
          const existingIndex = current.findIndex(
            (item) => item.investigation.id === investigation.id,
          );

          if (existingIndex >= 0) {
            const updated = [...current];
            updated[existingIndex] = row;
            return updated;
          }

          return [row, ...current];
        });
      } catch {
        setRows((current) => {
          const existingIndex = current.findIndex(
            (item) => item.investigation.id === investigation.id,
          );

          if (existingIndex >= 0) {
            return current;
          }

          return [{ investigation }, ...current];
        });
      }
    },
    [enrichInvestigationRow],
  );

  const handleExecutionEvent = useCallback(
    (event: InvestigationExecutionEvent) => {
      setRows((current) =>
        current.map((row) =>
          row.investigation.id === event.investigationId
            ? {
                ...row,
                investigation: {
                  ...row.investigation,
                  status: event.caseStatus,
                  updatedAt:
                    event.completedAt ??
                    event.startedAt ??
                    row.investigation.updatedAt,
                  executionFailureMessage:
                    event.eventType === "EXECUTION_FAILED"
                      ? event.message
                      : row.investigation.executionFailureMessage,
                  executionFailureStage:
                    event.eventType === "EXECUTION_FAILED"
                      ? event.stage
                      : row.investigation.executionFailureStage,
                  executionFailureAt:
                    event.eventType === "EXECUTION_FAILED"
                      ? event.completedAt
                      : row.investigation.executionFailureAt,
                },
              }
            : row,
        ),
      );
    },
    [],
  );

  const reloadInvestigations = useCallback(async () => {
    const investigations =
      await getProjectInvestigations(INVESTIGATION_PROJECT_ID);
    const customerIds = [
      ...new Set(
        investigations
          .map((investigation) => investigation.customerId)
          .filter((customerId): customerId is string =>
            Boolean(customerId),
          ),
      ),
    ];
    const transactionIds = [
      ...new Set(
        investigations
          .map((investigation) => investigation.transactionId)
          .filter((transactionId): transactionId is string =>
            Boolean(transactionId),
          ),
      ),
    ];
    const [customers, transactions] = await Promise.all([
      Promise.all(customerIds.map(getCustomer)),
      Promise.all(transactionIds.map(getTransaction)),
    ]);
    const customersById = new Map(
      customers.map((customer) => [customer.id, customer]),
    );
    const transactionsById = new Map(
      transactions.map((transaction) => [
        transaction.id,
        transaction,
      ]),
    );

    setRows(
      investigations.map((investigation) => ({
        investigation,
        customer: investigation.customerId
          ? customersById.get(investigation.customerId)
          : undefined,
        transaction: investigation.transactionId
          ? transactionsById.get(investigation.transactionId)
          : undefined,
      })),
    );
  }, []);

  const { streamState } = useInvestigationLiveStream({
    enabled: !isAuthLoading,
    onCreated: (notification) => {
      void upsertInvestigation(notification);
    },
    onExecution: handleExecutionEvent,
    onReconnect: () => {
      void reloadInvestigations().catch((caughtError) => {
        setError(
          getApiErrorMessageForStatus(
            caughtError,
            "Unable to refresh investigations after reconnect.",
          ),
        );
      });
    },
  });

  useEffect(() => {
    if (isAuthLoading) {
      return;
    }

    let isCurrent = true;

    async function loadInvestigations(): Promise<void> {
      try {
        setIsLoading(true);
        setError(null);
        await reloadInvestigations();
      } catch (caughtError) {
        if (isCurrent) {
          setError(
            getApiErrorMessageForStatus(
              caughtError,
              "Unable to load investigations.",
            ),
          );
        }
      } finally {
        if (isCurrent) {
          setIsLoading(false);
        }
      }
    }

    void loadInvestigations();

    return () => {
      isCurrent = false;
    };
  }, [isAuthLoading, reloadInvestigations]);

  const hasInProgressInvestigations = useMemo(
    () =>
      rows.some(({ investigation }) =>
        IN_PROGRESS_STATUSES.has(investigation.status),
      ),
    [rows],
  );

  useEffect(() => {
    if (isAuthLoading || !hasInProgressInvestigations) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void reloadInvestigations().catch(() => undefined);
    }, 3000);

    return () => window.clearInterval(intervalId);
  }, [hasInProgressInvestigations, isAuthLoading, reloadInvestigations]);

  async function handleRetryExecution(
    investigationId: string,
  ): Promise<void> {
    try {
      setRetryingId(investigationId);
      setRetryError(null);
      const updated = await retryInvestigationExecution(investigationId);
      setRows((current) =>
        current.map((row) =>
          row.investigation.id === investigationId
            ? { ...row, investigation: updated }
            : row,
        ),
      );
    } catch (caughtError) {
      setRetryError(
        getApiErrorMessageForStatus(
          caughtError,
          "Unable to retry investigation execution.",
        ),
      );
    } finally {
      setRetryingId(null);
    }
  }

  const filteredRows = useMemo(
    () =>
      rows.filter(({ investigation, customer }) => {
        const matchesStatus =
          !status || investigation.status === status;
        const matchesPriority =
          !priority || investigation.priority === priority;
        const matchesSource =
          !sourceFilter
          || (sourceFilter === "auto" && investigation.autoCreated)
          || (sourceFilter === "manual" && !investigation.autoCreated);
        const matchesCustomer =
          !customerSearch.trim()
          || (customer?.fullName ?? "")
            .toLowerCase()
            .includes(customerSearch.trim().toLowerCase())
          || investigation.title
            .toLowerCase()
            .includes(customerSearch.trim().toLowerCase());
        const createdAt = new Date(investigation.createdAt).getTime();
        const matchesFrom =
          !dateFrom || createdAt >= new Date(`${dateFrom}T00:00:00`).getTime();
        const matchesTo =
          !dateTo || createdAt <= new Date(`${dateTo}T23:59:59`).getTime();

        return (
          matchesStatus
          && matchesPriority
          && matchesSource
          && matchesCustomer
          && matchesFrom
          && matchesTo
        );
      }),
    [customerSearch, dateFrom, dateTo, priority, rows, sourceFilter, status],
  );

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
        <PageHeader
          title="Investigations"
          description="Monitor auto-created and manual cases across the active project."
          meta={<ConnectionStatusChip state={streamState} />}
          actions={
            canCreateInvestigation ? (
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => navigate("/investigations/new")}
              >
                Create Investigation
              </Button>
            ) : undefined
          }
        />

        <Paper sx={{ p: 2 }}>
          <Stack
            direction={{ xs: "column", lg: "row" }}
            spacing={1.5}
            sx={{ flexWrap: "wrap" }}
          >
            <FormControl size="small" sx={{ minWidth: 160 }}>
              <InputLabel id="case-status-label">Status</InputLabel>
              <Select
                labelId="case-status-label"
                label="Status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <MenuItem value="">All statuses</MenuItem>
                <MenuItem value="NEW">New</MenuItem>
                <MenuItem value="RUNNING">Running</MenuItem>
                <MenuItem value="REPORT_GENERATED">Report Generated</MenuItem>
                <MenuItem value="EXECUTION_FAILED">Execution Failed</MenuItem>
                <MenuItem value="AWAITING_REVIEW">Awaiting Review</MenuItem>
                <MenuItem value="ESCALATED">Escalated</MenuItem>
                <MenuItem value="APPROVED">Approved</MenuItem>
                <MenuItem value="REJECTED">Rejected</MenuItem>
                <MenuItem value="CLOSED">Closed</MenuItem>
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 140 }}>
              <InputLabel id="case-priority-label">Severity</InputLabel>
              <Select
                labelId="case-priority-label"
                label="Severity"
                value={priority}
                onChange={(event) => setPriority(event.target.value)}
              >
                <MenuItem value="">All severities</MenuItem>
                <MenuItem value="LOW">Low</MenuItem>
                <MenuItem value="MEDIUM">Medium</MenuItem>
                <MenuItem value="HIGH">High</MenuItem>
                <MenuItem value="CRITICAL">Critical</MenuItem>
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel id="case-source-label">Source</InputLabel>
              <Select
                labelId="case-source-label"
                label="Source"
                value={sourceFilter}
                onChange={(event) => setSourceFilter(event.target.value)}
              >
                <MenuItem value="">All sources</MenuItem>
                <MenuItem value="auto">Auto-created</MenuItem>
                <MenuItem value="manual">Manual</MenuItem>
              </Select>
            </FormControl>
            <TextField
              size="small"
              label="Customer search"
              value={customerSearch}
              onChange={(event) => setCustomerSearch(event.target.value)}
              sx={{ minWidth: 180 }}
            />
            <TextField
              size="small"
              label="From date"
              type="date"
              value={dateFrom}
              onChange={(event) => setDateFrom(event.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ width: 150 }}
            />
            <TextField
              size="small"
              label="To date"
              type="date"
              value={dateTo}
              onChange={(event) => setDateTo(event.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ width: 150 }}
            />
          </Stack>
        </Paper>

        {error && (
          <ErrorState
            error={error}
            fallback="Unable to load investigations."
            onRetry={() => void reloadInvestigations()}
          />
        )}
        {retryError && (
          <Alert severity="error">{retryError}</Alert>
        )}

        <Paper>
          {isLoading ? (
            <LoadingSpinner label="Loading investigations…" />
          ) : filteredRows.length === 0 ? (
            <EmptyState
              title="No investigations match the selected filters"
              description="Adjust filters or wait for screening to create a new case."
            />
          ) : (
            <TableContainer sx={{ maxHeight: 560, overflow: "auto" }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Reference</TableCell>
                    <TableCell>Customer</TableCell>
                    <TableCell>Source</TableCell>
                    <TableCell>Severity</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Pipeline</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredRows.map(
                    ({ investigation, customer, transaction }) => (
                      <TableRow
                        hover
                        key={investigation.id}
                        onClick={() =>
                          navigate(
                            `/investigations/${investigation.id}`,
                          )
                        }
                        sx={{ cursor: "pointer" }}
                      >
                        <TableCell>
                          <TruncatedText
                            value={
                              transaction?.transactionReference
                              ?? investigation.title
                            }
                            maxWidth={140}
                            monospace
                            copyable={false}
                          />
                        </TableCell>
                        <TableCell>{customer?.fullName ?? "—"}</TableCell>
                        <TableCell>
                          {investigation.autoCreated ? "Auto-created" : "Manual"}
                        </TableCell>
                        <TableCell>
                          <StatusChip
                            kind="severity"
                            value={investigation.priority}
                          />
                        </TableCell>
                        <TableCell>
                          <StatusChip
                            kind="investigation"
                            value={investigation.status}
                          />
                          {investigation.status === "EXECUTION_FAILED"
                            && investigation.executionFailureMessage && (
                            <Typography
                              variant="caption"
                              color="error"
                              sx={{ display: "block", mt: 0.5 }}
                            >
                              {investigation.executionFailureMessage}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">
                            {pipelineLabel(investigation.status)}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          {new Date(
                            investigation.createdAt,
                          ).toLocaleString()}
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                            <Button
                              size="small"
                              onClick={(event) => {
                                event.stopPropagation();
                                navigate(
                                  `/investigations/${investigation.id}`,
                                );
                              }}
                            >
                              Open
                            </Button>
                            {canExecuteInvestigation
                              && (investigation.status === "EXECUTION_FAILED"
                                || investigation.status === "NEW") && (
                              <Button
                                size="small"
                                variant="outlined"
                                disabled={retryingId === investigation.id}
                                onClick={(event) => {
                                  event.stopPropagation();
                                  void handleRetryExecution(investigation.id);
                                }}
                              >
                                {retryingId === investigation.id
                                  ? "Retrying..."
                                  : "Retry"}
                              </Button>
                            )}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ),
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      </Stack>
    </PageContainer>
  );
}
