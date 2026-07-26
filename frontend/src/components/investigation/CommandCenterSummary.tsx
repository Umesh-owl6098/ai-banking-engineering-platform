import {
  Box,
  Card,
  CardContent,
  Chip,
  LinearProgress,
  Stack,
  Typography,
} from "@mui/material";

import type { InvestigationCase, InvestigationReport } from "../../types/investigation";
import type { ExecutionTimelineStage } from "../../types/investigationExecution";
import StatusChip from "../ui/StatusChip";
import { stageStatusLabel } from "../../utils/executionTimeline";

function formatRules(rules: string[] | null | undefined): string {
  if (!rules || rules.length === 0) {
    return "No screening rules triggered";
  }

  return rules.map((rule) => rule.replaceAll("_", " ")).join(", ");
}

function pipelineProgress(stages: ExecutionTimelineStage[]): {
  completed: number;
  total: number;
  percent: number;
  activeStage: string | null;
} {
  const total = stages.length;
  const completed = stages.filter(
    (stage) => stage.stageStatus === "COMPLETED",
  ).length;
  const running = stages.find((stage) => stage.stageStatus === "RUNNING");

  return {
    completed,
    total,
    percent: total === 0 ? 0 : Math.round((completed / total) * 100),
    activeStage: running?.label ?? null,
  };
}

export default function CommandCenterSummary({
  investigation,
  timelineStages,
  recommendedAction,
}: {
  investigation: InvestigationCase;
  timelineStages: ExecutionTimelineStage[];
  recommendedAction: string | null;
}) {
  const progress = pipelineProgress(timelineStages);

  return (
    <Card variant="outlined" sx={{ borderColor: "divider" }}>
      <CardContent>
        <Stack spacing={2}>
          <Stack
            direction={{ xs: "column", md: "row" }}
            spacing={2}
            sx={{ justifyContent: "space-between", alignItems: { md: "center" } }}
          >
            <Box sx={{ flex: 1 }}>
              <Typography variant="overline" color="text.secondary">
                Trigger Reason
              </Typography>
              <Typography sx={{ fontWeight: 600 }}>
                {investigation.screeningReason ?? investigation.description}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {formatRules(investigation.screeningTriggeredRules)}
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
              <StatusChip kind="severity" value={investigation.priority} />
              <StatusChip kind="investigation" value={investigation.status} />
              {investigation.autoCreated && (
                <Chip label="Auto-created" color="info" size="small" variant="outlined" />
              )}
            </Stack>
          </Stack>

          <Box>
            <Stack
              direction="row"
              spacing={1}
              sx={{ alignItems: "center", mb: 0.5 }}
            >
              <Typography variant="body2" color="text.secondary">
                Pipeline progress
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {progress.completed}/{progress.total} stages
              </Typography>
              {progress.activeStage && (
                <Chip
                  label={`${stageStatusLabel("RUNNING")}: ${progress.activeStage}`}
                  size="small"
                  color="info"
                  variant="outlined"
                />
              )}
            </Stack>
            <LinearProgress
              variant="determinate"
              value={progress.percent}
              sx={{ height: 8, borderRadius: 1 }}
            />
          </Box>

          {recommendedAction && (
            <Box>
              <Typography variant="overline" color="text.secondary">
                Recommended Action
              </Typography>
              <Typography>{recommendedAction}</Typography>
            </Box>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function resolveRecommendedAction(
  report: InvestigationReport | null | undefined,
): string | null {
  if (report?.analystRecommendation?.trim()) {
    return report.analystRecommendation.trim();
  }

  return null;
}
