import api from "../api/api";
import type {
  Conversation,
  CreateConversationRequest,
} from "../types/conversation";

export async function getProjectConversations(
  projectId: string,
): Promise<Conversation[]> {
  const response = await api.get<Conversation[]>(
    `/projects/${projectId}/conversations`,
  );

  return response.data;
}

export async function createConversation(
  request: CreateConversationRequest,
): Promise<Conversation> {
  const response = await api.post<Conversation>(
    "/conversations",
    request,
  );

  return response.data;
}

export async function deleteConversation(
  conversationId: string,
): Promise<void> {
  await api.delete(
    `/conversations/${conversationId}`,
  );
}

export async function updateConversationTitle(
  conversationId: string,
  title: string,
): Promise<Conversation> {
  const response = await api.put<Conversation>(
    `/conversations/${conversationId}/title`,
    {
      title,
    },
  );

  return response.data;
}