import {
  Box,
  Button,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import ErrorState from "../components/ui/ErrorState";
import EmptyState from "../components/ui/EmptyState";
import { LoadingSpinner } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import PageHeader from "../components/ui/PageHeader";
import { useNotifications } from "../context/NotificationContext";
import { getNotifications } from "../services/notificationService";
import type { AppNotification } from "../types/notification";
import { severityChipColor } from "../utils/statusBadges";
import { layout } from "../theme/tokens";

export default function NotificationsPage() {
  const navigate = useNavigate();
  const { markRead, markAllRead, refreshNotifications } = useNotifications();
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const loadPage = useCallback(async (nextPage: number) => {
    try {
      setIsLoading(true);
      setError(null);
      const response = await getNotifications(nextPage, 20);
      setNotifications(response.content);
      setPage(response.page);
      setTotalPages(response.totalPages);
    } catch (caughtError) {
      setError(caughtError);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadPage(0);
  }, [loadPage]);

  async function handleOpen(notification: AppNotification): Promise<void> {
    if (!notification.read) {
      await markRead(notification.id);
    }

    if (notification.relatedInvestigationId) {
      navigate(`/investigations/${notification.relatedInvestigationId}`);
      return;
    }

    await refreshNotifications();
    await loadPage(page);
  }

  if (isLoading) {
    return (
      <PageContainer>
        <LoadingSpinner label="Loading notification history…" />
      </PageContainer>
    );
  }

  if (error != null && notifications.length === 0) {
    return (
      <PageContainer>
        <ErrorState
          error={error}
          fallback="Unable to load notifications."
          onRetry={() => void loadPage(page)}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
        <PageHeader
          title="Notification Center"
          description="Operational alerts for investigations, assignments, escalations, and AI execution events."
          actions={
            <Stack direction="row" spacing={1}>
              <Button
                variant="outlined"
                size="small"
                onClick={() => void loadPage(page)}
              >
                Refresh
              </Button>
              <Button
                variant="contained"
                size="small"
                onClick={() => void markAllRead().then(() => loadPage(page))}
              >
                Mark all read
              </Button>
            </Stack>
          }
        />

        {error != null && (
          <ErrorState
            error={error}
            fallback="Unable to refresh notifications."
            onRetry={() => void loadPage(page)}
          />
        )}

        {notifications.length === 0 ? (
          <EmptyState
            title="No notifications yet"
            description="Alerts will appear here when operational events require your attention."
          />
        ) : (
          <Box sx={{ overflowX: "auto" }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Title</TableCell>
                  <TableCell>Message</TableCell>
                  <TableCell>Severity</TableCell>
                  <TableCell>Time</TableCell>
                  <TableCell align="right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {notifications.map((notification) => (
                  <TableRow
                    key={notification.id}
                    hover
                    sx={{
                      bgcolor: notification.read ? "inherit" : "action.hover",
                    }}
                  >
                    <TableCell>
                      <Typography
                        variant="body2"
                        sx={{ fontWeight: notification.read ? 500 : 700 }}
                      >
                        {notification.title}
                      </Typography>
                    </TableCell>
                    <TableCell>{notification.message}</TableCell>
                    <TableCell>
                      <Chip
                        label={notification.severity}
                        size="small"
                        color={severityChipColor(notification.severity)}
                      />
                    </TableCell>
                    <TableCell>
                      {new Date(notification.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell align="right">
                      {notification.relatedInvestigationId ? (
                        <Button
                          size="small"
                          onClick={() => void handleOpen(notification)}
                        >
                          Open Investigation
                        </Button>
                      ) : (
                        <Typography variant="caption" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}

        {totalPages > 1 && (
          <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
            <Button
              size="small"
              disabled={page <= 0}
              onClick={() => void loadPage(page - 1)}
            >
              Previous
            </Button>
            <Button
              size="small"
              disabled={page + 1 >= totalPages}
              onClick={() => void loadPage(page + 1)}
            >
              Next
            </Button>
          </Stack>
        )}
      </Stack>
    </PageContainer>
  );
}
