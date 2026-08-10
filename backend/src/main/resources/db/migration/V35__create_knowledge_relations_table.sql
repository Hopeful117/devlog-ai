CREATE TABLE knowledge_relations (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    source_entity_type VARCHAR(30) NOT NULL,
    source_entity_id UUID NOT NULL,
    target_entity_type VARCHAR(30) NOT NULL,
    target_entity_id UUID NOT NULL,
    relation_type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_knowledge_relation_project
        FOREIGN KEY (project_id)
            REFERENCES projects(id) ON DELETE CASCADE,

    CONSTRAINT ck_knowledge_relation_source_entity_type
        CHECK (source_entity_type IN ('CHALLENGE', 'DECISION', 'ENGINEERING_EVENT', 'INSIGHT')),

    CONSTRAINT ck_knowledge_relation_target_entity_type
        CHECK (target_entity_type IN ('CHALLENGE', 'DECISION', 'ENGINEERING_EVENT', 'INSIGHT')),

    CONSTRAINT ck_knowledge_relation_type
        CHECK (relation_type IN ('RESOLVES', 'CAUSED_BY', 'RELATES_TO', 'DERIVED_FROM', 'ADDRESSES', 'INFORMED_BY')),

    CONSTRAINT ck_knowledge_relation_no_self_ref
        CHECK (source_entity_type <> target_entity_type OR source_entity_id <> target_entity_id)
);

CREATE INDEX idx_knowledge_relations_project_id
    ON knowledge_relations(project_id);

CREATE INDEX idx_knowledge_relations_source
    ON knowledge_relations(source_entity_type, source_entity_id);

CREATE INDEX idx_knowledge_relations_target
    ON knowledge_relations(target_entity_type, target_entity_id);
