import {
  Avatar,
  Box,
  Paper,
  Typography,
} from "@mui/material";
import PersonIcon from "@mui/icons-material/Person";
import SmartToyIcon from "@mui/icons-material/SmartToy";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { ChatMessage } from "../types/chat";

interface MessageBubbleProps {
  message: ChatMessage;
}

export default function MessageBubble({
  message,
}: MessageBubbleProps) {
  const isUser = message.role === "USER";

  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: isUser
          ? "flex-end"
          : "flex-start",
        mb: 2,
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexDirection: isUser
            ? "row-reverse"
            : "row",
          alignItems: "flex-start",
          gap: 1,
          maxWidth: "85%",
        }}
      >
        <Avatar
          sx={{
            width: 36,
            height: 36,
            bgcolor: isUser
              ? "primary.main"
              : "secondary.main",
          }}
        >
          {isUser ? (
            <PersonIcon fontSize="small" />
          ) : (
            <SmartToyIcon fontSize="small" />
          )}
        </Avatar>

        <Paper
          elevation={1}
          sx={{
            px: 2,
            py: 1.5,
            borderRadius: 3,
            bgcolor: isUser
              ? "primary.main"
              : "background.paper",
            color: isUser
              ? "primary.contrastText"
              : "text.primary",
            overflowWrap: "anywhere",
          }}
        >
          {isUser ? (
            <Typography
              variant="body1"
              sx={{
                whiteSpace: "pre-wrap",
              }}
            >
              {message.content}
            </Typography>
          ) : (
            <Box
              sx={{
                "& p": {
                  mt: 0,
                  mb: 1,
                },
                "& p:last-child": {
                  mb: 0,
                },
                "& pre": {
                  overflowX: "auto",
                  p: 1.5,
                  borderRadius: 1,
                  bgcolor: "grey.100",
                },
                "& code": {
                  fontFamily: "monospace",
                },
                "& ul, & ol": {
                  pl: 3,
                },
              }}
            >
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
              >
                {message.content ||
                  "Thinking..."}
              </ReactMarkdown>
            </Box>
          )}
        </Paper>
      </Box>
    </Box>
  );
}