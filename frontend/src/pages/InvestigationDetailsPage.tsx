import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useNavigate, useParams } from "react-router-dom";

import { useAuth } from "../hooks/useAuth";
import {
  getInvestigation,
  updateInvestigationStatus,
} from "../services/investigationService";
import {
  getCustomer,
  getTransaction,
} from "../services/mockBankingService";
import type { InvestigationCase } from "../types/investigation";
import type {
  MockCustomer,
  MockTransaction,
} from "../types/mockBanking";
import { getApiErrorMessage } from "../utils/apiError";

const validNextStatuses: Record<string, string[]> = {
  OPEN: ["INVESTIGATING"],
  INVESTIGATING: ["AWAITING_REVIEW"],
  AWAITING_REVIEW: ["APPROVED", "REJECTED", "ESCALATED"],
  ESCALATED: ["INVESTIGATING"],
  APPROVED: ["CLOSED"],
  REJECTED: ["CLOSED"],
  CLOSED: [],
};

function formatAmount(
  amount: number,
  currency: string,
): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
  }).format(amount);
}

export default function InvestigationDetailsPage() {
  const { investigationId } = useParams();
  const navigate = useNavigate();
  const { canUpdateInvestigationStatus } = useAuth();
  const [investigation, setInvestigation] =
    useState<InvestigationCase | null>(null);
  const [customer, setCustomer] =
    useState<MockCustomer | null>(null);
  const [transaction, setTransaction] =
    useState<MockTransaction | null>(null);
  const [nextStatus, setNextStatus] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(
    null,
  );

  useEffect(() => {
    let isCurrent = true;

    async function loadInvestigation(): Promise<void> {
      if (!investigationId) {
        setError("Investigation ID is required.");
        setIsLoading(false);
        return;
      }

      try {
        setIsLoading(true);
        setError(null);

        const caseData = await getInvestigation(investigationId);
        const [customerData, transactionData] = await Promise.all([
          caseData.customerId
            ? getCustomer(caseData.customerId)
            : Promise.resolve(null),
          caseData.transactionId
            ? getTransaction(caseData.transactionId)
            : Promise.resolve(null),
        ]);

        if (isCurrent) {
          setInvestigation(caseData);
          setCustomer(customerData);
          setTransaction(transactionData);
        }
      } catch (caughtError) {
        if (isCurrent) {
          setError(
            getApiErrorMessage(
              caughtError,
              "Unable to load investigation details.",
            ),
          );
        }
      } finally {
        if (isCurrent) {
          setIsLoading(false);
        }
      }
    }

    void loadInvestigation();

    return () => {
      isCurrent = false;
    };
  }, [investigationId]);

  const allowedStatuses = useMemo(
    () =>
      investigation
        ? validNextStatuses[investigation.status] ?? []
        : [],
    [investigation],
  );

  async function handleStatusUpdate(): Promise<void> {
    if (!investigation || !nextStatus) {
      return;
    }

    try {
      setIsUpdatingStatus(true);
      setError(null);
      setSuccessMessage(null);

      const updated = await updateInvestigationStatus(
        investigation.id,
        { status: nextStatus },
      );
      const refreshed = await getInvestigation(updated.id);

      setInvestigation(refreshed);
      setNextStatus("");
      setSuccessMessage("Investigation status updated.");
    } catch (caughtError) {
      setError(
        getApiErrorMessage(
          caughtError,
          "Unable to update investigation status.",
        ),
      );
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  if (isLoading) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "center",
          py: 8,
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  if (error && !investigation) {
    return (
      <Stack spacing={2}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate("/investigations")}
          sx={{ alignSelf: "flex-start" }}
        >
          Back to Investigations
        </Button>
        <Alert severity="error">{error}</Alert>
      </Stack>
    );
  }

  if (!investigation) {
    return null;
  }

  return (
    <Stack spacing={3}>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate("/investigations")}
        sx={{ alignSelf: "flex-start" }}
      >
        Back to Investigations
      </Button>

      <Box>
        <Typography variant="h4" gutterBottom>
          {investigation.title}
        </Typography>
        <Stack direction="row" spacing={1}>
          <Chip label={investigation.status} />
          <Chip label={`${investigation.priority} priority`} />
          <Chip label={investigation.caseType} variant="outlined" />
        </Stack>
      </Box>

      {error && <Alert severity="error">{error}</Alert>}
      {successMessage && (
        <Alert severity="success">{successMessage}</Alert>
      )}

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Case Description
          </Typography>
          <Typography sx={{ whiteSpace: "pre-wrap" }}>
            {investigation.description}
          </Typography>
          <Divider sx={{ my: 2 }} />
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Typography variant="caption" color="text.secondary">
                Created
              </Typography>
              <Typography>
                {new Date(
                  investigation.createdAt,
                ).toLocaleString()}
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Typography variant="caption" color="text.secondary">
                Last Updated
              </Typography>
              <Typography>
                {new Date(
                  investigation.updatedAt,
                ).toLocaleString()}
              </Typography>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {canUpdateInvestigationStatus && allowedStatuses.length > 0 && (
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Update Status
            </Typography>
            <Stack
              direction={{ xs: "column", sm: "row" }}
              spacing={2}
              sx={{ alignItems: { sm: "center" } }}
            >
              <FormControl size="small" sx={{ minWidth: 240 }}>
                <InputLabel id="next-status-label">
                  Next Status
                </InputLabel>
                <Select
                  labelId="next-status-label"
                  label="Next Status"
                  value={nextStatus}
                  onChange={(event) =>
                    setNextStatus(event.target.value)
                  }
                >
                  {allowedStatuses.map((status) => (
                    <MenuItem key={status} value={status}>
                      {status}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button
                variant="contained"
                disabled={!nextStatus || isUpdatingStatus}
                onClick={() => void handleStatusUpdate()}
              >
                {isUpdatingStatus ? "Updating..." : "Update Status"}
              </Button>
            </Stack>
          </CardContent>
        </Card>
      )}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Customer Summary
              </Typography>
              {customer ? (
                <Stack spacing={1}>
                  <Typography>{customer.fullName}</Typography>
                  <Typography color="text.secondary">
                    Account: {customer.accountNumber}
                  </Typography>
                  <Typography color="text.secondary">
                    KYC: {customer.kycStatus} · Risk:{" "}
                    {customer.riskRating}
                  </Typography>
                </Stack>
              ) : (
                <Typography color="text.secondary">
                  No customer is linked to this investigation.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Transaction Summary
              </Typography>
              {transaction ? (
                <Stack spacing={1}>
                  <Typography>
                    {transaction.transactionReference}
                  </Typography>
                  <Typography color="text.secondary">
                    {formatAmount(
                      transaction.amount,
                      transaction.currency,
                    )}{" "}
                    · {transaction.channel}
                  </Typography>
                  <Typography color="text.secondary">
                    Risk score: {transaction.riskScore ?? "—"}
                  </Typography>
                </Stack>
              ) : (
                <Typography color="text.secondary">
                  No transaction is linked to this investigation.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
