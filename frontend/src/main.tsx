import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { CssBaseline, ThemeProvider } from "@mui/material";

import App from "./App.tsx";
import AppToaster from "./components/ui/AppToaster.tsx";
import { appTheme } from "./theme/theme";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider theme={appTheme}>
      <CssBaseline />
      <App />
      <AppToaster />
    </ThemeProvider>
  </StrictMode>,
);
