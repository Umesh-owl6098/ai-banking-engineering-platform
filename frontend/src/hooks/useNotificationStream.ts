import { useCallback, useEffect, useRef, useState } from "react";

import { getNotificationsLiveUrl } from "../services/notificationService";
import type { AppNotification } from "../types/notification";
import { AUTH_STORAGE_KEY } from "../types/auth";

function parseNotificationEvent(rawEvent: string): AppNotification | null {
  const eventName = rawEvent
    .split("\n")
    .find((line) => line.startsWith("event:"))
    ?.slice(6)
    .trim();
  const dataLines = rawEvent
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trim());

  if (eventName !== "notification" || dataLines.length === 0) {
    return null;
  }

  try {
    return JSON.parse(dataLines.join("\n")) as AppNotification;
  } catch {
    return null;
  }
}

export function useNotificationStream(options?: {
  enabled?: boolean;
  onNotification?: (notification: AppNotification) => void;
}) {
  const { enabled = true, onNotification } = options ?? {};
  const [streamState, setStreamState] = useState<
    "connecting" | "connected" | "disconnected"
  >("disconnected");
  const onNotificationRef = useRef(onNotification);
  onNotificationRef.current = onNotification;

  const handleNotification = useCallback((notification: AppNotification) => {
    onNotificationRef.current?.(notification);
  }, []);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const authToken = (() => {
      const raw = localStorage.getItem(AUTH_STORAGE_KEY);
      if (!raw) {
        return null;
      }
      try {
        return (JSON.parse(raw) as { token?: string }).token ?? null;
      } catch {
        return null;
      }
    })();

    if (!authToken) {
      setStreamState("disconnected");
      return;
    }

    const controller = new AbortController();
    setStreamState("connecting");

    async function connectStream(): Promise<void> {
      while (!controller.signal.aborted) {
        try {
          const response = await fetch(getNotificationsLiveUrl(), {
            headers: {
              Authorization: `Bearer ${authToken}`,
              Accept: "text/event-stream",
            },
            signal: controller.signal,
          });

          if (!response.ok || !response.body) {
            throw new Error("Unable to connect to notification stream");
          }

          setStreamState("connected");

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";

          while (!controller.signal.aborted) {
            const { done, value } = await reader.read();
            if (done) {
              break;
            }

            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split("\n\n");
            buffer = events.pop() ?? "";

            for (const rawEvent of events) {
              const parsed = parseNotificationEvent(rawEvent);
              if (parsed) {
                handleNotification(parsed);
              }
            }
          }
        } catch {
          if (controller.signal.aborted) {
            return;
          }

          setStreamState("disconnected");
          await new Promise((resolve) => setTimeout(resolve, 3000));
        }
      }
    }

    void connectStream();

    return () => {
      controller.abort();
    };
  }, [enabled, handleNotification]);

  return { streamState };
}
