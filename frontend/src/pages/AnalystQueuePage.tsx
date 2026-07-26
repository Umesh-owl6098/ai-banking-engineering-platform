import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
} from "@mui/material";
import { useNavigate } from "react-router-dom";

import AnalystQueueTable from "../components/analyst/AnalystQueueTable";
import ErrorState from "../components/ui/ErrorState";
import { LoadingSpinner } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import PageHeader from "../components/ui/PageHeader";
import { useAuth } from "../hooks/useAuth";
import {
  assignInvestigation,
  claimInvestigation,
  getAnalystQueue,
  getAssignableAnalysts,
} from "../services/analystQueueService";
import { INVESTIGATION_PROJECT_ID } from "../services/investigationService";
import type { AnalystQueueItem, AssignableAnalyst } from "../types/analystQueue";
import { getApiErrorMessageForStatus } from "../utils/apiError";
import { layout } from "../theme/tokens";

type QueueTab = "my-queue" | "unassigned" | "in-review" | "escalated";

export default function AnalystQueuePage() {
  const navigate = useNavigate();
  const {
    user,
    isLoading: isAuthLoading,
    canAssignInvestigation,
    canClaimInvestigation,
    isReadOnly,
  } = useAuth();
  const [activeTab, setActiveTab] = useState<QueueTab>("my-queue");
  const [queue, setQueue] = useState<Awaited<ReturnType<typeof getAnalystQueue>> | null>(null);
  const [analysts, setAnalysts] = useState<AssignableAnalyst[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [assignTarget, setAssignTarget] = useState<AnalystQueueItem | null>(null);
  const [assigneeUsername, setAssigneeUsername] = useState("");
  const [notes, setNotes] = useState("");

  const loadQueue = useCallback(async () => {
    try {
      setLoadError(null);
      const response = await getAnalystQueue(INVESTIGATION_PROJECT_ID);
      setQueue(response);
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

    void loadQueue();
    if (canAssignInvestigation) {
      void getAssignableAnalysts()
        .then(setAnalysts)
        .catch(() => setAnalysts([]));
    }
  }, [isAuthLoading, loadQueue, canAssignInvestigation]);

  const rows = useMemo(() => {
    if (!queue) {
      return [];
    }

    switch (activeTab) {
      case "my-queue":
        return queue.myQueue;
      case "unassigned":
        return queue.unassigned;
      case "in-review":
        return queue.inReview;
      case "escalated":
        return queue.escalated;
      default:
        return [];
    }
  }, [activeTab, queue]);

  async function handleClaim(item: AnalystQueueItem): Promise<void> {
    try {
      setActionError(null);
      await claimInvestigation(item.investigationId);
      await loadQueue();
    } catch (caughtError) {
      setActionError(
        getApiErrorMessageForStatus(caughtError, "Unable to claim investigation."),
      );
    }
  }

  async function handleAssignSave(): Promise<void> {
    if (!assignTarget || !assigneeUsername) {
      return;
    }

    try {
      setActionError(null);
      await assignInvestigation(assignTarget.investigationId, {
        assigneeUsername,
        notes: notes.trim() || undefined,
      });
      setAssignTarget(null);
      setAssigneeUsername("");
      setNotes("");
      await loadQueue();
    } catch (caughtError) {
      setActionError(
        getApiErrorMessageForStatus(caughtError, "Unable to assign investigation."),
      );
    }
  }

  if (isLoading) {
    return (
      <PageContainer>
        <LoadingSpinner label="Loading analyst queue…" />
      </PageContainer>
    );
  }

  if (loadError != null && !queue) {
    return (
      <PageContainer>
        <ErrorState
          error={loadError}
          fallback="Unable to load Analyst Queue."
          forbiddenMessage="You don't have permission to access Analyst Queue."
          onRetry={() => {
            setIsLoading(true);
            void loadQueue();
          }}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
        <PageHeader
          title="Analyst Review Queue"
          description="Claim, assign, and track investigations awaiting analyst review."
          actions={
            <Button variant="outlined" size="small" onClick={() => void loadQueue()}>
              Refresh
            </Button>
          }
        />

        {actionError && (
          <ErrorState error={actionError} fallback="Unable to perform queue action." />
        )}
        {loadError != null && (
          <ErrorState
            error={loadError}
            fallback="Unable to load Analyst Queue."
            forbiddenMessage="You don't have permission to access Analyst Queue."
            onRetry={() => void loadQueue()}
          />
        )}

        <Tabs
          value={activeTab}
          onChange={(_event, value: QueueTab) => setActiveTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label={`My Queue (${queue?.myQueue.length ?? 0})`} value="my-queue" />
          <Tab label={`Unassigned (${queue?.unassigned.length ?? 0})`} value="unassigned" />
          <Tab label={`In Review (${queue?.inReview.length ?? 0})`} value="in-review" />
          <Tab label={`Escalated (${queue?.escalated.length ?? 0})`} value="escalated" />
        </Tabs>

        <AnalystQueueTable
          rows={rows}
          currentUsername={user?.username ?? null}
          canAssignInvestigation={canAssignInvestigation}
          canClaimInvestigation={canClaimInvestigation && !isReadOnly}
          showAssignAction={activeTab === "unassigned" || activeTab === "escalated"}
          onOpen={(investigationId) => navigate(`/investigations/${investigationId}`)}
          onClaim={(item) => void handleClaim(item)}
          onAssign={(item) => {
            setAssignTarget(item);
            setAssigneeUsername("");
            setNotes("");
          }}
        />
      </Stack>

      <Dialog
        open={assignTarget != null}
        onClose={() => setAssignTarget(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Assign Investigation</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="queue-assignee-label">Analyst</InputLabel>
              <Select
                labelId="queue-assignee-label"
                label="Analyst"
                value={assigneeUsername}
                onChange={(event) => setAssigneeUsername(event.target.value)}
              >
                {analysts.map((analyst) => (
                  <MenuItem key={analyst.id} value={analyst.username}>
                    {analyst.username}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Notes"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              multiline
              minRows={2}
              fullWidth
              size="small"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAssignTarget(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!assigneeUsername}
            onClick={() => void handleAssignSave()}
          >
            Assign
          </Button>
        </DialogActions>
      </Dialog>
    </PageContainer>
  );
}
