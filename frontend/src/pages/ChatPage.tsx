import { useEffect, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Divider,
  Paper,
  Stack,
} from "@mui/material";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";

import ChatInput from "../components/ChatInput";
import ConversationSidebar from "../components/ConversationSidebar";
import MessageBubble from "../components/MessageBubble";
import SourcePanel from "../components/SourcePanel";
import EmptyState from "../components/ui/EmptyState";
import PageHeader from "../components/ui/PageHeader";
import { useStreamingChat } from "../hooks/useStreamingChat";
import {
  createConversation,
  deleteConversation,
  getProjectConversations,
  updateConversationTitle,
} from "../services/conversationService";
import type { Conversation } from "../types/conversation";

const CONVERSATION_ID = "d6a43fe7-d27e-41fd-96c4-cbb497b3732b";
const PROJECT_ID = "8c0c0dee-dd8e-4419-bef3-a2e93c10a726";
const AGENT_ID = "c964b4de-f07a-4b61-bb53-18144b06f1fa";

function createConversationTitle(message: string): string {
  const cleanMessage = message.trim().replace(/\s+/g, " ");

  if (cleanMessage.length <= 55) {
    return cleanMessage;
  }

  return `${cleanMessage.slice(0, 52)}...`;
}

export default function ChatPage() {
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedConversationId, setSelectedConversationId] =
    useState(CONVERSATION_ID);
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

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    const loadConversations = async () => {
      try {
        setIsLoadingConversations(true);
        setConversationError(null);

        const data = await getProjectConversations(PROJECT_ID);
        setConversations(data);

        if (data.length > 0) {
          const currentConversationExists = data.some(
            (conversation) => conversation.id === selectedConversationId,
          );

          if (!currentConversationExists) {
            setSelectedConversationId(data[0].id);
          }
        }
      } catch (loadError) {
        setConversationError(
          loadError instanceof Error
            ? loadError.message
            : "Unable to load conversations.",
        );
      } finally {
        setIsLoadingConversations(false);
      }
    };

    void loadConversations();
  }, []);

  const handleNewConversation = async (): Promise<Conversation | null> => {
    try {
      const newConversation = await createConversation({
        projectId: PROJECT_ID,
        agentId: AGENT_ID,
        title: "New Banking Conversation",
      });

      setConversations((currentConversations) => [
        newConversation,
        ...currentConversations,
      ]);
      setSelectedConversationId(newConversation.id);
      return newConversation;
    } catch {
      setConversationError("Unable to create a new conversation.");
      return null;
    }
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
      } else {
        await handleNewConversation();
      }
    } catch {
      window.alert("Unable to delete the conversation. Please try again.");
    }
  };

  const handleSendMessage = async (message: string): Promise<void> => {
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
        onSelect={setSelectedConversationId}
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
              onSend={handleSendMessage}
              onStop={stopStreaming}
            />
          </Box>
        </Paper>
      </Box>
    </Box>
  );
}
