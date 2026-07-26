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
  TextField,
} from "@mui/material";
import { useEffect, useState } from "react";

import {
  assignInvestigation,
  claimInvestigation,
  getAssignableAnalysts,
  unassignInvestigation,
} from "../../services/analystQueueService";
import { startInvestigationReview } from "../../services/investigationService";
import type { AssignableAnalyst } from "../../types/analystQueue";
import type { InvestigationCase } from "../../types/investigation";
import { getApiErrorMessageForStatus } from "../../utils/apiError";
import SurfaceCard from "../ui/SurfaceCard";
import SummaryField from "./SummaryField";

export default function InvestigationAssignmentPanel({
  investigation,
  currentUsername,
  canAssignInvestigation,
  canClaimInvestigation,
  canReviewInvestigation,
  isReadOnly,
  onUpdated,
}: {
  investigation: InvestigationCase;
  currentUsername: string | null;
  canAssignInvestigation: boolean;
  canClaimInvestigation: boolean;
  canReviewInvestigation: boolean;
  isReadOnly: boolean;
  onUpdated: () => void | Promise<void>;
}) {
  const [analysts, setAnalysts] = useState<AssignableAnalyst[]>([]);
  const [assignOpen, setAssignOpen] = useState(false);
  const [assigneeUsername, setAssigneeUsername] = useState("");
  const [notes, setNotes] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canAssignInvestigation) {
      return;
    }

    void getAssignableAnalysts()
      .then(setAnalysts)
      .catch(() => setAnalysts([]));
  }, [canAssignInvestigation]);

  const isAssignedToCurrentUser =
    investigation.assignedAnalystUsername === currentUsername;
  const canClaim =
    canClaimInvestigation
    && !isReadOnly
    && investigation.status === "AWAITING_REVIEW"
    && !investigation.assignedAnalystId;
  const canStartReview =
    canReviewInvestigation
    && !isReadOnly
    && investigation.status === "ASSIGNED"
    && (isAssignedToCurrentUser || canAssignInvestigation);

  async function runAction(action: () => Promise<unknown>): Promise<void> {
    try {
      setIsSubmitting(true);
      setError(null);
      await action();
      await onUpdated();
    } catch (caughtError) {
      setError(
        getApiErrorMessageForStatus(
          caughtError,
          "Unable to update investigation assignment.",
        ),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <SurfaceCard title="Case Assignment">
        <Stack spacing={2}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
            <SummaryField
              label="Assigned Analyst"
              value={investigation.assignedAnalystUsername ?? "Unassigned"}
            />
            <SummaryField
              label="Assigned At"
              value={
                investigation.assignedAt
                  ? new Date(investigation.assignedAt).toLocaleString()
                  : "—"
              }
            />
            <SummaryField
              label="Review Started"
              value={
                investigation.reviewStartedAt
                  ? new Date(investigation.reviewStartedAt).toLocaleString()
                  : "—"
              }
            />
          </Stack>

          {investigation.assignmentNotes && (
            <SummaryField
              label="Assignment Notes"
              value={investigation.assignmentNotes}
            />
          )}

          {error && (
            <SummaryField label="Assignment Error" value={error} />
          )}

          {!isReadOnly && (
            <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
              {canClaim && (
                <Button
                  variant="contained"
                  size="small"
                  disabled={isSubmitting}
                  onClick={() =>
                    void runAction(() => claimInvestigation(investigation.id))
                  }
                >
                  Claim Case
                </Button>
              )}
              {canStartReview && (
                <Button
                  variant="outlined"
                  size="small"
                  disabled={isSubmitting}
                  onClick={() =>
                    void runAction(() => startInvestigationReview(investigation.id))
                  }
                >
                  Start Review
                </Button>
              )}
              {canAssignInvestigation && (
                <Button
                  variant="outlined"
                  size="small"
                  disabled={isSubmitting}
                  onClick={() => setAssignOpen(true)}
                >
                  {investigation.assignedAnalystId ? "Reassign" : "Assign"}
                </Button>
              )}
              {canAssignInvestigation && investigation.assignedAnalystId && (
                <Button
                  variant="text"
                  size="small"
                  color="warning"
                  disabled={isSubmitting || investigation.status === "IN_REVIEW"}
                  onClick={() =>
                    void runAction(() => unassignInvestigation(investigation.id))
                  }
                >
                  Unassign
                </Button>
              )}
            </Stack>
          )}
        </Stack>
      </SurfaceCard>

      <Dialog open={assignOpen} onClose={() => setAssignOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>
          {investigation.assignedAnalystId ? "Reassign Investigation" : "Assign Investigation"}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="assignee-label">Analyst</InputLabel>
              <Select
                labelId="assignee-label"
                label="Analyst"
                value={assigneeUsername}
                onChange={(event) => setAssigneeUsername(event.target.value)}
              >
                {analysts.map((analyst) => (
                  <MenuItem key={analyst.id} value={analyst.username}>
                    {analyst.username} ({analyst.role.replaceAll("_", " ")})
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
          <Button onClick={() => setAssignOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!assigneeUsername || isSubmitting}
            onClick={() =>
              void runAction(async () => {
                await assignInvestigation(investigation.id, {
                  assigneeUsername,
                  notes: notes.trim() || undefined,
                });
                setAssignOpen(false);
                setNotes("");
                setAssigneeUsername("");
              })
            }
          >
            Save Assignment
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
