import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Stack,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useNavigate, useParams } from "react-router-dom";

import {
  getCustomer,
  getTransaction,
} from "../services/mockBankingService";
import type {
  MockCustomer,
  MockTransaction,
} from "../types/mockBanking";
import { getApiErrorMessage } from "../utils/apiError";

function formatAmount(
  amount: number,
  currency: string,
): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
  }).format(amount);
}

export default function TransactionDetailsPage() {
  const { transactionId } = useParams();
  const navigate = useNavigate();
  const [transaction, setTransaction] =
    useState<MockTransaction | null>(null);
  const [customer, setCustomer] =
    useState<MockCustomer | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isCurrent = true;

    async function loadTransaction(): Promise<void> {
      if (!transactionId) {
        setError("Transaction ID is required.");
        setIsLoading(false);
        return;
      }

      try {
        setIsLoading(true);
        setError(null);

        const transactionData = await getTransaction(transactionId);
        const customerData = await getCustomer(
          transactionData.customerId,
        );

        if (isCurrent) {
          setTransaction(transactionData);
          setCustomer(customerData);
        }
      } catch (caughtError) {
        if (isCurrent) {
          setError(
            getApiErrorMessage(
              caughtError,
              "Unable to load transaction details.",
            ),
          );
        }
      } finally {
        if (isCurrent) {
          setIsLoading(false);
        }
      }
    }

    void loadTransaction();

    return () => {
      isCurrent = false;
    };
  }, [transactionId]);

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

  if (error || !transaction) {
    return (
      <Stack spacing={2}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate("/transactions/suspicious")}
          sx={{ alignSelf: "flex-start" }}
        >
          Back to Suspicious Transactions
        </Button>
        <Alert severity="error">
          {error ?? "Transaction not found."}
        </Alert>
      </Stack>
    );
  }

  const details = [
    ["Amount", formatAmount(transaction.amount, transaction.currency)],
    ["Transaction Type", transaction.transactionType],
    ["Status", transaction.transactionStatus],
    ["Channel", transaction.channel],
    ["Risk Score", transaction.riskScore?.toString() ?? "—"],
    ["Origin Country", transaction.originCountry ?? "—"],
    ["Destination Country", transaction.destinationCountry ?? "—"],
    [
      "Transaction Date",
      new Date(transaction.transactionDate).toLocaleString(),
    ],
  ];

  return (
    <Stack spacing={3}>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate("/transactions/suspicious")}
        sx={{ alignSelf: "flex-start" }}
      >
        Back to Suspicious Transactions
      </Button>

      <Box>
        <Typography variant="h4" gutterBottom>
          {transaction.transactionReference}
        </Typography>
        <Stack direction="row" spacing={1}>
          <Chip
            label={transaction.flagged ? "FLAGGED" : "UNFLAGGED"}
            color={transaction.flagged ? "error" : "default"}
          />
          <Chip label={transaction.transactionStatus} />
        </Stack>
      </Box>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Transaction Details
              </Typography>
              <Grid container spacing={2}>
                {details.map(([label, value]) => (
                  <Grid key={label} size={{ xs: 12, sm: 6 }}>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                    >
                      {label}
                    </Typography>
                    <Typography>{value}</Typography>
                  </Grid>
                ))}
              </Grid>
              {transaction.description && (
                <>
                  <Divider sx={{ my: 2 }} />
                  <Typography
                    variant="caption"
                    color="text.secondary"
                  >
                    Description
                  </Typography>
                  <Typography>{transaction.description}</Typography>
                </>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Customer Summary
              </Typography>
              {customer && (
                <Stack spacing={1}>
                  <Typography>{customer.fullName}</Typography>
                  <Typography color="text.secondary">
                    Account: {customer.accountNumber}
                  </Typography>
                  <Button
                    size="small"
                    onClick={() =>
                      navigate(`/customers/${customer.id}`)
                    }
                    sx={{ alignSelf: "flex-start" }}
                  >
                    View Customer
                  </Button>
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
