import { useCallback, useEffect, useRef, useState } from "react";

import { AUTH_STORAGE_KEY } from "../types/auth";
import type { InvestigationCreatedNotification } from "../types/investigation";
import type {
  InvestigationExecutionEvent,
  InvestigationLiveEvent,
} from "../types/investigationExecution";
import { getInvestigationsLiveUrl } from "../services/investigationService";

function parseInvestigationLiveEvent(
  rawEvent: string,
): InvestigationLiveEvent | null {
  const eventName = rawEvent
    .split("\n")
    .find((line) => line.startsWith("event:"))
    ?.slice(6)
    .trim();
  const dataLines = rawEvent
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trim());

  if (dataLines.length === 0) {
    return null;
  }

  try {
    const payload = JSON.parse(dataLines.join("\n")) as
      | InvestigationCreatedNotification
      | InvestigationExecutionEvent;

    if (eventName === "investigation-execution") {
      return {
        kind: "execution",
        data: payload as InvestigationExecutionEvent,
      };
    }

    return {
      kind: "created",
      data: payload as InvestigationCreatedNotification,
    };
  } catch {
    return null;
  }
}

export function useInvestigationLiveStream(options?: {
  investigationId?: string;
  enabled?: boolean;
  onCreated?: (notification: InvestigationCreatedNotification) => void;
  onExecution?: (event: InvestigationExecutionEvent) => void;
  onReconnect?: () => void;
}) {
  const {
    investigationId,
    enabled = true,
    onCreated,
    onExecution,
    onReconnect,
  } = options ?? {};
  const [streamState, setStreamState] = useState<
    "connecting" | "connected" | "disconnected"
  >("disconnected");
  const abortControllerRef = useRef<AbortController | null>(null);
  const onCreatedRef = useRef(onCreated);
  const onExecutionRef = useRef(onExecution);
  const onReconnectRef = useRef(onReconnect);
  const wasConnectedRef = useRef(false);

  onCreatedRef.current = onCreated;
  onExecutionRef.current = onExecution;
  onReconnectRef.current = onReconnect;

  const handleEvent = useCallback(
    (event: InvestigationLiveEvent) => {
      if (event.kind === "created") {
        onCreatedRef.current?.(event.data);
        return;
      }

      if (
        investigationId &&
        event.data.investigationId !== investigationId
      ) {
        return;
      }

      onExecutionRef.current?.(event.data);
    },
    [investigationId],
  );

  useEffect(() => {
    if (!enabled) {
      return;
    }

    abortControllerRef.current?.abort();

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
    abortControllerRef.current = controller;
    setStreamState("connecting");

    async function connectStream(): Promise<void> {
      while (!controller.signal.aborted) {
        try {
          const response = await fetch(getInvestigationsLiveUrl(), {
            headers: {
              Authorization: `Bearer ${authToken}`,
              Accept: "text/event-stream",
            },
            signal: controller.signal,
          });

          if (!response.ok || !response.body) {
            throw new Error("Unable to connect to investigation stream");
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
            const events = buffer.split("\n\n");
            buffer = events.pop() ?? "";

            for (const rawEvent of events) {
              const parsed = parseInvestigationLiveEvent(rawEvent);
              if (parsed) {
                handleEvent(parsed);
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
  }, [enabled, handleEvent]);

  return { streamState };
}
