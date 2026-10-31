UPDATE story_context_completion_policies
SET minimum_output_tokens = CASE complexity_class
        WHEN 'REDUCED' THEN 2048
        WHEN 'STANDARD' THEN 4096
        WHEN 'COMPLEX' THEN 8192
    END,
    target_output_tokens = CASE complexity_class
        WHEN 'REDUCED' THEN 4000
        WHEN 'STANDARD' THEN 6000
        WHEN 'COMPLEX' THEN 8192
    END,
    maximum_output_tokens = CASE complexity_class
        WHEN 'REDUCED' THEN 8192
        WHEN 'STANDARD' THEN 16384
        WHEN 'COMPLEX' THEN 32768
    END
WHERE policy_version = 'story-context-completion-v1'
  AND provider = 'openai_chatgpt'
  AND model_identifier = 'gpt-5.6-luna'
  AND intent_id = 'engineering-story-context-analysis'
  AND status = 'ACTIVE';
