export type NotificationType =
  | "CRITICAL_INVESTIGATION_CREATED"
  | "INVESTIGATION_ASSIGNED"
  | "INVESTIGATION_CLAIMED"
  | "INVESTIGATION_REASSIGNED"
  | "REPORT_GENERATED"
  | "INVESTIGATION_ESCALATED"
  | "AI_EXECUTION_FAILURE"
  | "INVESTIGATION_WAITING_TOO_LONG"
  | "OPENAI_FALLBACK_MODE";

export type NotificationSeverity = "INFO" | "WARNING" | "CRITICAL";

export interface AppNotification {
  id: string;
  userId: string;
  title: string;
  message: string;
  type: NotificationType;
  severity: NotificationSeverity;
  relatedInvestigationId: string | null;
  relatedTransactionId: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPageResponse {
  content: AppNotification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface UnreadCountResponse {
  unreadCount: number;
}
