import { Box, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";

export default function PageHeader({
  title,
  description,
  actions,
  meta,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  meta?: ReactNode;
}) {
  return (
    <Stack
      direction={{ xs: "column", md: "row" }}
      spacing={1.5}
      sx={{
        alignItems: { md: "flex-start" },
        justifyContent: "space-between",
        mb: 0.5,
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h5" component="h1">
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {description}
          </Typography>
        )}
        {meta && <Box sx={{ mt: 1 }}>{meta}</Box>}
      </Box>
      {actions && (
        <Stack direction="row" spacing={1} sx={{ flexShrink: 0, flexWrap: "wrap" }}>
          {actions}
        </Stack>
      )}
    </Stack>
  );
}
