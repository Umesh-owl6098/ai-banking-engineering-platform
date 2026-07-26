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
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useNavigate, useParams } from "react-router-dom";

import {
  getCustomer,
  getCustomerTransactions,
} from "../services/mockBankingService";
import type {
  MockCustomer,
  MockTransaction,
} from "../types/mockBanking";

function formatAmount(
  amount: number,
  currency: string,
): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
  }).format(amount);
}

export default function CustomerDetailsPage() {
  const { customerId } = useParams();
  const navigate = useNavigate();
  const [customer, setCustomer] =
    useState<MockCustomer | null>(null);
  const [transactions, setTransactions] =
    useState<MockTransaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isCurrent = true;

    async function loadCustomer(): Promise<void> {
      if (!customerId) {
        setError("Customer ID is required.");
        setIsLoading(false);
        return;
      }

      try {
        setIsLoading(true);
        setError(null);

        const [customerData, transactionData] =
          await Promise.all([
            getCustomer(customerId),
            getCustomerTransactions(customerId),
          ]);

        if (isCurrent) {
          setCustomer(customerData);
          setTransactions(transactionData);
        }
      } catch (caughtError) {
        if (isCurrent) {
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : "Unable to load customer details.",
          );
        }
      } finally {
        if (isCurrent) {
          setIsLoading(false);
        }
      }
    }

    void loadCustomer();

    return () => {
      isCurrent = false;
    };
  }, [customerId]);

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

  if (error || !customer) {
    return (
      <Stack spacing={2}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate("/customers")}
          sx={{ alignSelf: "flex-start" }}
        >
          Back to Customers
        </Button>
        <Alert severity="error">
          {error ?? "Customer not found."}
        </Alert>
      </Stack>
    );
  }

  const details = [
    ["Account Number", customer.accountNumber],
    ["Account Status", customer.accountStatus],
    ["Country of Residence", customer.countryOfResidence],
    ["Nationality", customer.nationality ?? "Not provided"],
    ["Occupation", customer.occupation ?? "Not provided"],
    ["Email", customer.email ?? "Not provided"],
    ["Source of Funds", customer.sourceOfFunds ?? "Not provided"],
    ["Account Opened", customer.accountOpened],
  ];

  return (
    <Stack spacing={3}>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate("/customers")}
        sx={{ alignSelf: "flex-start" }}
      >
        Back to Customers
      </Button>

      <Button
        variant="contained"
        onClick={() =>
          navigate("/investigations/new", {
            state: { customerId: customer.id },
          })
        }
        sx={{ alignSelf: "flex-start", mt: -2 }}
      >
        Create Investigation
      </Button>

      <Box>
        <Typography variant="h4" gutterBottom>
          {customer.fullName}
        </Typography>
        <Stack
          direction="row"
          spacing={1}
          useFlexGap
          sx={{ flexWrap: "wrap" }}
        >
          <Chip label={`KYC: ${customer.kycStatus}`} />
          <Chip
            label={`Risk: ${customer.riskRating}`}
            color={
              customer.riskRating === "HIGH"
                ? "error"
                : customer.riskRating === "MEDIUM"
                  ? "warning"
                  : "success"
            }
          />
          <Chip label={`PEP: ${customer.pepStatus}`} />
        </Stack>
      </Box>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Customer Information
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
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                KYC & Risk
              </Typography>
              <Stack spacing={2}>
                <Box>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                  >
                    KYC Status
                  </Typography>
                  <Typography>{customer.kycStatus}</Typography>
                </Box>
                <Divider />
                <Box>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                  >
                    Risk Rating
                  </Typography>
                  <Typography>{customer.riskRating}</Typography>
                </Box>
                <Divider />
                <Box>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                  >
                    PEP Status
                  </Typography>
                  <Typography>{customer.pepStatus}</Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Box>
        <Typography variant="h5" gutterBottom>
          Customer Transactions
        </Typography>
        <Paper variant="outlined">
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Reference</TableCell>
                  <TableCell>Amount</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Channel</TableCell>
                  <TableCell>Risk Score</TableCell>
                  <TableCell>Date</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {transactions.map((transaction) => (
                  <TableRow
                    key={transaction.id}
                    sx={
                      transaction.flagged
                        ? {
                            bgcolor:
                              "rgba(211,47,47,0.08)",
                          }
                        : undefined
                    }
                  >
                    <TableCell>
                      {transaction.transactionReference}
                    </TableCell>
                    <TableCell>
                      {formatAmount(
                        transaction.amount,
                        transaction.currency,
                      )}
                    </TableCell>
                    <TableCell>
                      {transaction.transactionType}
                    </TableCell>
                    <TableCell>{transaction.channel}</TableCell>
                    <TableCell>
                      {transaction.riskScore ?? "—"}
                    </TableCell>
                    <TableCell>
                      {new Date(
                        transaction.transactionDate,
                      ).toLocaleString()}
                    </TableCell>
                  </TableRow>
                ))}
                {transactions.length === 0 && (
                  <TableRow>
                    <TableCell align="center" colSpan={6}>
                      No transactions found for this customer.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      </Box>
    </Stack>
  );
}
