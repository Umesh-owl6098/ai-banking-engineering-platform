import {
  Alert,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Typography,
} from "@mui/material";

import type { InvestigationReviewContext } from "../../types/investigation";

export default function HumanReviewPanel({
  context,
  canReviewInvestigation,
  canDecideInvestigation,
  canRequestMoreInvestigation,
  isReadOnly,
  isSubmitting,
  decisionReason,
  comments,
  additionalNotes,
  onDecisionReasonChange,
  onCommentsChange,
  onAdditionalNotesChange,
  onDecision,
  error,
  successMessage,
}: {
  context: InvestigationReviewContext;
  canReviewInvestigation: boolean;
  canDecideInvestigation: boolean;
  canRequestMoreInvestigation: boolean;
  isReadOnly: boolean;
  isSubmitting: boolean;
  decisionReason: string;
  comments: string;
  additionalNotes: string;
  onDecisionReasonChange: (value: string) => void;
  onCommentsChange: (value: string) => void;
  onAdditionalNotesChange: (value: string) => void;
  onDecision: (
    action: "approve" | "reject" | "escalate" | "request-more",
  ) => void;
  error: string | null;
  successMessage: string | null;
}) {
  const { investigation, reviewSummary } = context;
  const canDecide =
    canDecideInvestigation
    && (investigation.status === "AWAITING_REVIEW"
      || investigation.status === "IN_REVIEW")
    && !isSubmitting;
  const canRequestMore =
    canRequestMoreInvestigation
    && (investigation.status === "AWAITING_REVIEW"
      || investigation.status === "IN_REVIEW")
    && !isSubmitting;
  const showDecisionForm = canReviewInvestigation && !isReadOnly;

  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Typography variant="h6">Human Review</Typography>
          <Typography color="text.secondary">
            Review status: {reviewSummary.reviewStatus}
            {reviewSummary.decision
              ? ` · Decision: ${reviewSummary.decision}`
              : ""}
          </Typography>

          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}

          {showDecisionForm ? (
            <>
              <TextField
                label="Decision Reason"
                value={decisionReason}
                onChange={(event) => onDecisionReasonChange(event.target.value)}
                required
                fullWidth
                multiline
                minRows={2}
              />
              <TextField
                label="Comments"
                value={comments}
                onChange={(event) => onCommentsChange(event.target.value)}
                fullWidth
                multiline
                minRows={2}
              />
              <TextField
                label="Additional Notes"
                value={additionalNotes}
                onChange={(event) =>
                  onAdditionalNotesChange(event.target.value)
                }
                fullWidth
                multiline
                minRows={2}
              />
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                {canDecideInvestigation && (
                  <>
                    <Button
                      variant="contained"
                      color="success"
                      disabled={!canDecide}
                      onClick={() => onDecision("approve")}
                    >
                      Approve
                    </Button>
                    <Button
                      variant="contained"
                      color="error"
                      disabled={!canDecide}
                      onClick={() => onDecision("reject")}
                    >
                      Reject
                    </Button>
                    <Button
                      variant="contained"
                      color="warning"
                      disabled={!canDecide}
                      onClick={() => onDecision("escalate")}
                    >
                      Escalate
                    </Button>
                  </>
                )}
                {canRequestMoreInvestigation && (
                  <Button
                    variant="outlined"
                    disabled={!canRequestMore}
                    onClick={() => onDecision("request-more")}
                  >
                    Request More Investigation
                  </Button>
                )}
              </Stack>
            </>
          ) : isReadOnly ? (
            <Alert severity="info">
              Your account has read-only access. Review decisions cannot be
              submitted.
            </Alert>
          ) : investigation.status !== "AWAITING_REVIEW" ? (
            <Alert severity="info">
              Human review actions become available when the investigation
              reaches AWAITING_REVIEW status.
            </Alert>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  );
}
