import {
  Button,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from "@mui/material";

import EmptyState from "../ui/EmptyState";
import StatusChip from "../ui/StatusChip";
import SurfaceCard from "../ui/SurfaceCard";
import type { AnalystQueueItem } from "../../types/analystQueue";
import { formatDurationMs } from "../../utils/statusBadges";

export default function AnalystQueueTable({
  rows,
  currentUsername,
  canAssignInvestigation,
  canClaimInvestigation,
  showAssignAction,
  onOpen,
  onClaim,
  onAssign,
}: {
  rows: AnalystQueueItem[];
  currentUsername: string | null;
  canAssignInvestigation: boolean;
  canClaimInvestigation: boolean;
  showAssignAction: boolean;
  onOpen: (investigationId: string) => void;
  onClaim: (item: AnalystQueueItem) => void;
  onAssign: (item: AnalystQueueItem) => void;
}) {
  return (
    <SurfaceCard>
      {rows.length === 0 ? (
        <EmptyState
          title="No cases in this queue"
          description="Investigations will appear here when they are ready for analyst review."
        />
      ) : (
        <TableContainer sx={{ overflowX: "auto" }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>Reference</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Severity</TableCell>
                <TableCell>Trigger</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Assigned Analyst</TableCell>
                <TableCell>Waiting</TableCell>
                <TableCell>Assigned</TableCell>
                <TableCell>Review Started</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => {
                const isMine = row.assignedAnalystUsername === currentUsername;
                const canClaimRow =
                  canClaimInvestigation
                  && row.status === "AWAITING_REVIEW"
                  && !row.assignedAnalystUsername;

                return (
                  <TableRow key={row.investigationId} hover selected={isMine}>
                    <TableCell>{row.reference}</TableCell>
                    <TableCell>{row.customerName}</TableCell>
                    <TableCell>
                      <StatusChip kind="severity" value={row.severity} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 220 }}>{row.triggerReason}</TableCell>
                    <TableCell>
                      <StatusChip kind="investigation" value={row.status} />
                    </TableCell>
                    <TableCell>
                      {row.assignedAnalystUsername ? (
                        <Chip
                          size="small"
                          label={row.assignedAnalystUsername}
                          color={isMine ? "primary" : "default"}
                          variant={isMine ? "filled" : "outlined"}
                        />
                      ) : (
                        "Unassigned"
                      )}
                    </TableCell>
                    <TableCell>{formatDurationMs(row.waitingDurationMs)}</TableCell>
                    <TableCell>
                      {row.assignedAt
                        ? new Date(row.assignedAt).toLocaleString()
                        : "—"}
                    </TableCell>
                    <TableCell>
                      {row.reviewStartedAt
                        ? new Date(row.reviewStartedAt).toLocaleString()
                        : "—"}
                    </TableCell>
                    <TableCell align="right">
                      <Stack
                        direction="row"
                        spacing={0.75}
                        sx={{ justifyContent: "flex-end" }}
                      >
                        {canClaimRow && (
                          <Button size="small" onClick={() => onClaim(row)}>
                            Claim
                          </Button>
                        )}
                        {canAssignInvestigation && showAssignAction && (
                          <Button size="small" onClick={() => onAssign(row)}>
                            Assign
                          </Button>
                        )}
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => onOpen(row.investigationId)}
                        >
                          Open
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </SurfaceCard>
  );
}
