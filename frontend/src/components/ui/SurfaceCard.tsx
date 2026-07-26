import { Card, CardContent, Typography } from "@mui/material";
import type { ReactNode } from "react";

export default function SurfaceCard({
  title,
  children,
  id,
  action,
}: {
  title?: string;
  children: ReactNode;
  id?: string;
  action?: ReactNode;
}) {
  return (
    <Card id={id}>
      <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
        {title && (
          <Typography
            variant="subtitle1"
            component="h2"
            sx={{ mb: 1.5, fontWeight: 700 }}
          >
            {title}
          </Typography>
        )}
        {action}
        {children}
      </CardContent>
    </Card>
  );
}
