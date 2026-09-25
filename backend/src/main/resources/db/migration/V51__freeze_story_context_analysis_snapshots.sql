-- Story Context Analysis is an immutable product snapshot. The row is
-- historical evidence and is insert-only after callback validation.
COMMENT ON COLUMN story_context_analyses.analysis_snapshot IS
    'Immutable validated StoryContextAnalysisResult snapshot; insert-only';
COMMENT ON COLUMN story_context_analyses.context_digest IS
    'Immutable Core-issued context digest captured at task submission';
COMMENT ON COLUMN story_context_analyses.prompt_execution_metadata IS
    'Immutable provider execution trace captured at callback';
