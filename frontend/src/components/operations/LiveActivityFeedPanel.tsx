import {
  Box,
  Chip,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";

import EmptyState from "../ui/EmptyState";
import StatusChip from "../ui/StatusChip";
import SurfaceCard from "../ui/SurfaceCard";
import type { ActivityFeedEntry } from "../../types/operationsCenter";
import { formatStatusLabel } from "../../utils/statusBadges";

function feedChip(entry: ActivityFeedEntry) {
  if (entry.kind === "transaction" && entry.status) {
    return <StatusChip kind="screening" value={entry.status} />;
  }

  if (entry.kind === "investigation-created" && entry.status) {
    return <StatusChip kind="investigation" value={entry.status} />;
  }

  if (entry.kind === "investigation-execution" && entry.status) {
    return (
      <Chip
        size="small"
        label={formatStatusLabel(entry.status)}
        variant="outlined"
        color="info"
      />
    );
  }

  if (entry.kind === "sse-reconnect") {
    return <Chip size="small" label="Reconnect" color="warning" variant="outlined" />;
  }

  return null;
}

export default function LiveActivityFeedPanel({
  entries,
}: {
  entries: ActivityFeedEntry[];
}) {
  return (
    <SurfaceCard title="Live Activity Feed">
      {entries.length === 0 ? (
        <EmptyState
          title="No live activity yet"
          description="Transaction screening and investigation events will appear here in real time."
        />
      ) : (
        <List dense disablePadding sx={{ maxHeight: 420, overflowY: "auto" }}>
          {entries.map((entry) => (
            <ListItem
              key={entry.id}
              divider
              sx={{ alignItems: "flex-start", px: 0 }}
            >
              <ListItemText
                primary={
                  <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {entry.title}
                    </Typography>
                    {feedChip(entry)}
                  </Stack>
                }
                secondary={
                  <Box sx={{ mt: 0.5 }}>
                    <Typography variant="body2" color="text.secondary">
                      {entry.detail}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {new Date(entry.occurredAt).toLocaleString()}
                    </Typography>
                  </Box>
                }
              />
            </ListItem>
          ))}
        </List>
      )}
    </SurfaceCard>
  );
}
