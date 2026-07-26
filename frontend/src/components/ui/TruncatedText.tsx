import ContentCopyOutlinedIcon from "@mui/icons-material/ContentCopyOutlined";
import { IconButton, Stack, Tooltip, Typography } from "@mui/material";
import { useState } from "react";

export default function TruncatedText({
  value,
  maxWidth = 160,
  monospace = false,
  copyable = true,
}: {
  value: string;
  maxWidth?: number | string;
  monospace?: boolean;
  copyable?: boolean;
}) {
  const [copied, setCopied] = useState(false);

  async function handleCopy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: "center", minWidth: 0 }}>
      <Tooltip title={value} enterDelay={400}>
        <Typography
          variant="body2"
          noWrap
          sx={{
            maxWidth,
            fontFamily: monospace ? "monospace" : undefined,
            fontSize: monospace ? "0.75rem" : undefined,
          }}
        >
          {value}
        </Typography>
      </Tooltip>
      {copyable && (
        <Tooltip title={copied ? "Copied" : "Copy"}>
          <IconButton
            size="small"
            aria-label={`Copy ${value}`}
            onClick={(event) => {
              event.stopPropagation();
              void handleCopy();
            }}
          >
            <ContentCopyOutlinedIcon sx={{ fontSize: 14 }} />
          </IconButton>
        </Tooltip>
      )}
    </Stack>
  );
}
