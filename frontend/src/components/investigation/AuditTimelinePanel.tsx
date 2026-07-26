import { Card, CardContent, Stack, Typography } from "@mui/material";

import type { InvestigationTimelineEntry } from "../../types/investigation";

export default function AuditTimelinePanel({
  timeline,
}: {
  timeline: InvestigationTimelineEntry[];
}) {
  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Audit Timeline
        </Typography>
        <Stack spacing={1.5}>
          {timeline.length === 0 ? (
            <Typography color="text.secondary">
              No audit events recorded yet.
            </Typography>
          ) : (
            timeline.map((entry) => (
              <Stack key={`${entry.sequence}-${entry.label}`} spacing={0.25}>
                <Typography sx={{ fontWeight: 600 }}>
                  {entry.sequence}. {entry.label}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {new Date(entry.occurredAt).toLocaleString()}
                  {entry.actor ? ` · ${entry.actor}` : ""}
                </Typography>
              </Stack>
            ))
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
