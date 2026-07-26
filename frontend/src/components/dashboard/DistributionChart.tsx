import { Box, LinearProgress, Stack, Typography } from "@mui/material";

export default function DistributionChart({
  title,
  data,
  color = "primary.main",
}: {
  title: string;
  data: { label: string; value: number }[];
  color?: string;
}) {
  const maxValue = Math.max(...data.map((item) => item.value), 1);

  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 600 }}>
        {title}
      </Typography>
      {data.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No data available.
        </Typography>
      ) : (
        <Stack spacing={1.25}>
          {data.map((item) => (
            <Box key={item.label}>
              <Stack
                direction="row"
                spacing={1}
                sx={{ justifyContent: "space-between", mb: 0.5 }}
              >
                <Typography variant="body2">{item.label}</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {item.value}
                </Typography>
              </Stack>
              <LinearProgress
                variant="determinate"
                value={(item.value / maxValue) * 100}
                sx={{
                  height: 8,
                  borderRadius: 1,
                  bgcolor: "grey.200",
                  "& .MuiLinearProgress-bar": {
                    bgcolor: color,
                    borderRadius: 1,
                  },
                }}
              />
            </Box>
          ))}
        </Stack>
      )}
    </Box>
  );
}
