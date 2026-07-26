import { Chip } from "@mui/material";

export default function ConnectionStatusChip({
  state,
  label,
}: {
  state: "connecting" | "connected" | "disconnected";
  label?: string;
}) {
  const defaultLabel =
    state === "connected"
      ? "Connected"
      : state === "connecting"
        ? "Reconnecting"
        : "Disconnected";

  const color =
    state === "connected"
      ? "success"
      : state === "connecting"
        ? "warning"
        : "default";

  return (
    <Chip
      size="small"
      label={label ?? defaultLabel}
      color={color}
      variant="outlined"
      aria-live="polite"
    />
  );
}
