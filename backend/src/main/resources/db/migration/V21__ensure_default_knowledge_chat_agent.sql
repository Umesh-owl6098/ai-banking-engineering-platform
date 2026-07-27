/*
 * Ensures the default monitoring project has a knowledge chat agent even when
 * the V20 UUID already exists on another project (ON CONFLICT DO NOTHING).
 */
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
SELECT
    'b8f3a2c1-4d5e-6f70-8192-a3b4c5d6e7f8',
    '8c0c0dee-dd8e-4419-bef3-a2e93c10a726',
    'Knowledge Chat Assistant',
    'RAG-powered assistant for banking policy and compliance documents',
    'gpt-4.1-mini',
    'You are a helpful banking and financial assistant. Give clear, accurate and practical answers using simple language.',
    0.3,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM ai_agents
    WHERE project_id = '8c0c0dee-dd8e-4419-bef3-a2e93c10a726'
      AND name = 'Knowledge Chat Assistant'
);
