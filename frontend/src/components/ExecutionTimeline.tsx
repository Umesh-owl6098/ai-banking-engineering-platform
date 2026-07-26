import {
  Box,
  Chip,
  Stack,
  Typography,
} from "@mui/material";

import type { ExecutionTimelineStage } from "../types/investigationExecution";
import {
  formatStageDuration,
  stageStatusColor,
  stageStatusLabel,
} from "../utils/executionTimeline";

function formatTimestamp(value: string | null): string {
  if (!value) {
    return "—";
  }

  return new Date(value).toLocaleTimeString();
}

function TimelineRow({ stage }: { stage: ExecutionTimelineStage }) {
  const duration = formatStageDuration(stage.durationMs);

  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: "flex-start" }}>
      <Box
        sx={{
          width: 12,
          height: 12,
          mt: 0.75,
          borderRadius: "50%",
          bgcolor:
            stage.stageStatus === "COMPLETED"
              ? "success.main"
              : stage.stageStatus === "RUNNING"
                ? "info.main"
                : stage.stageStatus === "FAILED"
                  ? "error.main"
                  : "grey.400",
          flexShrink: 0,
        }}
      />
      <Box sx={{ flexGrow: 1, pb: 2 }}>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={1}
          sx={{ alignItems: { sm: "center" }, mb: 0.5 }}
        >
          <Typography sx={{ fontWeight: 600 }}>{stage.label}</Typography>
          <Chip
            label={
              stage.summary === "Awaiting Review"
                ? "Awaiting Review"
                : stageStatusLabel(stage.stageStatus)
            }
            color={stageStatusColor(stage.stageStatus)}
            size="small"
          />
          {duration && (
            <Chip
              label={duration}
              size="small"
              variant="outlined"
            />
          )}
        </Stack>
        {stage.summary && (
          <Typography variant="body2" sx={{ mt: 0.5 }}>
            {stage.summary}
          </Typography>
        )}
        <Typography variant="body2" color="text.secondary">
          {stage.stageStatus === "WAITING"
            ? "Waiting"
            : `Started ${formatTimestamp(stage.startedAt)}${
                stage.completedAt
                  ? ` • Completed ${formatTimestamp(stage.completedAt)}`
                  : ""
              }`}
        </Typography>
      </Box>
    </Stack>
  );
}

export default function ExecutionTimeline({
  stages,
}: {
  stages: ExecutionTimelineStage[];
}) {
  return (
    <Box sx={{ pl: 0.5 }}>
      {stages.map((stage) => (
        <TimelineRow key={stage.id} stage={stage} />
      ))}
    </Box>
  );
}
