export interface AiAgent {
  id: string;
  name: string;
  description?: string;
  model: string;
  systemPrompt?: string;
  temperature?: number;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}
