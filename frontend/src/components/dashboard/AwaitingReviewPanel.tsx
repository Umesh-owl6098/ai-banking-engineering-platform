import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import RateReviewOutlinedIcon from "@mui/icons-material/RateReviewOutlined";
import { useNavigate } from "react-router-dom";

import type { AwaitingReviewRow } from "../../types/dashboard";
import {
  formatDurationMs,
  severityChipColor,
} from "../../utils/statusBadges";

export default function AwaitingReviewPanel({
  rows,
}: {
  rows: AwaitingReviewRow[];
}) {
  const navigate = useNavigate();

  return (
    <Card sx={{ height: "100%" }}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
          Awaiting Human Review
        </Typography>

        <Stack spacing={1.25} sx={{ maxHeight: 360, overflow: "auto" }}>
          {rows.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              No cases are waiting for analyst review.
            </Typography>
          ) : (
            rows.map((row) => (
              <Box
                key={row.investigationId}
                sx={{
                  p: 1.25,
                  borderRadius: 1.5,
                  border: "1px solid",
                  borderColor: "divider",
                  bgcolor: "warning.50",
                }}
              >
                <Stack spacing={0.75}>
                  <Stack
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: "center", flexWrap: "wrap" }}
                  >
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {row.reference}
                    </Typography>
                    <Chip
                      size="small"
                      label={row.severity}
                      color={severityChipColor(row.severity)}
                    />
                  </Stack>
                  <Typography variant="body2">{row.customerName}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Recommendation: {row.finalRecommendation}
                    {row.confidencePercent != null
                      ? ` · ${row.confidencePercent}% confidence`
                      : ""}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Waiting {formatDurationMs(row.waitingDurationMs)}
                  </Typography>
                  <Stack direction="row" sx={{ justifyContent: "flex-end" }}>
                    <Button
                      size="small"
                      variant="contained"
                      color="warning"
                      startIcon={<RateReviewOutlinedIcon />}
                      onClick={() =>
                        navigate(`/investigations/${row.investigationId}`)
                      }
                    >
                      Review Now
                    </Button>
                  </Stack>
                </Stack>
              </Box>
            ))
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
