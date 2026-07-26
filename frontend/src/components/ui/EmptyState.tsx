import { Box, Typography } from "@mui/material";
import InboxOutlinedIcon from "@mui/icons-material/InboxOutlined";

export default function EmptyState({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <Box
      sx={{
        py: 5,
        px: 2,
        textAlign: "center",
        color: "text.secondary",
      }}
    >
      <InboxOutlinedIcon sx={{ fontSize: 32, mb: 1, opacity: 0.6 }} aria-hidden />
      <Typography variant="subtitle2" color="text.primary">
        {title}
      </Typography>
      {description && (
        <Typography variant="body2" sx={{ mt: 0.5, maxWidth: 420, mx: "auto" }}>
          {description}
        </Typography>
      )}
    </Box>
  );
}
