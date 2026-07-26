import { Chip } from "@mui/material";

import {
  executionStatusChipColor,
  formatStatusLabel,
  investigationStatusChipColor,
  screeningStatusChipColor,
  severityChipColor,
} from "../../utils/statusBadges";

type StatusKind = "severity" | "investigation" | "screening" | "execution";

function colorFor(kind: StatusKind, value: string) {
  switch (kind) {
    case "severity":
      return severityChipColor(value);
    case "investigation":
      return investigationStatusChipColor(value);
    case "screening":
      return screeningStatusChipColor(value);
    case "execution":
      return executionStatusChipColor(value);
    default:
      return "default" as const;
  }
}

export default function StatusChip({
  kind,
  value,
  size = "small",
  variant = "filled",
}: {
  kind: StatusKind;
  value: string;
  size?: "small" | "medium";
  variant?: "filled" | "outlined";
}) {
  return (
    <Chip
      size={size}
      variant={variant}
      color={colorFor(kind, value)}
      label={formatStatusLabel(value)}
      aria-label={`${kind} ${formatStatusLabel(value)}`}
    />
  );
}
