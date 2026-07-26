import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";

import { AuthProvider } from "./context/AuthContext";
import { NotificationProvider } from "./context/NotificationContext";
import AppShell from "./components/AppShell";
import ProtectedRoute from "./components/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import ChatPage from "./pages/ChatPage";
import CustomerDetailsPage from "./pages/CustomerDetailsPage";
import CustomersPage from "./pages/CustomersPage";
import CreateInvestigationPage from "./pages/CreateInvestigationPage";
import DashboardPage from "./pages/DashboardPage";
import OperationsCenterPage from "./pages/OperationsCenterPage";
import AnalystQueuePage from "./pages/AnalystQueuePage";
import NotificationsPage from "./pages/NotificationsPage";
import InvestigationExplainabilityPage from "./pages/InvestigationExplainabilityPage";
import InvestigationReviewPage from "./pages/InvestigationReviewPage";
import InvestigationWorkspacePage from "./pages/InvestigationWorkspacePage";
import InvestigationsPage from "./pages/InvestigationsPage";
import SuspiciousTransactionsPage from "./pages/SuspiciousTransactionsPage";
import LiveTransactionsPage from "./pages/LiveTransactionsPage";
import TransactionDetailsPage from "./pages/TransactionDetailsPage";

function App() {
  return (
    <AuthProvider>
      <NotificationProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppShell />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/operations" element={<OperationsCenterPage />} />
              <Route path="/analyst-queue" element={<AnalystQueuePage />} />
              <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/customers" element={<CustomersPage />} />
          <Route
            path="/customers/:customerId"
            element={<CustomerDetailsPage />}
          />
          <Route
            path="/transactions/suspicious"
            element={<SuspiciousTransactionsPage />}
          />
          <Route
            path="/transactions/live"
            element={<LiveTransactionsPage />}
          />
          <Route
            path="/transactions/:transactionId"
            element={<TransactionDetailsPage />}
          />
          <Route
            path="/investigations"
            element={<InvestigationsPage />}
          />
          <Route
            path="/investigations/new"
            element={<CreateInvestigationPage />}
          />
          <Route
            path="/investigations/:investigationId"
            element={<InvestigationWorkspacePage />}
          />
          <Route
            path="/investigations/:investigationId/review"
            element={<InvestigationReviewPage />}
          />
          <Route
            path="/investigations/:investigationId/explainability"
            element={<InvestigationExplainabilityPage />}
          />
              <Route path="/chat" element={<ChatPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
      </NotificationProvider>
    </AuthProvider>
  );
}

export default App;