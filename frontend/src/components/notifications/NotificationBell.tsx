import NotificationsNoneOutlinedIcon from "@mui/icons-material/NotificationsNoneOutlined";
import {
  Badge,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Menu,
  Stack,
  Typography,
} from "@mui/material";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { useNotifications } from "../../context/NotificationContext";
import EmptyState from "../ui/EmptyState";
import ErrorState from "../ui/ErrorState";
import { LoadingSpinner } from "../ui/LoadingState";
import { severityChipColor } from "../../utils/statusBadges";

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString();
}

export default function NotificationBell() {
  const navigate = useNavigate();
  const {
    unreadCount,
    recentNotifications,
    isLoading,
    error,
    refreshNotifications,
    markRead,
    markAllRead,
  } = useNotifications();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  async function handleOpenInvestigation(
    notificationId: string,
    investigationId: string | null,
  ): Promise<void> {
    try {
      await markRead(notificationId);
    } catch {
      // Navigation should still proceed even if mark-read fails.
    }

    setAnchorEl(null);
    if (investigationId) {
      navigate(`/investigations/${investigationId}`);
    }
  }

  return (
    <>
      <IconButton
        aria-label="Notifications"
        color="inherit"
        onClick={(event) => {
          setAnchorEl(event.currentTarget);
          void refreshNotifications();
        }}
      >
        <Badge badgeContent={unreadCount} color="error" max={99}>
          <NotificationsNoneOutlinedIcon />
        </Badge>
      </IconButton>

      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={() => setAnchorEl(null)}
        slotProps={{
          paper: {
            sx: { width: 380, maxWidth: "92vw" },
          },
        }}
      >
        <Box sx={{ px: 2, py: 1.5 }}>
          <Stack
            direction="row"
            sx={{ alignItems: "center", justifyContent: "space-between" }}
          >
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              Notifications
            </Typography>
            <Button
              size="small"
              disabled={unreadCount === 0}
              onClick={() => void markAllRead()}
            >
              Mark all read
            </Button>
          </Stack>
        </Box>

        <Divider />

        {isLoading && (
          <Box sx={{ p: 2 }}>
            <LoadingSpinner label="Loading notifications…" />
          </Box>
        )}

        {!isLoading && error != null && (
          <Box sx={{ p: 2 }}>
            <ErrorState
              error={error}
              fallback="Unable to load notifications."
              onRetry={() => void refreshNotifications()}
            />
          </Box>
        )}

        {!isLoading && error == null && recentNotifications.length === 0 && (
          <Box sx={{ p: 2 }}>
            <EmptyState
              title="No notifications"
              description="Operational alerts will appear here when investigations require attention."
            />
          </Box>
        )}

        {!isLoading && error == null && recentNotifications.length > 0 && (
          <List dense disablePadding sx={{ maxHeight: 420, overflow: "auto" }}>
            {recentNotifications.map((notification) => (
              <ListItem
                key={notification.id}
                divider
                sx={{
                  alignItems: "flex-start",
                  bgcolor: notification.read ? "inherit" : "action.hover",
                }}
              >
                <ListItemText
                  primary={
                    <Stack
                      direction="row"
                      spacing={1}
                      sx={{ alignItems: "center", mb: 0.5 }}
                    >
                      <Typography
                        variant="body2"
                        sx={{ fontWeight: notification.read ? 500 : 700 }}
                      >
                        {notification.title}
                      </Typography>
                      <Chip
                        label={notification.severity}
                        size="small"
                        color={severityChipColor(notification.severity)}
                      />
                    </Stack>
                  }
                  secondary={
                    <Stack spacing={1} sx={{ mt: 0.5 }}>
                      <Typography variant="body2" color="text.secondary">
                        {notification.message}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatTimestamp(notification.createdAt)}
                      </Typography>
                      {notification.relatedInvestigationId && (
                        <Button
                          size="small"
                          variant="outlined"
                          sx={{ alignSelf: "flex-start" }}
                          onClick={() =>
                            void handleOpenInvestigation(
                              notification.id,
                              notification.relatedInvestigationId,
                            )
                          }
                        >
                          Open Investigation
                        </Button>
                      )}
                    </Stack>
                  }
                />
              </ListItem>
            ))}
          </List>
        )}

        <Divider />
        <Box sx={{ p: 1.5 }}>
          <Button
            fullWidth
            onClick={() => {
              setAnchorEl(null);
              navigate("/notifications");
            }}
          >
            View all notifications
          </Button>
        </Box>
      </Menu>
    </>
  );
}
