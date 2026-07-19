CREATE TABLE ai_agents (

                           id UUID PRIMARY KEY,

                           project_id UUID NOT NULL,

                           name VARCHAR(150) NOT NULL,

                           description VARCHAR(1000),

                           model VARCHAR(100) NOT NULL,

                           system_prompt TEXT,

                           temperature DOUBLE PRECISION,

                           active BOOLEAN NOT NULL,

                           created_at TIMESTAMP NOT NULL,

                           updated_at TIMESTAMP NOT NULL,

                           CONSTRAINT fk_project
                               FOREIGN KEY(project_id)
                                   REFERENCES projects(id)
                                   ON DELETE CASCADE
);