import { Box, Typography } from "@mui/material";
import type { ReactNode } from "react";

export default function SummaryField({
  label,
  value,
}: {
  label: string;
  value: ReactNode;
}) {
  const displayValue =
    typeof value === "boolean" ? (value ? "Yes" : "No") : (value ?? "—");

  return (
    <Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
        {label}
      </Typography>
      {typeof displayValue === "string" || typeof displayValue === "number" ? (
        <Typography variant="body2">{displayValue}</Typography>
      ) : (
        displayValue
      )}
    </Box>
  );
}
