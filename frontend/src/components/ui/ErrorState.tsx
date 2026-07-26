import { Alert, Button, Stack } from "@mui/material";
import { useNavigate } from "react-router-dom";

import { getApiErrorPresentation } from "../../utils/apiError";

export default function ErrorState({
  error,
  fallback,
  onRetry,
  severity,
  forbiddenMessage,
}: {
  error: unknown;
  fallback: string;
  onRetry?: () => void;
  severity?: "error" | "warning";
  forbiddenMessage?: string;
}) {
  const navigate = useNavigate();
  const presentation = getApiErrorPresentation(
    error,
    forbiddenMessage ?? fallback,
  );
  const resolvedSeverity = severity ?? presentation.severity;
  const showRetry = presentation.showRetry && onRetry != null;

  return (
    <Alert
      severity={resolvedSeverity}
      action={
        presentation.redirectToLogin ? (
          <Button
            color="inherit"
            size="small"
            onClick={() => navigate("/login")}
          >
            Sign in
          </Button>
        ) : showRetry ? (
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        ) : undefined
      }
    >
      <Stack spacing={0.5}>
        <span>{presentation.message}</span>
        {presentation.hint && <span>{presentation.hint}</span>}
      </Stack>
    </Alert>
  );
}
