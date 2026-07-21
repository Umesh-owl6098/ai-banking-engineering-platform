export interface Conversation {
  id: string;
  title: string;
  projectId: string;
  agentId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConversationRequest {
  projectId: string;
  agentId: string;
  title?: string;
}