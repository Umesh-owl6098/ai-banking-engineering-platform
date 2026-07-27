import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Divider,
  Paper,
  Stack,
} from "@mui/material";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";
import axios from "axios";

import ChatInput from "../components/ChatInput";
import ConversationSidebar from "../components/ConversationSidebar";
import MessageBubble from "../components/MessageBubble";
import SourcePanel from "../components/SourcePanel";
import EmptyState from "../components/ui/EmptyState";
import PageHeader from "../components/ui/PageHeader";
import { useStreamingChat } from "../hooks/useStreamingChat";
import {
  getProjectAgents,
  resolveKnowledgeChatAgentId,
} from "../services/agentService";
import {
  createConversation,
  deleteConversation,
  getProjectConversations,
  updateConversationTitle,
} from "../services/conversationService";
import { INVESTIGATION_PROJECT_ID } from "../services/investigationService";
import type { Conversation } from "../types/conversation";

function createConversationTitle(message: string): string {
  const cleanMessage = message.trim().replace(/\s+/g, " ");

  if (cleanMessage.length <= 55) {
    return cleanMessage;
  }

  return `${cleanMessage.slice(0, 52)}...`;
}

function readErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const responseMessage = error.response?.data?.message;

    if (typeof responseMessage === "string" && responseMessage.trim()) {
      return responseMessage;
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }

  return fallback;
}

export default function ChatPage() {
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedConversationId, setSelectedConversationId] =
    useState("");
  const [chatAgentId, setChatAgentId] = useState<string | null>(null);
  const [isLoadingConversations, setIsLoadingConversations] = useState(true);
  const [conversationError, setConversationError] = useState<string | null>(
    null,
  );

  const {
    messages,
    isStreaming,
    error,
    sendMessage,
    stopStreaming,
    clearMessages,
  } = useStreamingChat(selectedConversationId);

  const resolveChatAgent = useCallback(async (): Promise<string> => {
    const agents = await getProjectAgents(INVESTIGATION_PROJECT_ID);
    const agentId = resolveKnowledgeChatAgentId(agents);

    if (!agentId) {
      throw new Error(
        "No active knowledge chat agent is configured for this project.",
      );
    }

    setChatAgentId(agentId);
    return agentId;
  }, []);

  const createAndSelectConversation = useCallback(
    async (agentId: string): Promise<Conversation | null> => {
      try {
        const newConversation = await createConversation({
          projectId: INVESTIGATION_PROJECT_ID,
          agentId,
          title: "New Banking Conversation",
        });

        setConversations((currentConversations) => [
          newConversation,
          ...currentConversations,
        ]);
        setSelectedConversationId(newConversation.id);

        return newConversation;
      } catch (createError) {
        setConversationError(
          readErrorMessage(
            createError,
            "Unable to create a new conversation.",
          ),
        );
        return null;
      }
    },
    [],
  );

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    const initializeConversations = async (): Promise<void> => {
      try {
        setIsLoadingConversations(true);
        setConversationError(null);

        const agentId = await resolveChatAgent();

        const data = await getProjectConversations(
          INVESTIGATION_PROJECT_ID,
        );
        setConversations(data);

        if (data.length > 0) {
          setSelectedConversationId(data[0].id);
          return;
        }

        await createAndSelectConversation(agentId);
      } catch (loadError) {
        setConversationError(
          readErrorMessage(
            loadError,
            "Unable to load conversations.",
          ),
        );
      } finally {
        setIsLoadingConversations(false);
      }
    };

    void initializeConversations();
  }, [createAndSelectConversation, resolveChatAgent]);

  const handleNewConversation = async (): Promise<void> => {
    if (!chatAgentId) {
      setConversationError(
        "No knowledge chat agent is available. Refresh the page and try again.",
      );
      return;
    }

    stopStreaming();
    setConversationError(null);
    await createAndSelectConversation(chatAgentId);
  };

  const handleDeleteConversation = async (
    conversationId: string,
  ): Promise<void> => {
    try {
      await deleteConversation(conversationId);

      const remainingConversations = conversations.filter(
        (conversation) => conversation.id !== conversationId,
      );

      setConversations(remainingConversations);

      const deletedSelectedConversation =
        conversationId === selectedConversationId;

      if (!deletedSelectedConversation) {
        return;
      }

      stopStreaming();

      if (remainingConversations.length > 0) {
        setSelectedConversationId(remainingConversations[0].id);
      } else if (chatAgentId) {
        await createAndSelectConversation(chatAgentId);
      }
    } catch {
      window.alert("Unable to delete the conversation. Please try again.");
    }
  };

  const handleSendMessage = async (message: string): Promise<void> => {
    if (!selectedConversationId) {
      return;
    }

    const selectedConversation = conversations.find(
      (conversation) => conversation.id === selectedConversationId,
    );

    const shouldUpdateTitle =
      selectedConversation?.title === "New Banking Conversation"
      && messages.length === 0;

    if (shouldUpdateTitle) {
      const newTitle = createConversationTitle(message);

      try {
        const updatedConversation = await updateConversationTitle(
          selectedConversationId,
          newTitle,
        );

        setConversations((currentConversations) =>
          currentConversations.map((conversation) =>
            conversation.id === updatedConversation.id
              ? updatedConversation
              : conversation,
          ),
        );
      } catch {
        // Title update is non-blocking.
      }
    }

    await sendMessage(message);
  };

  const handleSelectConversation = (conversationId: string): void => {
    if (conversationId === selectedConversationId) {
      return;
    }

    stopStreaming();
    setSelectedConversationId(conversationId);
  };

  const isChatReady =
    !isLoadingConversations
    && selectedConversationId.length > 0
    && chatAgentId != null;

  return (
    <Box
      sx={{
        display: "flex",
        minHeight: "calc(100vh - 96px)",
        mx: { xs: -2, md: -2.5 },
        mb: { xs: -2, md: -2.5 },
        mt: { xs: -2, md: -2.5 },
        borderTop: 1,
        borderColor: "divider",
      }}
    >
      <ConversationSidebar
        conversations={isLoadingConversations ? [] : conversations}
        selectedConversationId={selectedConversationId}
        onSelect={handleSelectConversation}
        onNewConversation={() => {
          void handleNewConversation();
        }}
        onDeleteConversation={handleDeleteConversation}
      />

      <Box sx={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <Paper
          variant="outlined"
          square
          sx={{
            flex: 1,
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
            borderTop: 0,
            borderRight: 0,
            borderBottom: 0,
          }}
        >
          <Box sx={{ px: 2.5, py: 1.75 }}>
            <PageHeader
              title="Knowledge Chat"
              description="Ask questions about uploaded banking policies and compliance documents."
              actions={
                <Button
                  variant="outlined"
                  color="error"
                  size="small"
                  startIcon={<DeleteOutlinedIcon />}
                  onClick={clearMessages}
                  disabled={messages.length === 0 || isStreaming}
                >
                  Clear
                </Button>
              }
            />
          </Box>

          <Divider />

          {conversationError && (
            <Alert severity="error" sx={{ mx: 2.5, mt: 2 }}>
              {conversationError}
            </Alert>
          )}

          <Box
            sx={{
              flexGrow: 1,
              overflowY: "auto",
              px: 2.5,
              py: 2,
              bgcolor: "grey.50",
            }}
          >
            {messages.length === 0 ? (
              <EmptyState
                title="Start a conversation"
                description="Try asking about account policies, transaction limits, customer verification, fraud controls, or compliance requirements."
              />
            ) : (
              <Stack spacing={1}>
                {messages.map((message) => (
                  <Box key={message.id}>
                    <MessageBubble message={message} />

                    {message.role === "ASSISTANT" && (
                      <Box sx={{ ml: 6, maxWidth: "80%" }}>
                        <SourcePanel sources={message.sources} />
                      </Box>
                    )}
                  </Box>
                ))}

                <div ref={messagesEndRef} />
              </Stack>
            )}
          </Box>

          <Divider />

          <Box sx={{ p: 2, bgcolor: "background.paper" }}>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {error}
              </Alert>
            )}

            <ChatInput
              isStreaming={isStreaming}
              disabled={!isChatReady}
              onSend={handleSendMessage}
              onStop={stopStreaming}
            />
          </Box>
        </Paper>
      </Box>
    </Box>
  );
}
