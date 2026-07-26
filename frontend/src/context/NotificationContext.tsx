import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { useNotificationStream } from "../hooks/useNotificationStream";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "../services/notificationService";
import type { AppNotification } from "../types/notification";
import { useAuth } from "../hooks/useAuth";

interface NotificationContextValue {
  unreadCount: number;
  recentNotifications: AppNotification[];
  isLoading: boolean;
  error: unknown;
  refreshNotifications: () => Promise<void>;
  markRead: (notificationId: string) => Promise<void>;
  markAllRead: () => Promise<void>;
}

const NotificationContext = createContext<
  NotificationContextValue | undefined
>(undefined);

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const [unreadCount, setUnreadCount] = useState(0);
  const [recentNotifications, setRecentNotifications] = useState<
    AppNotification[]
  >([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const refreshNotifications = useCallback(async () => {
    if (!isAuthenticated) {
      setUnreadCount(0);
      setRecentNotifications([]);
      setIsLoading(false);
      return;
    }

    try {
      setError(null);
      const [page, count] = await Promise.all([
        getNotifications(0, 10),
        getUnreadNotificationCount(),
      ]);
      setRecentNotifications(page.content);
      setUnreadCount(count);
    } catch (caughtError) {
      setError(caughtError);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (isAuthLoading) {
      return;
    }

    setIsLoading(true);
    void refreshNotifications();
  }, [isAuthLoading, refreshNotifications]);

  useNotificationStream({
    enabled: isAuthenticated && !isAuthLoading,
    onNotification: (notification) => {
      setRecentNotifications((current) => [
        notification,
        ...current.filter((item) => item.id !== notification.id),
      ].slice(0, 10));
      if (!notification.read) {
        setUnreadCount((current) => current + 1);
      }
    },
  });

  const markRead = useCallback(async (notificationId: string) => {
    const updated = await markNotificationRead(notificationId);
    setRecentNotifications((current) =>
      current.map((item) =>
        item.id === notificationId ? updated : item,
      ),
    );
    setUnreadCount((current) => Math.max(0, current - 1));
  }, []);

  const markAllRead = useCallback(async () => {
    const remaining = await markAllNotificationsRead();
    setUnreadCount(remaining);
    setRecentNotifications((current) =>
      current.map((item) => ({ ...item, read: true })),
    );
  }, []);

  const value = useMemo(
    () => ({
      unreadCount,
      recentNotifications,
      isLoading,
      error,
      refreshNotifications,
      markRead,
      markAllRead,
    }),
    [
      unreadCount,
      recentNotifications,
      isLoading,
      error,
      refreshNotifications,
      markRead,
      markAllRead,
    ],
  );

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error(
      "useNotifications must be used within NotificationProvider",
    );
  }
  return context;
}
