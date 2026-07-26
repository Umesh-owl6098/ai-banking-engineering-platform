import { createTheme } from "@mui/material/styles";

import { layout } from "./tokens";

export const appTheme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#1e3a5f",
      light: "#e8eef4",
      contrastText: "#ffffff",
    },
    secondary: {
      main: "#475569",
    },
    background: {
      default: "#f4f6f8",
      paper: "#ffffff",
    },
    text: {
      primary: "#0f172a",
      secondary: "#475569",
    },
    divider: "#e2e8f0",
    success: { main: "#15803d" },
    warning: { main: "#b45309" },
    error: { main: "#b91c1c" },
    info: { main: "#0369a1" },
  },
  typography: {
    fontFamily:
      '"IBM Plex Sans", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    h4: { fontSize: "1.375rem", fontWeight: 700, lineHeight: 1.25 },
    h5: { fontSize: "1.125rem", fontWeight: 700, lineHeight: 1.3 },
    h6: { fontSize: "1rem", fontWeight: 600, lineHeight: 1.35 },
    subtitle1: { fontSize: "0.9375rem", fontWeight: 600 },
    subtitle2: { fontSize: "0.875rem", fontWeight: 600 },
    body2: { fontSize: "0.875rem" },
    caption: { fontSize: "0.75rem" },
    overline: {
      fontSize: "0.6875rem",
      fontWeight: 600,
      letterSpacing: "0.06em",
      textTransform: "uppercase",
    },
  },
  shape: { borderRadius: 8 },
  spacing: 8,
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        "#root": {
          width: "100%",
          maxWidth: "none",
          margin: 0,
          textAlign: "initial",
          border: "none",
          minHeight: "100svh",
        },
        body: {
          margin: 0,
          backgroundColor: "#f4f6f8",
        },
        "*:focus-visible": {
          outline: "2px solid #0369a1",
          outlineOffset: 2,
        },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { textTransform: "none", fontWeight: 600 },
      },
    },
    MuiCard: {
      defaultProps: { variant: "outlined" },
      styleOverrides: {
        root: {
          borderColor: "#e2e8f0",
          boxShadow: "0 1px 2px rgba(15, 23, 42, 0.06)",
        },
      },
    },
    MuiPaper: {
      defaultProps: { variant: "outlined" },
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          fontWeight: 600,
          backgroundColor: "#f8fafc",
          whiteSpace: "nowrap",
        },
        root: {
          fontSize: "0.8125rem",
          verticalAlign: "top",
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, fontSize: "0.6875rem" },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: { textTransform: "none", fontWeight: 600, minHeight: 44 },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          minHeight: 40,
          paddingTop: 6,
          paddingBottom: 6,
        },
      },
    },
    MuiListItemIcon: {
      styleOverrides: {
        root: {
          minWidth: 36,
          color: "inherit",
        },
      },
    },
    MuiToolbar: {
      styleOverrides: {
        root: {
          minHeight: `${layout.sidebarWidth / 3}px !important`,
          paddingLeft: 16,
          paddingRight: 16,
        },
      },
    },
  },
});
