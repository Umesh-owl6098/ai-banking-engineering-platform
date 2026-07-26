import { Box, CircularProgress, Skeleton, Stack, Typography } from "@mui/material";

export function LoadingSpinner({
  label = "Loading…",
}: {
  label?: string;
}) {
  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        py: 6,
        gap: 1.5,
      }}
    >
      <CircularProgress size={28} aria-hidden />
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
}

export function TableSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <Stack spacing={1} sx={{ p: 2 }}>
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} variant="rounded" height={36} />
      ))}
    </Stack>
  );
}
