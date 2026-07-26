import { useCallback, useEffect, useRef, useState } from "react";

import { getSimulationLiveUrl } from "../services/simulationService";
import { AUTH_STORAGE_KEY } from "../types/auth";
import type { LiveTransactionEvent } from "../types/simulation";

function parseSseEvent(rawEvent: string): LiveTransactionEvent | null {
  const dataLines = rawEvent
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trim());

  if (dataLines.length === 0) {
    return null;
  }

  try {
    return JSON.parse(dataLines.join("\n")) as LiveTransactionEvent;
  } catch {
    return null;
  }
}

export function useSimulationLiveStream(options?: {
  enabled?: boolean;
  maxEvents?: number;
  onEvent?: (event: LiveTransactionEvent) => void;
  onReconnect?: () => void;
}) {
  const { enabled = true, maxEvents = 200, onEvent, onReconnect } =
    options ?? {};
  const [events, setEvents] = useState<LiveTransactionEvent[]>([]);
  const [streamState, setStreamState] = useState<
    "connecting" | "connected" | "disconnected"
  >("disconnected");
  const onEventRef = useRef(onEvent);
  const onReconnectRef = useRef(onReconnect);
  const wasConnectedRef = useRef(false);

  onEventRef.current = onEvent;
  onReconnectRef.current = onReconnect;

  const upsertEvent = useCallback(
    (event: LiveTransactionEvent) => {
      setEvents((current) => {
        const eventId = String(event.transactionId);
        const existingIndex = current.findIndex(
          (row) => String(row.transactionId) === eventId,
        );

        if (existingIndex >= 0) {
          const updated = [...current];
          updated[existingIndex] = event;
          return updated;
        }

        return [event, ...current].slice(0, maxEvents);
      });
      onEventRef.current?.(event);
    },
    [maxEvents],
  );

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const controller = new AbortController();

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

    setStreamState("connecting");

    async function connectStream(): Promise<void> {
      while (!controller.signal.aborted) {
        try {
          const response = await fetch(getSimulationLiveUrl(), {
            headers: {
              Authorization: `Bearer ${authToken}`,
              Accept: "text/event-stream",
            },
            signal: controller.signal,
          });

          if (!response.ok || !response.body) {
            throw new Error("Unable to connect to transaction stream");
          }

          setStreamState("connected");
          if (wasConnectedRef.current) {
            onReconnectRef.current?.();
          }
          wasConnectedRef.current = true;

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";

          while (!controller.signal.aborted) {
            const { done, value } = await reader.read();
            if (done) {
              break;
            }

            buffer += decoder.decode(value, { stream: true });
            const chunks = buffer.split("\n\n");
            buffer = chunks.pop() ?? "";

            for (const rawEvent of chunks) {
              const parsed = parseSseEvent(rawEvent);
              if (parsed) {
                upsertEvent(parsed);
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
  }, [enabled, upsertEvent]);

  return { events, streamState, upsertEvent };
}
