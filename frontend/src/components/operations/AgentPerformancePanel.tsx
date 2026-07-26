import {
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import SurfaceCard from "../ui/SurfaceCard";
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

export default function AgentPerformancePanel({
  rows,
}: {
  rows: AgentActivitySummary[];
}) {
  return (
    <SurfaceCard title="AI Agent Performance">
      <TableContainer sx={{ maxHeight: 360, overflowX: "auto" }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell>Agent</TableCell>
              <TableCell align="right">Total Executions</TableCell>
              <TableCell align="right">Avg Time</TableCell>
              <TableCell align="right">Success</TableCell>
              <TableCell align="right">Failure</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => {
              const totalExecutions =
                row.runningCount + row.completedCount + row.failedCount;

              return (
                <TableRow key={row.agentType} hover>
                  <TableCell>
                    {AGENT_LABELS[row.agentType] ?? row.agentType}
                  </TableCell>
                  <TableCell align="right">{totalExecutions}</TableCell>
                  <TableCell align="right">
                    {formatDurationMs(row.averageDurationMs)}
                  </TableCell>
                  <TableCell align="right">
                    {row.completedCount > 0 ? (
                      <Chip
                        size="small"
                        color="success"
                        label={row.completedCount}
                        variant="outlined"
                      />
                    ) : (
                      row.completedCount
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {row.failedCount > 0 ? (
                      <Chip
                        size="small"
                        color="error"
                        label={row.failedCount}
                        variant="outlined"
                      />
                    ) : (
                      row.failedCount
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>
                  <Typography color="text.secondary" variant="body2">
                    No agent execution data available yet.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </SurfaceCard>
  );
}
