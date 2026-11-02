UPDATE story_context_completion_policies
SET target_output_tokens = 8192
WHERE policy_version = 'story-context-completion-v1'
  AND provider = 'openai_chatgpt'
  AND model_identifier = 'gpt-5.6-luna'
  AND intent_id = 'engineering-story-context-analysis'
  AND complexity_class = 'REDUCED'
  AND status = 'ACTIVE';
