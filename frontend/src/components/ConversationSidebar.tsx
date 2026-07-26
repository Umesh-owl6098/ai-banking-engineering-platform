import {
  Box,
  Button,
  Divider,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Tooltip,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";

import type { Conversation } from "../types/conversation";

interface ConversationSidebarProps {
  conversations: Conversation[];
  selectedConversationId: string;
  onSelect: (conversationId: string) => void;
  onNewConversation: () => void;
  onDeleteConversation: (
    conversationId: string,
  ) => Promise<void>;
}

export default function ConversationSidebar({
  conversations,
  selectedConversationId,
  onSelect,
  onNewConversation,
  onDeleteConversation,
}: ConversationSidebarProps) {
  const handleDelete = async (
    event: React.MouseEvent<HTMLButtonElement>,
    conversation: Conversation,
  ) => {
    event.stopPropagation();

    const confirmed = window.confirm(
      `Delete "${conversation.title || "Untitled Conversation"}"?`,
    );

    if (!confirmed) {
      return;
    }

    await onDeleteConversation(conversation.id);
  };

  return (
    <Paper
      variant="outlined"
      square
      sx={{
        width: 260,
        flexShrink: 0,
        display: "flex",
        flexDirection: "column",
        height: "100%",
        borderTop: 0,
        borderBottom: 0,
        borderLeft: 0,
        borderRadius: 0,
      }}
    >
      <Box sx={{ p: 1.75 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          Conversations
        </Typography>

        <Button
          fullWidth
          variant="contained"
          size="small"
          startIcon={<AddIcon />}
          sx={{ mt: 1.5 }}
          onClick={onNewConversation}
        >
          New Conversation
        </Button>
      </Box>

      <Divider />

      <List
        sx={{
          flexGrow: 1,
          overflowY: "auto",
        }}
      >
        {conversations.map((conversation) => (
          <ListItemButton
            key={conversation.id}
            selected={
              conversation.id ===
              selectedConversationId
            }
            onClick={() =>
              onSelect(conversation.id)
            }
            sx={{
              pr: 1,
            }}
          >
            <ListItemText
              primary={
                <Typography noWrap>
                  {conversation.title ||
                    "Untitled Conversation"}
                </Typography>
              }
              secondary={
                conversation.updatedAt
                  ? new Date(
                      conversation.updatedAt,
                    ).toLocaleDateString()
                  : ""
              }
            />

            <Tooltip title="Delete conversation">
              <IconButton
                size="small"
                color="error"
                onClick={(event) =>
                  void handleDelete(
                    event,
                    conversation,
                  )
                }
                aria-label="Delete conversation"
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </ListItemButton>
        ))}

        {conversations.length === 0 && (
          <Box
            sx={{
              p: 2,
              textAlign: "center",
            }}
          >
            <Typography
              variant="body2"
              color="text.secondary"
            >
              No conversations yet.
            </Typography>
          </Box>
        )}
      </List>
    </Paper>
  );
}