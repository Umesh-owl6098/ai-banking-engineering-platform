INSERT INTO ai_agents (
    id,
    project_id,
    name,
    description,
    model,
    system_prompt,
    temperature,
    active,
    created_at,
    updated_at
)
VALUES (
    'c964b4de-f07a-4b61-bb53-18144b06f1fa',
    '8c0c0dee-dd8e-4419-bef3-a2e93c10a726',
    'Knowledge Chat Assistant',
    'RAG-powered assistant for banking policy and compliance documents',
    'gpt-4.1-mini',
    'You are a helpful banking and financial assistant. Give clear, accurate and practical answers using simple language.',
    0.3,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
