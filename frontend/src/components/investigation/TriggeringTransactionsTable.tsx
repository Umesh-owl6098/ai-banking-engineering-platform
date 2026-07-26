import {
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

import type { InvestigationCase } from "../../types/investigation";
import type { MockTransaction } from "../../types/mockBanking";

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

function formatRules(rules: string[] | null | undefined): string {
  if (!rules || rules.length === 0) {
    return "—";
  }

  return rules.map((rule) => rule.replaceAll("_", " ")).join(", ");
}

export default function TriggeringTransactionsTable({
  investigation,
  transactions,
}: {
  investigation: InvestigationCase;
  transactions: MockTransaction[];
}) {
  const triggerId = investigation.transactionId;

  return (
    <Card sx={{ height: "100%" }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Triggering Transactions
        </Typography>
        {transactions.length === 0 ? (
          <Typography color="text.secondary">
            No transactions are linked to this investigation.
          </Typography>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>Reference</TableCell>
                  <TableCell>Amount</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Channel</TableCell>
                  <TableCell>Route</TableCell>
                  <TableCell>Risk Score</TableCell>
                  <TableCell>Screening Status</TableCell>
                  <TableCell>Triggered Rules</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {transactions.map((transaction) => {
                  const isTrigger = transaction.id === triggerId;

                  return (
                    <TableRow
                      key={transaction.id}
                      sx={{
                        bgcolor: isTrigger ? "warning.light" : undefined,
                      }}
                    >
                      <TableCell>
                        {new Date(transaction.transactionDate).toLocaleString()}
                        {isTrigger && (
                          <Chip
                            label="Trigger"
                            color="warning"
                            size="small"
                            sx={{ ml: 1 }}
                          />
                        )}
                      </TableCell>
                      <TableCell>{transaction.transactionReference}</TableCell>
                      <TableCell>
                        {formatAmount(transaction.amount, transaction.currency)}
                      </TableCell>
                      <TableCell>{transaction.transactionType}</TableCell>
                      <TableCell>{transaction.channel}</TableCell>
                      <TableCell>
                        {transaction.originCountry ?? "—"} →{" "}
                        {transaction.destinationCountry ?? "—"}
                      </TableCell>
                      <TableCell>{transaction.riskScore ?? "—"}</TableCell>
                      <TableCell>
                        {isTrigger
                          ? investigation.screeningStatus?.replaceAll("_", " ") ?? "—"
                          : "—"}
                      </TableCell>
                      <TableCell>
                        {isTrigger
                          ? formatRules(investigation.screeningTriggeredRules)
                          : "—"}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
}
