import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { getCurrentUser, login as loginRequest } from "../services/authService";
import type { AuthUser, LoginRequest } from "../types/auth";
import { AUTH_STORAGE_KEY } from "../types/auth";

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<void>;
  logout: () => void;
  canCreateInvestigation: boolean;
  canExecuteInvestigation: boolean;
  canReviewInvestigation: boolean;
  canDecideInvestigation: boolean;
  canRequestMoreInvestigation: boolean;
  canUpdateInvestigationStatus: boolean;
  canAssignInvestigation: boolean;
  canClaimInvestigation: boolean;
  isReadOnly: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(
  undefined,
);

export { AuthContext };

interface StoredSession {
  token: string;
  user: AuthUser;
}

function readStoredSession(): StoredSession | null {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

function writeStoredSession(session: StoredSession | null): void {
  if (!session) {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return;
  }

  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const logout = useCallback(() => {
    setUser(null);
    setToken(null);
    writeStoredSession(null);
  }, []);

  const login = useCallback(async (request: LoginRequest) => {
    const response = await loginRequest(request);
    const nextUser: AuthUser = {
      id: response.userId,
      username: response.username,
      role: response.role,
    };
    setToken(response.accessToken);
    setUser(nextUser);
    writeStoredSession({
      token: response.accessToken,
      user: nextUser,
    });
  }, []);

  useEffect(() => {
    let isCurrent = true;

    async function restoreSession(): Promise<void> {
      const stored = readStoredSession();
      if (!stored) {
        if (isCurrent) {
          setIsLoading(false);
        }
        return;
      }

      setToken(stored.token);
      setUser(stored.user);

      try {
        const currentUser = await getCurrentUser();
        if (isCurrent) {
          setUser(currentUser);
          writeStoredSession({
            token: stored.token,
            user: currentUser,
          });
        }
      } catch {
        if (isCurrent) {
          logout();
        }
      } finally {
        if (isCurrent) {
          setIsLoading(false);
        }
      }
    }

    void restoreSession();

    return () => {
      isCurrent = false;
    };
  }, [logout]);

  const value = useMemo<AuthContextValue>(() => {
    const role = user?.role;
    const isReadOnly = role === "READ_ONLY";

    return {
      user,
      token,
      isLoading,
      isAuthenticated: Boolean(user && token),
      login,
      logout,
      canCreateInvestigation:
        role === "ADMIN"
        || role === "SUPERVISOR"
        || role === "FRAUD_ANALYST",
      canExecuteInvestigation:
        role === "ADMIN" || role === "SUPERVISOR",
      canReviewInvestigation:
        role === "ADMIN"
        || role === "SUPERVISOR"
        || role === "COMPLIANCE_ANALYST",
      canDecideInvestigation:
        role === "ADMIN"
        || role === "SUPERVISOR"
        || role === "COMPLIANCE_ANALYST",
      canRequestMoreInvestigation:
        role === "ADMIN"
        || role === "SUPERVISOR"
        || role === "FRAUD_ANALYST"
        || role === "COMPLIANCE_ANALYST",
      canUpdateInvestigationStatus:
        role === "ADMIN"
        || role === "SUPERVISOR"
        || role === "FRAUD_ANALYST",
      canAssignInvestigation:
        role === "ADMIN" || role === "SUPERVISOR",
      canClaimInvestigation:
        role === "FRAUD_ANALYST" || role === "COMPLIANCE_ANALYST",
      isReadOnly,
    };
  }, [user, token, isLoading, login, logout]);

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  );
}
