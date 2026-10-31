CREATE TABLE story_context_completion_policies (
    id UUID PRIMARY KEY,
    policy_version VARCHAR(64) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model_identifier VARCHAR(255) NOT NULL,
    intent_id VARCHAR(160) NOT NULL,
    complexity_class VARCHAR(32) NOT NULL,
    minimum_output_tokens INTEGER NOT NULL,
    target_output_tokens INTEGER NOT NULL,
    maximum_output_tokens INTEGER NOT NULL,
    context_capacity_tokens INTEGER NOT NULL,
    measurement_certainty VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_story_context_completion_policy UNIQUE
        (policy_version, provider, model_identifier, intent_id, complexity_class),
    CONSTRAINT ck_story_context_completion_policy_bounds CHECK (
        minimum_output_tokens > 0
        AND minimum_output_tokens <= target_output_tokens
        AND target_output_tokens <= maximum_output_tokens
        AND maximum_output_tokens < context_capacity_tokens
    )
);

INSERT INTO story_context_completion_policies (
    id, policy_version, provider, model_identifier, intent_id, complexity_class,
    minimum_output_tokens, target_output_tokens, maximum_output_tokens,
    context_capacity_tokens, measurement_certainty, status, created_at
) VALUES
    ('00000000-0000-0000-0000-000000000731', 'story-context-completion-v1',
     'openai_chatgpt', 'gpt-5.6-luna', 'engineering-story-context-analysis',
     'REDUCED', 1024, 2000, 4096, 1048576, 'REPLAY_VALIDATED', 'ACTIVE', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000732', 'story-context-completion-v1',
     'openai_chatgpt', 'gpt-5.6-luna', 'engineering-story-context-analysis',
     'STANDARD', 2048, 3000, 8192, 1048576, 'REPLAY_VALIDATED', 'ACTIVE', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000733', 'story-context-completion-v1',
     'openai_chatgpt', 'gpt-5.6-luna', 'engineering-story-context-analysis',
     'COMPLEX', 4096, 4096, 16384, 1048576, 'REPLAY_VALIDATED', 'ACTIVE', CURRENT_TIMESTAMP);
