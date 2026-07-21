import {
  useEffect,
  useRef,
  useState,
} from "react";

import {
  getConversationMessages,
  streamChat,
} from "../services/chatService";

import type {
  ChatMessage,
  SourceReference,
} from "../types/chat";

export function useStreamingChat(
  conversationId: string,
) {
  const [messages, setMessages] = useState<
    ChatMessage[]
  >([]);

  const [isLoadingMessages, setIsLoadingMessages] =
    useState(false);

  const [isStreaming, setIsStreaming] =
    useState(false);

  const [error, setError] = useState<
    string | null
  >(null);

  const abortControllerRef =
    useRef<AbortController | null>(null);

  useEffect(() => {
    let isCurrentRequest = true;

    abortControllerRef.current?.abort();
    abortControllerRef.current = null;

    setMessages([]);
    setError(null);
    setIsStreaming(false);

    if (!conversationId) {
      setIsLoadingMessages(false);
      return;
    }

    async function loadMessages(): Promise<void> {
      setIsLoadingMessages(true);

      try {
        const savedMessages =
          await getConversationMessages(
            conversationId,
          );

        if (isCurrentRequest) {
          setMessages(savedMessages);
        }
      } catch (caughtError) {
        if (isCurrentRequest) {
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : "Unable to load conversation messages.",
          );
        }
      } finally {
        if (isCurrentRequest) {
          setIsLoadingMessages(false);
        }
      }
    }

    void loadMessages();

    return () => {
      isCurrentRequest = false;
    };
  }, [conversationId]);

  async function sendMessage(
    message: string,
  ): Promise<void> {
    const trimmedMessage = message.trim();

    if (
      !conversationId ||
      !trimmedMessage ||
      isStreaming
    ) {
      return;
    }

    setError(null);
    setIsStreaming(true);

    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: "USER",
      content: trimmedMessage,
    };

    const assistantMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: "ASSISTANT",
      content: "",
      sources: [],
    };

    setMessages((currentMessages) => [
      ...currentMessages,
      userMessage,
      assistantMessage,
    ]);

    const abortController =
      new AbortController();

    abortControllerRef.current =
      abortController;

    try {
      await streamChat(
        {
          conversationId,
          message: trimmedMessage,
        },
        {
          onToken: (token) => {
            setMessages((currentMessages) =>
              updateLastAssistantMessage(
                currentMessages,
                (currentMessage) => ({
                  ...currentMessage,
                  content:
                    currentMessage.content +
                    token,
                }),
              ),
            );
          },

          onSources: (
            sources: SourceReference[],
          ) => {
            setMessages((currentMessages) =>
              updateLastAssistantMessage(
                currentMessages,
                (currentMessage) => ({
                  ...currentMessage,
                  sources,
                }),
              ),
            );
          },

          onComplete: (completion) => {
            setMessages((currentMessages) =>
              updateLastAssistantMessage(
                currentMessages,
                (currentMessage) => ({
                  ...currentMessage,
                  id:
                    completion.assistantMessageId ||
                    currentMessage.id,
                }),
              ),
            );

            setIsStreaming(false);
          },

          onError: (errorMessage) => {
            setError(errorMessage);
            setIsStreaming(false);
          },
        },
        abortController.signal,
      );
    } catch (caughtError) {
      if (
        caughtError instanceof DOMException &&
        caughtError.name === "AbortError"
      ) {
        setError("Response generation stopped.");
      } else {
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : "Unable to send the message.",
        );
      }

      setIsStreaming(false);
    } finally {
      abortControllerRef.current = null;
    }
  }

  function stopStreaming(): void {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setIsStreaming(false);
  }

  function clearMessages(): void {
    setMessages([]);
    setError(null);
  }

  return {
    messages,
    isLoadingMessages,
    isStreaming,
    error,
    sendMessage,
    stopStreaming,
    clearMessages,
  };
}

function updateLastAssistantMessage(
  messages: ChatMessage[],
  updater: (
    message: ChatMessage,
  ) => ChatMessage,
): ChatMessage[] {
  const updatedMessages = [...messages];

  for (
    let index = updatedMessages.length - 1;
    index >= 0;
    index--
  ) {
    if (
      updatedMessages[index].role ===
      "ASSISTANT"
    ) {
      updatedMessages[index] = updater(
        updatedMessages[index],
      );

      break;
    }
  }

  return updatedMessages;
}