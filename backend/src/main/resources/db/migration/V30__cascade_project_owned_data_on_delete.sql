-- A project owns its DevLog persistence. External repositories and workspaces are not database
-- children and are deliberately outside this cascade boundary.
ALTER TABLE knowledge_events
    DROP CONSTRAINT fk_knowledge_event_project,
    ADD CONSTRAINT fk_knowledge_event_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE decisions
    DROP CONSTRAINT fk_decision_project,
    ADD CONSTRAINT fk_decision_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE artifacts
    DROP CONSTRAINT fk_artifact_project,
    ADD CONSTRAINT fk_artifact_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE documentations
    DROP CONSTRAINT fk_documentation_project,
    ADD CONSTRAINT fk_documentation_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE validatable_proposals
    DROP CONSTRAINT fk_validatable_proposal_project,
    ADD CONSTRAINT fk_validatable_proposal_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    DROP CONSTRAINT fk_validatable_proposal_analysis,
    ADD CONSTRAINT fk_validatable_proposal_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE;

ALTER TABLE validations
    DROP CONSTRAINT fk_validation_proposal,
    ADD CONSTRAINT fk_validation_proposal
        FOREIGN KEY (proposal_id) REFERENCES validatable_proposals(id) ON DELETE CASCADE;

ALTER TABLE insights
    DROP CONSTRAINT fk_insight_proposal,
    ADD CONSTRAINT fk_insight_proposal
        FOREIGN KEY (proposal_id) REFERENCES validatable_proposals(id) ON DELETE SET NULL,
    DROP CONSTRAINT fk_insight_validation,
    ADD CONSTRAINT fk_insight_validation
        FOREIGN KEY (validation_id) REFERENCES validations(id) ON DELETE SET NULL;

ALTER TABLE sources
    DROP CONSTRAINT fk_source_project,
    ADD CONSTRAINT fk_source_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE project_profile_snapshots
    DROP CONSTRAINT fk_profile_project,
    ADD CONSTRAINT fk_profile_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE generated_deliverable_insights
    DROP CONSTRAINT generated_deliverable_insights_insight_id_fkey,
    ADD CONSTRAINT generated_deliverable_insights_insight_id_fkey
        FOREIGN KEY (insight_id) REFERENCES insights(id) ON DELETE CASCADE;
