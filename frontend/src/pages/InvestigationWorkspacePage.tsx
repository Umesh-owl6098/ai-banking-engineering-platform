import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Grid,
  Stack,
  Tab,
  Tabs,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useNavigate, useParams } from "react-router-dom";

import ExecutionTimeline from "../components/ExecutionTimeline";
import AgentFindingAccordion from "../components/investigation/AgentFindingAccordion";
import AuditTimelinePanel from "../components/investigation/AuditTimelinePanel";
import CommandCenterSummary, {
  resolveRecommendedAction,
} from "../components/investigation/CommandCenterSummary";
import CustomerRiskSummary from "../components/investigation/CustomerRiskSummary";
import FindingExplainabilityCard from "../components/investigation/FindingExplainabilityCard";
import HumanReviewPanel from "../components/investigation/HumanReviewPanel";
import InvestigationAssignmentPanel from "../components/investigation/InvestigationAssignmentPanel";
import InvestigationCaseHeader from "../components/investigation/InvestigationCaseHeader";
import InvestigationReportPanel from "../components/investigation/InvestigationReportPanel";
import TriggeringTransactionsTable from "../components/investigation/TriggeringTransactionsTable";
import EmptyState from "../components/ui/EmptyState";
import ErrorState from "../components/ui/ErrorState";
import { LoadingSpinner } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import SurfaceCard from "../components/ui/SurfaceCard";
import { useAuth } from "../hooks/useAuth";
import { useInvestigationLiveStream } from "../hooks/useInvestigationLiveStream";
import {
  approveInvestigation,
  escalateInvestigation,
  getInvestigation,
  getInvestigationExplainability,
  getInvestigationFindings,
  getInvestigationReport,
  getInvestigationReview,
  rejectInvestigation,
  requestMoreInvestigation,
  retryInvestigationExecution,
  startInvestigationReview,
} from "../services/investigationService";
import {
  getCustomer,
  getCustomerTransactions,
  getTransaction,
} from "../services/mockBankingService";
import type {
  AgentFinding,
  ExplainabilityResponse,
  InvestigationCase,
  InvestigationReport,
  InvestigationReviewContext,
} from "../types/investigation";
import type { ExecutionTimelineStage } from "../types/investigationExecution";
import type {
  MockCustomer,
  MockTransaction,
} from "../types/mockBanking";
import { getApiErrorMessage } from "../utils/apiError";
import {
  applyExecutionEvent,
  computeTotalExecutionDuration,
  hydrateExecutionTimelineFromPersisted,
} from "../utils/executionTimeline";
import {
  AGENT_TYPES,
  latestFindingByAgent,
  type WorkspaceAgentType,
} from "../utils/investigationWorkspace";
import { layout } from "../theme/tokens";

type DetailTab =
  | "findings"
  | "explainability"
  | "report"
  | "review"
  | "audit";

export default function InvestigationWorkspacePage() {
  const { investigationId } = useParams();
  const navigate = useNavigate();
  const {
    user,
    canReviewInvestigation,
    canDecideInvestigation,
    canRequestMoreInvestigation,
    canExecuteInvestigation,
    canAssignInvestigation,
    canClaimInvestigation,
    isReadOnly,
  } = useAuth();

  const [investigation, setInvestigation] =
    useState<InvestigationCase | null>(null);
  const [customer, setCustomer] = useState<MockCustomer | null>(null);
  const [transactions, setTransactions] = useState<MockTransaction[]>([]);
  const [findings, setFindings] = useState<AgentFinding[]>([]);
  const [explainability, setExplainability] = useState<
    ExplainabilityResponse[]
  >([]);
  const [reviewContext, setReviewContext] =
    useState<InvestigationReviewContext | null>(null);
  const [timelineStages, setTimelineStages] = useState<
    ExecutionTimelineStage[]
  >([]);
  const [comments, setComments] = useState("");
  const [decisionReason, setDecisionReason] = useState("");
  const [additionalNotes, setAdditionalNotes] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [loadWarnings, setLoadWarnings] = useState<string[]>([]);
  const [isRetryingExecution, setIsRetryingExecution] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<DetailTab>("findings");

  const syncTimelineFromPersisted = useCallback(
    (
      caseData: InvestigationCase,
      findingsData: AgentFinding[],
      reviewData: InvestigationReviewContext | null,
      report: InvestigationReport | null,
    ) => {
      setTimelineStages(
        hydrateExecutionTimelineFromPersisted(
          reviewData?.investigation ?? caseData,
          findingsData,
          report ?? reviewData?.report ?? null,
          reviewData?.timeline ?? [],
        ),
      );
    },
    [],
  );

  const refreshFindings = useCallback(async () => {
    if (!investigationId) {
      return [];
    }

    try {
      const findingsData = await getInvestigationFindings(investigationId);
      setFindings(findingsData);
      return findingsData;
    } catch (caughtError) {
      setLoadWarnings((current) => [
        ...current,
        getApiErrorMessage(
          caughtError,
          "Unable to refresh agent findings.",
        ),
      ]);
      throw caughtError;
    }
  }, [investigationId]);

  const refreshReviewContext = useCallback(async () => {
    if (!investigationId) {
      return null;
    }

    try {
      const context = await getInvestigationReview(investigationId);
      setReviewContext(context);
      setInvestigation(context.investigation);
      return context;
    } catch (caughtError) {
      setLoadWarnings((current) => [
        ...current,
        getApiErrorMessage(
          caughtError,
          "Unable to refresh review context.",
        ),
      ]);
      throw caughtError;
    }
  }, [investigationId]);

  const loadCommandCenter = useCallback(async () => {
    if (!investigationId) {
      setError("Investigation ID is required.");
      setIsLoading(false);
      return;
    }

    try {
      setIsLoading(true);
      setError(null);
      setLoadWarnings([]);

      const caseData = await getInvestigation(investigationId);
      const [
        customerData,
        transactionData,
        findingsResult,
        explainabilityResult,
        reviewResult,
        customerTransactions,
        reportData,
      ] = await Promise.allSettled([
        caseData.customerId
          ? getCustomer(caseData.customerId)
          : Promise.resolve(null),
        caseData.transactionId
          ? getTransaction(caseData.transactionId)
          : Promise.resolve(null),
        getInvestigationFindings(investigationId),
        getInvestigationExplainability(investigationId),
        getInvestigationReview(investigationId),
        caseData.customerId
          ? getCustomerTransactions(caseData.customerId)
          : Promise.resolve([]),
        getInvestigationReport(investigationId),
      ]);

      const warnings: string[] = [];
      const findingsData =
        findingsResult.status === "fulfilled" ? findingsResult.value : [];
      if (findingsResult.status === "rejected") {
        warnings.push(
          getApiErrorMessage(
            findingsResult.reason,
            "Unable to load agent findings.",
          ),
        );
      }

      const explainabilityData =
        explainabilityResult.status === "fulfilled"
          ? explainabilityResult.value
          : [];
      if (explainabilityResult.status === "rejected") {
        warnings.push(
          getApiErrorMessage(
            explainabilityResult.reason,
            "Unable to load explainability data.",
          ),
        );
      }

      const reviewData =
        reviewResult.status === "fulfilled" ? reviewResult.value : null;
      if (reviewResult.status === "rejected") {
        warnings.push(
          getApiErrorMessage(
            reviewResult.reason,
            "Unable to load review context.",
          ),
        );
      }

      const customerDataValue =
        customerData.status === "fulfilled" ? customerData.value : null;
      const transactionDataValue =
        transactionData.status === "fulfilled" ? transactionData.value : null;
      const customerTransactionsValue =
        customerTransactions.status === "fulfilled"
          ? customerTransactions.value
          : [];
      const reportDataValue =
        reportData.status === "fulfilled" ? reportData.value : null;

      const investigationCase = reviewData?.investigation ?? caseData;
      const resolvedReport = reviewData?.report ?? reportDataValue ?? null;
      const resolvedReviewContext: InvestigationReviewContext | null =
        reviewData
          ? { ...reviewData, report: resolvedReport }
          : resolvedReport
            ? {
                investigation: investigationCase,
                report: resolvedReport,
                reviewSummary: {
                  reviewStatus: "NOT_STARTED",
                  reviewUser: null,
                  decision: null,
                  reviewStartedAt: null,
                  decisionAt: null,
                },
                decisions: [],
                timeline: [],
              }
            : null;

      const linkedTransactions = transactionDataValue
        ? [
            transactionDataValue,
            ...customerTransactionsValue.filter(
              (item) => item.id !== transactionDataValue.id,
            ),
          ]
        : customerTransactionsValue;

      setInvestigation(investigationCase);
      setCustomer(customerDataValue);
      setTransactions(linkedTransactions);
      setFindings(findingsData);
      setExplainability(explainabilityData);
      setReviewContext(resolvedReviewContext);
      setLoadWarnings(warnings);
      syncTimelineFromPersisted(
        investigationCase,
        findingsData,
        resolvedReviewContext,
        resolvedReport,
      );
    } catch (caughtError) {
      setError(
        getApiErrorMessage(
          caughtError,
          "Unable to load investigation command center.",
        ),
      );
    } finally {
      setIsLoading(false);
    }
  }, [investigationId, syncTimelineFromPersisted]);

  useEffect(() => {
    void loadCommandCenter();
  }, [loadCommandCenter]);

  const rehydrateTimelineFromBackend = useCallback(async () => {
    if (!investigationId) {
      return;
    }

    try {
      const latestFindings = await refreshFindings().catch(() => findings);
      const context = await refreshReviewContext().catch(() => reviewContext);
      const report =
        context?.report ?? (await getInvestigationReport(investigationId));
      const caseData = context?.investigation ?? investigation;

      if (!caseData) {
        return;
      }

      const resolvedReviewContext =
        context
        ?? (report
          ? {
              investigation: caseData,
              report,
              reviewSummary: {
                reviewStatus: "NOT_STARTED" as const,
                reviewUser: null,
                decision: null,
                reviewStartedAt: null,
                decisionAt: null,
              },
              decisions: [],
              timeline: [],
            }
          : null);

      setInvestigation(caseData);
      syncTimelineFromPersisted(
        caseData,
        latestFindings,
        resolvedReviewContext,
        report,
      );
    } catch {
      // Errors are recorded via refresh* helpers.
    }
  }, [
    investigationId,
    refreshFindings,
    refreshReviewContext,
    syncTimelineFromPersisted,
    findings,
    reviewContext,
    investigation,
  ]);

  useEffect(() => {
    if (
      !investigationId
      || isLoading
      || !investigation
      || (investigation.status !== "NEW"
        && investigation.status !== "RUNNING"
        && investigation.status !== "REPORT_GENERATED")
    ) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void rehydrateTimelineFromBackend();
    }, 2000);

    return () => window.clearInterval(intervalId);
  }, [
    investigationId,
    isLoading,
    investigation,
    rehydrateTimelineFromBackend,
  ]);

  useEffect(() => {
    if (!isLoading && window.location.hash) {
      const hash = window.location.hash.replace("#", "");
      const tabFromHash: Partial<Record<string, DetailTab>> = {
        findings: "findings",
        explainability: "explainability",
        report: "report",
        review: "review",
        audit: "audit",
      };

      if (tabFromHash[hash]) {
        setActiveTab(tabFromHash[hash]!);
      }

      const target = document.querySelector(window.location.hash);
      target?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [isLoading]);

  useInvestigationLiveStream({
    investigationId,
    enabled: Boolean(investigationId) && !isLoading,
    onExecution: (event) => {
      setTimelineStages((current) =>
        applyExecutionEvent(current, event),
      );
      setInvestigation((current) =>
        current
          ? {
              ...current,
              status: event.caseStatus,
              updatedAt:
                event.completedAt ??
                event.startedAt ??
                current.updatedAt,
            }
          : current,
      );

      if (
        event.eventType === "AGENT_COMPLETED"
        || event.eventType === "AGENT_FAILED"
        || event.eventType === "SUPERVISOR_COMPLETED"
        || event.eventType === "REPORT_GENERATED"
        || event.eventType === "INVESTIGATION_READY_FOR_REVIEW"
        || event.eventType === "EXECUTION_FAILED"
      ) {
        void rehydrateTimelineFromBackend();
      }
    },
  });

  const findingsByAgent = useMemo(
    () =>
      Object.fromEntries(
        AGENT_TYPES.map((agentType) => [
          agentType,
          latestFindingByAgent(findings, agentType),
        ]),
      ) as Record<WorkspaceAgentType, AgentFinding | null>,
    [findings],
  );

  const explainabilityByAgent = useMemo(
    () =>
      Object.fromEntries(
        explainability.map((response) => [response.agentType, response]),
      ) as Partial<Record<WorkspaceAgentType, ExplainabilityResponse>>,
    [explainability],
  );

  const recommendedAction = useMemo(
    () => resolveRecommendedAction(reviewContext?.report ?? null),
    [reviewContext?.report],
  );

  const totalExecutionDuration = useMemo(
    () =>
      investigation
        ? computeTotalExecutionDuration(investigation, timelineStages)
        : null,
    [investigation, timelineStages],
  );

  async function handleRetryExecution(): Promise<void> {
    if (!investigationId) {
      return;
    }

    try {
      setIsRetryingExecution(true);
      setError(null);
      const updated = await retryInvestigationExecution(investigationId);
      setInvestigation(updated);
      await loadCommandCenter();
    } catch (caughtError) {
      setError(
        getApiErrorMessage(
          caughtError,
          "Unable to retry investigation execution.",
        ),
      );
    } finally {
      setIsRetryingExecution(false);
    }
  }

  async function handleDecision(
    action: "approve" | "reject" | "escalate" | "request-more",
  ): Promise<void> {
    if (!investigationId || !decisionReason.trim()) {
      setReviewError("Decision reason is required.");
      return;
    }

    const request = {
      decisionReason: decisionReason.trim(),
      comments: comments.trim() || undefined,
      additionalNotes: additionalNotes.trim() || undefined,
    };

    const handlers = {
      approve: approveInvestigation,
      reject: rejectInvestigation,
      escalate: escalateInvestigation,
      "request-more": requestMoreInvestigation,
    };

    try {
      setIsSubmitting(true);
      setReviewError(null);
      setSuccessMessage(null);

      if (reviewContext?.reviewSummary.reviewStatus === "NOT_STARTED") {
        await startInvestigationReview(investigationId);
      }

      const updated = await handlers[action](investigationId, request);
      setReviewContext(updated);
      setInvestigation(updated.investigation);
      setSuccessMessage("Review decision recorded.");
      await refreshReviewContext();
    } catch (caughtError) {
      setReviewError(
        getApiErrorMessage(
          caughtError,
          "Unable to record review decision.",
        ),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return (
      <PageContainer>
        <LoadingSpinner label="Loading investigation command center…" />
      </PageContainer>
    );
  }

  if (error && !investigation) {
    return (
      <PageContainer>
        <Stack spacing={layout.sectionGap}>
          <Button
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate("/investigations")}
            sx={{ alignSelf: "flex-start" }}
          >
            Back to Investigations
          </Button>
          <ErrorState
            error={error}
            fallback="Unable to load investigation command center."
            onRetry={() => void loadCommandCenter()}
          />
        </Stack>
      </PageContainer>
    );
  }

  if (!investigation) {
    return null;
  }

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate("/investigations")}
        sx={{ alignSelf: "flex-start" }}
      >
        Back to Investigations
      </Button>

      {error && (
        <ErrorState
          error={error}
          fallback="Unable to complete the requested action."
        />
      )}

      {investigation.status === "EXECUTION_FAILED" && (
        <Alert
          severity="error"
          action={
            canExecuteInvestigation ? (
              <Button
                color="inherit"
                size="small"
                disabled={isRetryingExecution}
                onClick={() => void handleRetryExecution()}
              >
                {isRetryingExecution ? "Retrying..." : "Retry Execution"}
              </Button>
            ) : undefined
          }
        >
          {investigation.executionFailureMessage
            ?? "Investigation execution failed."}
        </Alert>
      )}

      {loadWarnings.map((warning) => (
        <Alert key={warning} severity="warning">
          {warning}
        </Alert>
      ))}

      <InvestigationCaseHeader
        investigation={investigation}
        totalExecutionDuration={totalExecutionDuration}
      />

      <CommandCenterSummary
        investigation={investigation}
        timelineStages={timelineStages}
        recommendedAction={recommendedAction}
      />

      <InvestigationAssignmentPanel
        investigation={investigation}
        currentUsername={user?.username ?? null}
        canAssignInvestigation={canAssignInvestigation}
        canClaimInvestigation={canClaimInvestigation}
        canReviewInvestigation={canReviewInvestigation}
        isReadOnly={isReadOnly}
        onUpdated={loadCommandCenter}
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <CustomerRiskSummary customer={customer} />
        </Grid>
        <Grid size={{ xs: 12, lg: 7 }}>
          <TriggeringTransactionsTable
            investigation={investigation}
            transactions={transactions}
          />
        </Grid>
      </Grid>

      <SurfaceCard id="pipeline" title="Live Investigation Pipeline">
        <ExecutionTimeline stages={timelineStages} />
      </SurfaceCard>

      <Card variant="outlined">
        <Tabs
          value={activeTab}
          onChange={(_event, value: DetailTab) => setActiveTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="Agent Findings" value="findings" />
          <Tab label="Explainability" value="explainability" />
          <Tab label="Report" value="report" />
          <Tab label="Human Review" value="review" />
          <Tab label="Audit Timeline" value="audit" />
        </Tabs>
        <CardContent>
          {activeTab === "findings" && (
            <Stack id="findings" spacing={1}>
              {AGENT_TYPES.map((agentType) => (
                <AgentFindingAccordion
                  key={agentType}
                  agentType={agentType}
                  finding={findingsByAgent[agentType]}
                  explainability={explainabilityByAgent[agentType] ?? null}
                />
              ))}
            </Stack>
          )}

          {activeTab === "explainability" && (
            <Stack id="explainability" spacing={2}>
              {explainability.length === 0 ? (
                <EmptyState
                  title="No explainability data"
                  description="Explainability becomes available after agents complete their analysis."
                />
              ) : (
                explainability.map((response) => (
                  <FindingExplainabilityCard
                    key={response.findingId}
                    response={response}
                  />
                ))
              )}
            </Stack>
          )}

          {activeTab === "report" && (
            <Box id="report">
              <InvestigationReportPanel report={reviewContext?.report ?? null} />
            </Box>
          )}

          {activeTab === "review" && reviewContext && (
            <Box id="review">
              <HumanReviewPanel
                context={reviewContext}
                canReviewInvestigation={canReviewInvestigation}
                canDecideInvestigation={canDecideInvestigation}
                canRequestMoreInvestigation={canRequestMoreInvestigation}
                isReadOnly={isReadOnly}
                isSubmitting={isSubmitting}
                decisionReason={decisionReason}
                comments={comments}
                additionalNotes={additionalNotes}
                onDecisionReasonChange={setDecisionReason}
                onCommentsChange={setComments}
                onAdditionalNotesChange={setAdditionalNotes}
                onDecision={(action) => void handleDecision(action)}
                error={reviewError}
                successMessage={successMessage}
              />
            </Box>
          )}

          {activeTab === "review" && !reviewContext && (
            <EmptyState
              title="Review not available"
              description="Review context will appear once the investigation report is generated."
            />
          )}

          {activeTab === "audit" && (
            <AuditTimelinePanel timeline={reviewContext?.timeline ?? []} />
          )}
        </CardContent>
      </Card>
      </Stack>
    </PageContainer>
  );
}
