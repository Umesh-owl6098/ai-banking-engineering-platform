import {
  Chip,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

import EmptyState from "../ui/EmptyState";
import SurfaceCard from "../ui/SurfaceCard";
import type { OperationsErrorEntry } from "../../types/operationsCenter";

function errorChip(type: string) {
  if (type === "EXECUTION_FAILED" || type === "REPORT_FAILURE") {
    return <Chip size="small" color="error" label={type.replaceAll("_", " ")} />;
  }
  if (type === "OPENAI_FALLBACK") {
    return <Chip size="small" color="warning" label="OpenAI fallback" />;
  }
  return <Chip size="small" label={type.replaceAll("_", " ")} variant="outlined" />;
}

export default function ErrorMonitoringPanel({
  recentErrors,
  executionFailureTotal,
  reportFallbackTotal,
  reportFailureTotal,
  sseReconnectCount,
}: {
  recentErrors: OperationsErrorEntry[];
  executionFailureTotal: number;
  reportFallbackTotal: number;
  reportFailureTotal: number;
  sseReconnectCount: number;
}) {
  return (
    <SurfaceCard title="Error Monitoring">
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
          <Chip
            size="small"
            variant="outlined"
            label={`Execution failures: ${executionFailureTotal}`}
            color={executionFailureTotal > 0 ? "error" : "default"}
          />
          <Chip
            size="small"
            variant="outlined"
            label={`OpenAI fallback uses: ${reportFallbackTotal}`}
            color={reportFallbackTotal > 0 ? "warning" : "default"}
          />
          <Chip
            size="small"
            variant="outlined"
            label={`Report failures: ${reportFailureTotal}`}
            color={reportFailureTotal > 0 ? "error" : "default"}
          />
          <Chip
            size="small"
            variant="outlined"
            label={`SSE reconnects: ${sseReconnectCount}`}
            color={sseReconnectCount > 0 ? "warning" : "default"}
          />
        </Stack>

        {recentErrors.length === 0 ? (
          <EmptyState
            title="No recent errors"
            description="Execution failures and fallback usage will appear here when detected."
          />
        ) : (
          <List dense disablePadding sx={{ maxHeight: 320, overflowY: "auto" }}>
            {recentErrors.map((entry, index) => (
              <ListItem
                key={`${entry.errorType}-${entry.occurredAt}-${index}`}
                divider
                sx={{ alignItems: "flex-start", px: 0 }}
              >
                <ListItemText
                  primary={
                    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                      {errorChip(entry.errorType)}
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {entry.source}
                      </Typography>
                    </Stack>
                  }
                  secondary={
                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                      <Typography variant="body2" color="text.secondary">
                        {entry.message}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(entry.occurredAt).toLocaleString()}
                        {entry.investigationId && (
                          <>
                            {" · "}
                            <Typography
                              component={RouterLink}
                              to={`/investigations/${entry.investigationId}`}
                              variant="caption"
                              sx={{ color: "primary.main" }}
                            >
                              Open investigation
                            </Typography>
                          </>
                        )}
                      </Typography>
                    </Stack>
                  }
                />
              </ListItem>
            ))}
          </List>
        )}
      </Stack>
    </SurfaceCard>
  );
}
