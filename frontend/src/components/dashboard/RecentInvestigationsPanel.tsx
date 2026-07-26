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

import type { RecentInvestigationRow } from "../../types/dashboard";
import {
  investigationStatusChipColor,
  severityChipColor,
} from "../../utils/statusBadges";

export default function RecentInvestigationsPanel({
  rows,
}: {
  rows: RecentInvestigationRow[];
}) {
  const navigate = useNavigate();

  return (
    <Card sx={{ height: "100%" }}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
          Recent Investigations
        </Typography>
        <TableContainer sx={{ maxHeight: 360, overflow: "auto" }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>Reference</TableCell>
                <TableCell>Source</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Severity</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Created</TableCell>
                <TableCell align="right">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography variant="body2" color="text.secondary">
                      No investigations yet.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((row) => (
                  <TableRow key={row.investigationId} hover>
                    <TableCell>{row.reference}</TableCell>
                    <TableCell>{row.source}</TableCell>
                    <TableCell>{row.customerName}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={row.severity}
                        color={severityChipColor(row.severity)}
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={row.status}
                        color={investigationStatusChipColor(row.status)}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      {new Date(row.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        endIcon={<OpenInNewIcon />}
                        onClick={() =>
                          navigate(`/investigations/${row.investigationId}`)
                        }
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
