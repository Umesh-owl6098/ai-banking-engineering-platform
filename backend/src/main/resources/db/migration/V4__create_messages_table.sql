CREATE TABLE messages (
                          id UUID PRIMARY KEY,

                          conversation_id UUID NOT NULL,

                          role VARCHAR(50) NOT NULL,

                          content TEXT NOT NULL,

                          token_count INTEGER,

                          created_at TIMESTAMP NOT NULL,

                          CONSTRAINT fk_message_conversation
                              FOREIGN KEY (conversation_id)
                                  REFERENCES conversations(id)
                                  ON DELETE CASCADE
);