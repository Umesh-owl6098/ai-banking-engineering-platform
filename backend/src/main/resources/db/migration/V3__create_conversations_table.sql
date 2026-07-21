CREATE TABLE conversations (
                               id UUID PRIMARY KEY,

                               project_id UUID NOT NULL,
                               agent_id UUID NOT NULL,

                               title VARCHAR(255) NOT NULL,

                               status VARCHAR(50) NOT NULL,

                               created_at TIMESTAMP NOT NULL,
                               updated_at TIMESTAMP NOT NULL,

                               CONSTRAINT fk_conversation_project
                                   FOREIGN KEY (project_id)
                                       REFERENCES projects(id),

                               CONSTRAINT fk_conversation_agent
                                   FOREIGN KEY (agent_id)
                                       REFERENCES ai_agents(id)
);