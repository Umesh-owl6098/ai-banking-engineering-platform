import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Paper,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import DescriptionIcon from "@mui/icons-material/Description";
import type { SourceReference } from "../types/chat";

interface SourcePanelProps {
  sources?: SourceReference[];
}

export default function SourcePanel({
  sources = [],
}: SourcePanelProps) {
  if (sources.length === 0) {
    return null;
  }

  return (
    <Box sx={{ mt: 1 }}>
      <Typography
        variant="subtitle2"
        gutterBottom
      >
        Sources
      </Typography>

      {sources.map((source, index) => (
        <Accordion
          key={index}
          disableGutters
          elevation={0}
        >
          <AccordionSummary
            expandIcon={<ExpandMoreIcon />}
          >
            <DescriptionIcon
              fontSize="small"
              sx={{ mr: 1 }}
            />

            <Typography
              sx={{ flexGrow: 1 }}
            >
              {source.fileName}
            </Typography>

            <Chip
              size="small"
              label={`${Math.round(
                source.similarity * 100,
              )}%`}
            />
          </AccordionSummary>

          <AccordionDetails>
            <Paper
              variant="outlined"
              sx={{
                p: 2,
                bgcolor: "grey.50",
              }}
            >
              <Typography
                variant="body2"
                sx={{
                  whiteSpace: "pre-wrap",
                }}
              >
                {source.preview}
              </Typography>

              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ mt: 2 }}
              >
                Chunk #{source.chunkIndex}
              </Typography>
            </Paper>
          </AccordionDetails>
        </Accordion>
      ))}
    </Box>
  );
}