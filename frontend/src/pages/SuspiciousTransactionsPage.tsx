import { useEffect, useState } from "react";
import {
  Button,
  Chip,
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
import { useNavigate } from "react-router-dom";

import EmptyState from "../components/ui/EmptyState";
import ErrorState from "../components/ui/ErrorState";
import { TableSkeleton } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import PageHeader from "../components/ui/PageHeader";
import TruncatedText from "../components/ui/TruncatedText";
import { getFlaggedTransactions } from "../services/mockBankingService";
import { layout, table } from "../theme/tokens";
import type { MockTransaction } from "../types/mockBanking";
import { formatCurrency } from "../utils/statusBadges";

export default function SuspiciousTransactionsPage() {
  const navigate = useNavigate();
  const [transactions, setTransactions] = useState<MockTransaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  async function loadTransactions(): Promise<void> {
    try {
      setIsLoading(true);
      setError(null);
      const data = await getFlaggedTransactions();
      setTransactions(data);
    } catch (caughtError) {
      setError(caughtError);
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadTransactions();
  }, []);

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
        <PageHeader
          title="Suspicious Transactions"
          description="Flagged transactions ordered by most recent activity."
        />

        {error != null && (
          <ErrorState
            error={error}
            fallback="Unable to load suspicious transactions."
            onRetry={() => void loadTransactions()}
          />
        )}

        <Paper variant="outlined" sx={{ overflow: "hidden" }}>
          {isLoading ? (
            <TableSkeleton rows={6} />
          ) : (
            <TableContainer sx={{ maxWidth: "100%", overflowX: "auto" }}>
              <Table size={table.size} stickyHeader={table.stickyHeader}>
                <TableHead>
                  <TableRow>
                    <TableCell>Reference</TableCell>
                    <TableCell>Amount</TableCell>
                    <TableCell>Channel</TableCell>
                    <TableCell>Risk Score</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {transactions.map((transaction) => (
                    <TableRow
                      key={transaction.id}
                      hover
                      sx={{ bgcolor: "error.50" }}
                    >
                      <TableCell sx={{ maxWidth: 200 }}>
                        <Stack spacing={0.25}>
                          <TruncatedText
                            value={transaction.transactionReference}
                            maxWidth={180}
                            monospace
                            copyable
                          />
                          <Typography variant="caption" color="text.secondary">
                            {transaction.transactionType}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        {formatCurrency(
                          transaction.amount,
                          transaction.currency,
                        )}
                      </TableCell>
                      <TableCell>{transaction.channel}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          color={
                            (transaction.riskScore ?? 0) >= 85
                              ? "error"
                              : "warning"
                          }
                          label={transaction.riskScore ?? "—"}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip size="small" color="error" label="Flagged" />
                      </TableCell>
                      <TableCell sx={{ whiteSpace: "nowrap" }}>
                        {new Date(transaction.transactionDate).toLocaleString()}
                      </TableCell>
                      <TableCell align="right">
                        <Stack
                          direction="row"
                          spacing={0.75}
                          sx={{ justifyContent: "flex-end" }}
                        >
                          <Button
                            size="small"
                            variant="text"
                            onClick={() =>
                              navigate(`/transactions/${transaction.id}`)
                            }
                          >
                            View
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() =>
                              navigate("/investigations/new", {
                                state: {
                                  customerId: transaction.customerId,
                                  transactionId: transaction.id,
                                },
                              })
                            }
                          >
                            Investigate
                          </Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                  {transactions.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7}>
                        <EmptyState
                          title="No flagged transactions"
                          description="No suspicious transactions are currently flagged in the system."
                        />
                      </TableCell>
                    </TableRow>
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
