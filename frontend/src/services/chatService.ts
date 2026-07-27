import { API_BASE_URL, getAuthHeaders } from "../api/api";
import type {
  ChatRequest,
  ChatMessage,
  SourceReference,
} from "../types/chat";

interface StreamMetadata {
  conversationId: string;
  userMessageId: string;
  startedAt: string;
}

interface StreamCompletion {
  conversationId: string;
  userMessageId: string;
  assistantMessageId: string;
  createdAt: string;
  fallback?: boolean;
  fallbackReason?: string;
}

interface StreamErrorPayload {
  message?: string;
  code?: string;
}

export type StreamEndReason =
  | "complete"
  | "error"
  | "premature_eof"
  | "aborted";

export interface StreamResult {
  endReason: StreamEndReason;
  errorMessage?: string;
  errorCode?: string;
}

export interface StreamCallbacks {
  onMetadata?: (metadata: StreamMetadata) => void;
  onToken: (token: string) => void;
  onSources?: (sources: SourceReference[]) => void;
  onComplete?: (completion: StreamCompletion) => void;
  onError?: (message: string, code?: string) => void;
}

export class ChatStreamError extends Error {
  readonly code?: string;

  readonly endReason: StreamEndReason;

  constructor(
    message: string,
    endReason: StreamEndReason,
    code?: string,
  ) {
    super(message);
    this.name = "ChatStreamError";
    this.endReason = endReason;
    this.code = code;
  }
}

export async function streamChat(
  request: ChatRequest,
  callbacks: StreamCallbacks,
  signal?: AbortSignal,
): Promise<StreamResult> {
  if (signal?.aborted) {
    return {
      endReason: "aborted",
      errorMessage: "Response generation stopped.",
    };
  }

  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/chat/stream`, {
      method: "POST",
      headers: getAuthHeaders({
        Accept: "text/event-stream",
        "Content-Type": "application/json",
      }),
      body: JSON.stringify(request),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) {
      return {
        endReason: "aborted",
        errorMessage: "Response generation stopped.",
      };
    }

    throw new ChatStreamError(
      describeFetchFailure(error),
      "error",
      "NETWORK_ERROR",
    );
  }

  if (!response.ok) {
    const errorText = await response.text();

    throw new ChatStreamError(
      errorText || `Chat request failed with status ${response.status}`,
      "error",
      `HTTP_${response.status}`,
    );
  }

  if (!response.body) {
    throw new ChatStreamError(
      "Streaming response body is unavailable",
      "error",
      "EMPTY_BODY",
    );
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  let buffer = "";
  let receivedComplete = false;
  let receivedError = false;
  let lastErrorMessage: string | undefined;
  let lastErrorCode: string | undefined;

  try {
    while (true) {
      const { value, done } = await reader.read();

      if (done) {
        break;
      }

      buffer += decoder.decode(value, {
        stream: true,
      });

      const rawEvents = buffer.split(/\r?\n\r?\n/);

      buffer = rawEvents.pop() ?? "";

      for (const rawEvent of rawEvents) {
        const eventResult = processSseEvent(rawEvent, callbacks);

        if (eventResult.type === "complete") {
          receivedComplete = true;
        }

        if (eventResult.type === "error") {
          receivedError = true;
          lastErrorMessage = eventResult.message;
          lastErrorCode = eventResult.code;
        }
      }
    }

    buffer += decoder.decode();

    if (buffer.trim()) {
      const eventResult = processSseEvent(buffer, callbacks);

      if (eventResult.type === "complete") {
        receivedComplete = true;
      }

      if (eventResult.type === "error") {
        receivedError = true;
        lastErrorMessage = eventResult.message;
        lastErrorCode = eventResult.code;
      }
    }
  } catch (error) {
    if (signal?.aborted) {
      return {
        endReason: "aborted",
        errorMessage: "Response generation stopped.",
      };
    }

    throw new ChatStreamError(
      describeFetchFailure(error),
      "error",
      "STREAM_READ_ERROR",
    );
  }

  if (receivedError) {
    return {
      endReason: "error",
      errorMessage: lastErrorMessage ?? "Streaming chat failed",
      errorCode: lastErrorCode,
    };
  }

  if (!receivedComplete) {
    const message =
      "The assistant response ended unexpectedly before completion.";

    callbacks.onError?.(message, "PREMATURE_EOF");

    return {
      endReason: "premature_eof",
      errorMessage: message,
      errorCode: "PREMATURE_EOF",
    };
  }

  return {
    endReason: "complete",
  };
}

type SseEventResult =
  | { type: "ignored" }
  | { type: "complete" }
  | { type: "error"; message: string; code?: string };

export function processSseEvent(
  rawEvent: string,
  callbacks: StreamCallbacks,
): SseEventResult {
  const lines = rawEvent.split(/\r?\n/);

  let eventName = "";
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventName = line.slice("event:".length).trim();
    }

    if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trim());
    }
  }

  if (!eventName || dataLines.length === 0) {
    return { type: "ignored" };
  }

  const dataText = dataLines.join("\n");

  let data: unknown;

  try {
    data = JSON.parse(dataText);
  } catch {
    const message = `Invalid streaming data received: ${dataText}`;

    callbacks.onError?.(message, "MALFORMED_STREAM");

    return {
      type: "error",
      message,
      code: "MALFORMED_STREAM",
    };
  }

  switch (eventName) {
    case "metadata":
      callbacks.onMetadata?.(data as StreamMetadata);
      break;

    case "token": {
      const tokenData = data as { token?: string };

      if (tokenData.token) {
        callbacks.onToken(tokenData.token);
      }

      break;
    }

    case "sources": {
      const sourcesData = data as {
        sources?: SourceReference[];
      };

      callbacks.onSources?.(sourcesData.sources ?? []);

      break;
    }

    case "complete":
      callbacks.onComplete?.(data as StreamCompletion);

      return { type: "complete" };

    case "error": {
      const errorData = data as StreamErrorPayload;
      const message = errorData.message ?? "Streaming chat failed";

      callbacks.onError?.(message, errorData.code);

      return {
        type: "error",
        message,
        code: errorData.code,
      };
    }

    default:
      console.debug("Unhandled SSE event:", eventName, data);
  }

  return { type: "ignored" };
}

function describeFetchFailure(error: unknown): string {
  if (error instanceof ChatStreamError) {
    return error.message;
  }

  if (error instanceof DOMException && error.name === "AbortError") {
    return "Response generation stopped.";
  }

  if (error instanceof TypeError) {
    return "Network error while receiving the assistant response.";
  }

  if (error instanceof Error) {
    return error.message;
  }

  return "Unable to send the message.";
}

export async function getConversationMessages(
  conversationId: string,
): Promise<ChatMessage[]> {
  const response = await fetch(
    `${API_BASE_URL}/conversations/${conversationId}/messages`,
    {
      headers: getAuthHeaders(),
    },
  );

  if (!response.ok) {
    throw new Error(`Unable to load messages (${response.status})`);
  }

  return response.json();
}
