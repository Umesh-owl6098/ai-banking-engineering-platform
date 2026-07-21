export interface SourceReference {
  fileName: string;
  chunkIndex: number;
  similarity: number;
  preview: string;
}

export interface ChatMessage {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  sources?: SourceReference[];
}

export interface ChatRequest {
  conversationId: string;
  message: string;
}