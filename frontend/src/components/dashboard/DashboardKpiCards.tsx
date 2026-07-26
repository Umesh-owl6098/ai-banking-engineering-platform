import { Box, Card, CardContent, Grid, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";

export default function DashboardKpiCards({
  items,
}: {
  items: {
    label: string;
    value: string | number;
    hint?: string;
    icon: ReactNode;
    accent: string;
  }[];
}) {
  return (
    <Grid container spacing={2}>
      {items.map((item) => (
        <Grid key={item.label} size={{ xs: 12, sm: 6, lg: 3 }}>
          <Card
            variant="outlined"
            sx={{
              height: "100%",
              borderColor: "divider",
            }}
          >
            <CardContent sx={{ p: 2, "&:last-child": { pb: 2 } }}>
              <Stack
                direction="row"
                spacing={1.5}
                sx={{ justifyContent: "space-between", alignItems: "flex-start" }}
              >
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body2" color="text.secondary">
                    {item.label}
                  </Typography>
                  <Typography variant="h6" sx={{ mt: 0.25, fontWeight: 700 }}>
                    {item.value}
                  </Typography>
                  {item.hint && (
                    <Typography variant="caption" color="text.secondary">
                      {item.hint}
                    </Typography>
                  )}
                </Box>
                <Box
                  aria-hidden
                  sx={{
                    color: item.accent,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    width: 36,
                    height: 36,
                    borderRadius: 1.5,
                    bgcolor: "action.hover",
                    flexShrink: 0,
                  }}
                >
                  {item.icon}
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}
