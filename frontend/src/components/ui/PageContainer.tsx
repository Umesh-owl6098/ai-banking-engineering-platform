import { Box } from "@mui/material";
import type { ReactNode } from "react";

import { layout } from "../../theme/tokens";

export default function PageContainer({ children }: { children: ReactNode }) {
  return (
    <Box
      sx={{
        width: "100%",
        maxWidth: layout.pageMaxWidth,
        mx: "auto",
        minWidth: 0,
      }}
    >
      {children}
    </Box>
  );
}
