import LogoutOutlinedIcon from "@mui/icons-material/LogoutOutlined";
import NotificationsNoneOutlinedIcon from "@mui/icons-material/NotificationsNoneOutlined";
import QueueOutlinedIcon from "@mui/icons-material/QueueOutlined";
import MonitorHeartOutlinedIcon from "@mui/icons-material/MonitorHeartOutlined";
import DashboardOutlinedIcon from "@mui/icons-material/DashboardOutlined";
import GroupsOutlinedIcon from "@mui/icons-material/GroupsOutlined";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import SensorsOutlinedIcon from "@mui/icons-material/SensorsOutlined";
import AssignmentOutlinedIcon from "@mui/icons-material/AssignmentOutlined";
import ChatOutlinedIcon from "@mui/icons-material/ChatOutlined";
import {
  Box,
  Button,
  Chip,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Typography,
} from "@mui/material";
import { NavLink, Outlet, useNavigate } from "react-router-dom";

import NotificationBell from "./notifications/NotificationBell";
import { useAuth } from "../hooks/useAuth";
import { layout } from "../theme/tokens";
import { roleChipColor } from "../utils/statusBadges";

const navigation = [
  { label: "Dashboard", path: "/", icon: <DashboardOutlinedIcon fontSize="small" /> },
  {
    label: "Operations Center",
    path: "/operations",
    icon: <MonitorHeartOutlinedIcon fontSize="small" />,
  },
  { label: "Customers", path: "/customers", icon: <GroupsOutlinedIcon fontSize="small" /> },
  {
    label: "Suspicious Transactions",
    path: "/transactions/suspicious",
    icon: <WarningAmberOutlinedIcon fontSize="small" />,
  },
  {
    label: "Live Transactions",
    path: "/transactions/live",
    icon: <SensorsOutlinedIcon fontSize="small" />,
  },
  {
    label: "Analyst Queue",
    path: "/analyst-queue",
    icon: <QueueOutlinedIcon fontSize="small" />,
  },
  {
    label: "Notifications",
    path: "/notifications",
    icon: <NotificationsNoneOutlinedIcon fontSize="small" />,
  },
  {
    label: "Investigations",
    path: "/investigations",
    icon: <AssignmentOutlinedIcon fontSize="small" />,
  },
  { label: "Knowledge Chat", path: "/chat", icon: <ChatOutlinedIcon fontSize="small" /> },
];

export default function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout(): void {
    logout();
    navigate("/login");
  }

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "background.default" }}>
      <Box
        component="nav"
        aria-label="Primary"
        sx={{
          width: layout.sidebarWidth,
          flexShrink: 0,
          borderRight: 1,
          borderColor: "divider",
          bgcolor: "background.paper",
          display: "flex",
          flexDirection: "column",
          minHeight: "100vh",
        }}
      >
        <Toolbar sx={{ py: 1.5, alignItems: "flex-start" }}>
          <Typography
            variant="subtitle1"
            sx={{ fontWeight: 700, lineHeight: 1.3, fontSize: "0.95rem" }}
          >
            AI Financial Crime Operations
          </Typography>
        </Toolbar>

        <Divider />

        <List dense sx={{ px: 1, py: 1, flexGrow: 1 }}>
          {navigation.map((item) => (
            <ListItemButton
              key={item.path}
              component={NavLink}
              to={item.path}
              end={item.path === "/"}
              sx={{
                borderRadius: 1.5,
                mb: 0.25,
                "&.active": {
                  bgcolor: "primary.main",
                  color: "primary.contrastText",
                  "& .MuiListItemIcon-root": { color: "inherit" },
                  "& .MuiTypography-root": { fontWeight: 700 },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 32 }}>{item.icon}</ListItemIcon>
              <ListItemText
                primary={item.label}
                slotProps={{ primary: { sx: { fontSize: "0.8125rem" } } }}
              />
            </ListItemButton>
          ))}
        </List>

        <Divider />

        <Box sx={{ p: 1.5 }}>
          <Stack spacing={1}>
            <Box>
              <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                {user?.username ?? "Unknown user"}
              </Typography>
              <Chip
                label={user?.role ?? "UNKNOWN"}
                size="small"
                color={roleChipColor(user?.role ?? "")}
                sx={{ mt: 0.5 }}
              />
            </Box>
            <Button
              variant="outlined"
              size="small"
              startIcon={<LogoutOutlinedIcon />}
              onClick={handleLogout}
              fullWidth
            >
              Sign Out
            </Button>
          </Stack>
        </Box>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          display: "flex",
          flexDirection: "column",
          minHeight: "100vh",
        }}
      >
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "flex-end",
            px: layout.pagePadding,
            py: 1,
            borderBottom: 1,
            borderColor: "divider",
            bgcolor: "background.paper",
          }}
        >
          <NotificationBell />
        </Box>
        <Box sx={{ flexGrow: 1, p: layout.pagePadding }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
