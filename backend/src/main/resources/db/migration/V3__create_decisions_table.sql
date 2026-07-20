CREATE TABLE decisions (

                           id UUID PRIMARY KEY,

                           project_id UUID NOT NULL,

                           title VARCHAR(255) NOT NULL,

                           context VARCHAR(5000) NOT NULL,

                           choice VARCHAR(5000) NOT NULL,

                           rationale VARCHAR(5000) NOT NULL,

                           consequences VARCHAR(5000),

                           created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL,


                           CONSTRAINT fk_decision_project
                               FOREIGN KEY (project_id)
                                   REFERENCES projects(id)

);