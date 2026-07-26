export interface AnalystQueueItem {
  investigationId: string;
  reference: string;
  customerName: string;
  severity: string;
  triggerReason: string;
  status: string;
  assignedAnalystUsername: string | null;
  waitingDurationMs: number;
  assignedAt: string | null;
  reviewStartedAt: string | null;
  updatedAt: string;
}

export interface AnalystQueueResponse {
  myQueue: AnalystQueueItem[];
  unassigned: AnalystQueueItem[];
  inReview: AnalystQueueItem[];
  escalated: AnalystQueueItem[];
  allAssigned: AnalystQueueItem[];
}

export interface AssignableAnalyst {
  id: string;
  username: string;
  role: string;
}

export interface AssignInvestigationRequest {
  assigneeUsername: string;
  notes?: string;
}
