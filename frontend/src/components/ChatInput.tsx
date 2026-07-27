import { useState } from "react";
import {
  Box,
  IconButton,
  Paper,
  TextField,
  Tooltip,
} from "@mui/material";
import SendIcon from "@mui/icons-material/Send";
import StopCircleIcon from "@mui/icons-material/StopCircle";

interface ChatInputProps {
  isStreaming: boolean;
  disabled?: boolean;
  onSend: (message: string) => Promise<void>;
  onStop: () => void;
}

export default function ChatInput({
  isStreaming,
  disabled = false,
  onSend,
  onStop,
}: ChatInputProps) {
  const [message, setMessage] = useState("");

  async function handleSend(): Promise<void> {
    const trimmedMessage = message.trim();

    if (!trimmedMessage || isStreaming || disabled) {
      return;
    }

    setMessage("");
    await onSend(trimmedMessage);
  }

  function handleKeyDown(
    event: React.KeyboardEvent<HTMLDivElement>,
  ): void {
    if (
      event.key === "Enter" &&
      !event.shiftKey
    ) {
      event.preventDefault();
      void handleSend();
    }
  }

  return (
    <Paper
      elevation={3}
      sx={{
        p: 1,
        borderRadius: 3,
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "flex-end",
          gap: 1,
        }}
      >
        <TextField
          fullWidth
          multiline
          minRows={1}
          maxRows={6}
          placeholder="Ask something about your banking documents..."
          value={message}
          disabled={isStreaming || disabled}
          onChange={(event) =>
            setMessage(event.target.value)
          }
          onKeyDown={handleKeyDown}
          variant="outlined"
        />

        {isStreaming ? (
          <Tooltip title="Stop response">
            <IconButton
              color="error"
              onClick={onStop}
              size="large"
            >
              <StopCircleIcon />
            </IconButton>
          </Tooltip>
        ) : (
          <Tooltip title="Send message">
            <span>
              <IconButton
                color="primary"
                onClick={() => {
                  void handleSend();
                }}
                disabled={!message.trim() || disabled}
                size="large"
              >
                <SendIcon />
              </IconButton>
            </span>
          </Tooltip>
        )}
      </Box>
    </Paper>
  );
}