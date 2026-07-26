import api, { API_BASE_URL } from "../api/api";
import type {
  AppNotification,
  NotificationPageResponse,
  UnreadCountResponse,
} from "../types/notification";

export function getNotificationsLiveUrl(): string {
  return `${API_BASE_URL}/notifications/live`;
}

export async function getNotifications(
  page = 0,
  size = 20,
): Promise<NotificationPageResponse> {
  const response = await api.get<NotificationPageResponse>("/notifications", {
    params: { page, size },
  });
  return response.data;
}

export async function getUnreadNotificationCount(): Promise<number> {
  const response = await api.get<UnreadCountResponse>(
    "/notifications/unread-count",
  );
  return response.data.unreadCount;
}

export async function markNotificationRead(
  notificationId: string,
): Promise<AppNotification> {
  const response = await api.post<AppNotification>(
    `/notifications/${notificationId}/read`,
  );
  return response.data;
}

export async function markAllNotificationsRead(): Promise<number> {
  const response = await api.post<UnreadCountResponse>(
    "/notifications/read-all",
  );
  return response.data.unreadCount;
}
