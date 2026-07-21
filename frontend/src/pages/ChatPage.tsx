import { useEffect, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Container,
  Divider,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";

import ChatInput from "../components/ChatInput";
import ConversationSidebar from "../components/ConversationSidebar";
import MessageBubble from "../components/MessageBubble";
import SourcePanel from "../components/SourcePanel";
import { useStreamingChat } from "../hooks/useStreamingChat";
import {
  createConversation,
  deleteConversation,
  getProjectConversations,
  updateConversationTitle,
} from "../services/conversationService";
import type { Conversation } from "../types/conversation";

const CONVERSATION_ID =
  "d6a43fe7-d27e-41fd-96c4-cbb497b3732b";

const PROJECT_ID =
  "8c0c0dee-dd8e-4419-bef3-a2e93c10a726";

const AGENT_ID =
  "c964b4de-f07a-4b61-bb53-18144b06f1fa";

function createConversationTitle(
  message: string,
): string {
  const cleanMessage = message
    .trim()
    .replace(/\s+/g, " ");

  if (cleanMessage.length <= 55) {
    return cleanMessage;
  }

  return `${cleanMessage.slice(0, 52)}...`;
}

export default function ChatPage() {
  const messagesEndRef =
    useRef<HTMLDivElement | null>(null);

  const [conversations, setConversations] =
    useState<Conversation[]>([]);

  const [
    selectedConversationId,
    setSelectedConversationId,
  ] = useState(CONVERSATION_ID);

  const [
    isLoadingConversations,
    setIsLoadingConversations,
  ] = useState(true);

  const {
    messages,
    isStreaming,
    error,
    sendMessage,
    stopStreaming,
    clearMessages,
  } = useStreamingChat(selectedConversationId);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({
      behavior: "smooth",
    });
  }, [messages]);

  useEffect(() => {
    const loadConversations = async () => {
      try {
        setIsLoadingConversations(true);

        const data =
          await getProjectConversations(
            PROJECT_ID,
          );

        setConversations(data);

        if (data.length > 0) {
          const currentConversationExists =
            data.some(
              (conversation) =>
                conversation.id ===
                selectedConversationId,
            );

          if (!currentConversationExists) {
            setSelectedConversationId(
              data[0].id,
            );
          }
        }
      } catch (loadError) {
        console.error(
          "Failed to load conversations:",
          loadError,
        );
      } finally {
        setIsLoadingConversations(false);
      }
    };

    void loadConversations();
  }, []);

  const handleNewConversation =
    async (): Promise<Conversation | null> => {
      try {
        const newConversation =
          await createConversation({
            projectId: PROJECT_ID,
            agentId: AGENT_ID,
            title: "New Banking Conversation",
          });

        setConversations(
          (currentConversations) => [
            newConversation,
            ...currentConversations,
          ],
        );

        setSelectedConversationId(
          newConversation.id,
        );

        return newConversation;
      } catch (createError) {
        console.error(
          "Failed to create conversation:",
          createError,
        );

        return null;
      }
    };

  const handleDeleteConversation = async (
    conversationId: string,
  ): Promise<void> => {
    try {
      await deleteConversation(
        conversationId,
      );

      const remainingConversations =
        conversations.filter(
          (conversation) =>
            conversation.id !==
            conversationId,
        );

      setConversations(
        remainingConversations,
      );

      const deletedSelectedConversation =
        conversationId ===
        selectedConversationId;

      if (!deletedSelectedConversation) {
        return;
      }

      stopStreaming();

      if (
        remainingConversations.length > 0
      ) {
        setSelectedConversationId(
          remainingConversations[0].id,
        );
      } else {
        await handleNewConversation();
      }
    } catch (deleteError) {
      console.error(
        "Failed to delete conversation:",
        deleteError,
      );

      window.alert(
        "Unable to delete the conversation. Please try again.",
      );
    }
  };

  const handleSendMessage = async (
    message: string,
  ): Promise<void> => {
    const selectedConversation =
      conversations.find(
        (conversation) =>
          conversation.id ===
          selectedConversationId,
      );

    const shouldUpdateTitle =
      selectedConversation?.title ===
        "New Banking Conversation" &&
      messages.length === 0;

    if (shouldUpdateTitle) {
      const newTitle =
        createConversationTitle(message);

      try {
        const updatedConversation =
          await updateConversationTitle(
            selectedConversationId,
            newTitle,
          );

        setConversations(
          (currentConversations) =>
            currentConversations.map(
              (conversation) =>
                conversation.id ===
                updatedConversation.id
                  ? updatedConversation
                  : conversation,
            ),
        );
      } catch (titleError) {
        console.error(
          "Failed to update conversation title:",
          titleError,
        );
      }
    }

    await sendMessage(message);
  };

  return (
    <Container
      maxWidth={false}
      disableGutters
      sx={{
        height: "100vh",
      }}
    >
      <Box
        sx={{
          display: "flex",
          height: "100%",
        }}
      >
        <ConversationSidebar
          conversations={
            isLoadingConversations
              ? []
              : conversations
          }
          selectedConversationId={
            selectedConversationId
          }
          onSelect={
            setSelectedConversationId
          }
          onNewConversation={() => {
            void handleNewConversation();
          }}
          onDeleteConversation={
            handleDeleteConversation
          }
        />

        <Box
          sx={{
            flex: 1,
            p: 3,
            minWidth: 0,
          }}
        >
          <Paper
            elevation={3}
            sx={{
              height:
                "calc(100vh - 48px)",
              display: "flex",
              flexDirection: "column",
              overflow: "hidden",
              borderRadius: 3,
            }}
          >
            <Box
              sx={{
                px: 3,
                py: 2,
                display: "flex",
                justifyContent:
                  "space-between",
                alignItems: "center",
              }}
            >
              <Box>
                <Typography variant="h5">
                  AI Banking Assistant
                </Typography>

                <Typography
                  variant="body2"
                  color="text.secondary"
                >
                  Ask questions about your
                  uploaded banking documents.
                </Typography>
              </Box>

              <Button
                variant="outlined"
                color="error"
                startIcon={
                  <DeleteOutlinedIcon />
                }
                onClick={clearMessages}
                disabled={
                  messages.length === 0 ||
                  isStreaming
                }
              >
                Clear
              </Button>
            </Box>

            <Divider />

            <Box
              sx={{
                flexGrow: 1,
                overflowY: "auto",
                px: 3,
                py: 2,
                bgcolor: "grey.50",
              }}
            >
              {messages.length === 0 ? (
                <Box
                  sx={{
                    height: "100%",
                    display: "flex",
                    justifyContent:
                      "center",
                    alignItems: "center",
                    textAlign: "center",
                  }}
                >
                  <Box>
                    <Typography
                      variant="h6"
                      gutterBottom
                    >
                      Start a conversation
                    </Typography>

                    <Typography
                      color="text.secondary"
                      sx={{
                        maxWidth: 500,
                      }}
                    >
                      Try asking about account
                      policies, transaction
                      limits, customer
                      verification, fraud
                      controls, or compliance
                      requirements.
                    </Typography>
                  </Box>
                </Box>
              ) : (
                <Stack spacing={1}>
                  {messages.map(
                    (message) => (
                      <Box key={message.id}>
                        <MessageBubble
                          message={message}
                        />

                        {message.role ===
                          "ASSISTANT" && (
                          <Box
                            sx={{
                              ml: 6,
                              maxWidth:
                                "80%",
                            }}
                          >
                            <SourcePanel
                              sources={
                                message.sources
                              }
                            />
                          </Box>
                        )}
                      </Box>
                    ),
                  )}

                  <div
                    ref={messagesEndRef}
                  />
                </Stack>
              )}
            </Box>

            <Divider />

            <Box
              sx={{
                p: 2,
                bgcolor:
                  "background.paper",
              }}
            >
              {error && (
                <Alert
                  severity="error"
                  sx={{ mb: 2 }}
                >
                  {error}
                </Alert>
              )}

              <ChatInput
                isStreaming={isStreaming}
                onSend={
                  handleSendMessage
                }
                onStop={stopStreaming}
              />
            </Box>
          </Paper>
        </Box>
      </Box>
    </Container>
  );
}