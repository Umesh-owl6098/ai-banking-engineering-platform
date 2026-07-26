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
}

interface StreamCallbacks {
  onMetadata?: (metadata: StreamMetadata) => void;
  onToken: (token: string) => void;
  onSources?: (sources: SourceReference[]) => void;
  onComplete?: (completion: StreamCompletion) => void;
  onError?: (message: string) => void;
}

export async function streamChat(
  request: ChatRequest,
  callbacks: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/chat/stream`, {
    method: "POST",
    headers: getAuthHeaders({
      Accept: "text/event-stream",
      "Content-Type": "application/json",
    }),
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    const errorText = await response.text();

    throw new Error(
      errorText || `Chat request failed with status ${response.status}`,
    );
  }

  if (!response.body) {
    throw new Error("Streaming response body is unavailable");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  let buffer = "";

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
      processSseEvent(rawEvent, callbacks);
    }
  }

  buffer += decoder.decode();

  if (buffer.trim()) {
    processSseEvent(buffer, callbacks);
  }
}

function processSseEvent(
  rawEvent: string,
  callbacks: StreamCallbacks,
): void {
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
    return;
  }

  const dataText = dataLines.join("\n");

  let data: unknown;

  try {
    data = JSON.parse(dataText);
  } catch {
    throw new Error(`Invalid streaming data received: ${dataText}`);
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
      break;

    case "error": {
      const errorData = data as { message?: string };

      callbacks.onError?.(errorData.message ?? "Streaming chat failed");

      break;
    }

    default:
      console.debug("Unhandled SSE event:", eventName, data);
  }
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
