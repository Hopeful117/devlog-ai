CREATE TABLE artifacts (

                           id UUID PRIMARY KEY,

                           project_id UUID NOT NULL,

                           name VARCHAR(255) NOT NULL,

                           type VARCHAR(50) NOT NULL,

                           path VARCHAR(1000),

                           description VARCHAR(5000),

                           created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL,


                           CONSTRAINT fk_artifact_project
                               FOREIGN KEY (project_id)
                                   REFERENCES projects(id)

);