import {
  Button,
  Card,
  CardContent,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useNavigate } from "react-router-dom";

import type { RecentScreenedTransactionRow } from "../../types/dashboard";
import {
  formatCurrency,
  screeningStatusChipColor,
} from "../../utils/statusBadges";

export default function RecentTransactionsPanel({
  transactions,
}: {
  transactions: RecentScreenedTransactionRow[];
}) {
  const navigate = useNavigate();

  return (
    <Card sx={{ height: "100%" }}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
          Recent Transactions
        </Typography>
        <TableContainer sx={{ maxHeight: 360, overflow: "auto" }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>Time</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Amount</TableCell>
                <TableCell>Route</TableCell>
                <TableCell>Screening</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell align="right">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {transactions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography variant="body2" color="text.secondary">
                      Screened transactions will appear here in real time.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                transactions.map((transaction) => (
                  <TableRow key={transaction.transactionId} hover>
                    <TableCell>
                      {transaction.screenedAt
                        ? new Date(transaction.screenedAt).toLocaleTimeString()
                        : "—"}
                    </TableCell>
                    <TableCell>{transaction.customerName}</TableCell>
                    <TableCell>
                      {formatCurrency(
                        transaction.amount,
                        transaction.currency,
                      )}
                    </TableCell>
                    <TableCell>{transaction.route}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={transaction.screeningStatus.replaceAll("_", " ")}
                        color={screeningStatusChipColor(
                          transaction.screeningStatus,
                        )}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      {transaction.triggeredRules.length > 0
                        ? transaction.triggeredRules.join(", ")
                        : transaction.screeningReason}
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        disabled={!transaction.investigationId}
                        endIcon={<OpenInNewIcon />}
                        onClick={() => {
                          if (transaction.investigationId) {
                            navigate(
                              `/investigations/${transaction.investigationId}`,
                            );
                          }
                        }}
                      >
                        Open
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  );
}
