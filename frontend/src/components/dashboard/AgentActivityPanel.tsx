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

import type { AgentActivitySummary } from "../../types/dashboard";
import { formatDurationMs } from "../../utils/statusBadges";

const AGENT_LABELS: Record<string, string> = {
  SUPERVISOR: "Supervisor",
  FRAUD: "Fraud",
  KYC: "KYC",
  AML: "AML",
  COMPLIANCE: "Compliance",
  REPORT: "Report",
};

export default function AgentActivityPanel({
  rows,
}: {
  rows: AgentActivitySummary[];
}) {
  return (
    <Card sx={{ height: "100%" }}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
          Agent Activity
        </Typography>
        <TableContainer sx={{ maxHeight: 320 }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>Agent</TableCell>
                <TableCell align="right">Running</TableCell>
                <TableCell align="right">Completed</TableCell>
                <TableCell align="right">Failed</TableCell>
                <TableCell align="right">Avg Duration</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.agentType} hover>
                  <TableCell>
                    {AGENT_LABELS[row.agentType] ?? row.agentType}
                  </TableCell>
                  <TableCell align="right">
                    {row.runningCount > 0 ? (
                      <Chip
                        size="small"
                        label={row.runningCount}
                        color="info"
                        variant="outlined"
                      />
                    ) : (
                      row.runningCount
                    )}
                  </TableCell>
                  <TableCell align="right">{row.completedCount}</TableCell>
                  <TableCell align="right">
                    {row.failedCount > 0 ? (
                      <Chip
                        size="small"
                        label={row.failedCount}
                        color="error"
                        variant="outlined"
                      />
                    ) : (
                      row.failedCount
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {formatDurationMs(row.averageDurationMs)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  );
}
