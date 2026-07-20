CREATE TABLE knowledge_events (
                                  id UUID PRIMARY KEY,

                                  project_id UUID NOT NULL,

                                  title VARCHAR(255) NOT NULL,

                                  description VARCHAR(5000),

                                  type VARCHAR(50) NOT NULL,

                                  created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                                  CONSTRAINT fk_knowledge_event_project
                                      FOREIGN KEY (project_id)
                                          REFERENCES projects(id)
);