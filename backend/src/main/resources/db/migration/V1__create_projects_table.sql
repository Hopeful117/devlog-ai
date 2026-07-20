CREATE TABLE projects (
                          id UUID PRIMARY KEY,
                          name VARCHAR(100) NOT NULL UNIQUE,
                          slug VARCHAR(100) NOT NULL UNIQUE,
                          description TEXT,
                          status VARCHAR(50) NOT NULL,
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);