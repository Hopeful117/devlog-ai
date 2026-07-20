CREATE TABLE documentations (

                                id UUID PRIMARY KEY,

                                project_id UUID NOT NULL,

                                title VARCHAR(255) NOT NULL,

                                type VARCHAR(50) NOT NULL,

                                content TEXT NOT NULL,

                                version INTEGER NOT NULL,

                                created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                                updated_at TIMESTAMP WITH TIME ZONE NOT NULL,


                                CONSTRAINT fk_documentation_project
                                    FOREIGN KEY (project_id)
                                        REFERENCES projects(id)

);