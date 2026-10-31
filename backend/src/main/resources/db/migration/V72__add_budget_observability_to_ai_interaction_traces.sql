ALTER TABLE ai_interaction_traces
    ADD COLUMN execution_phase VARCHAR(40),
    ADD COLUMN provider_status VARCHAR(40),
    ADD COLUMN incomplete_reason VARCHAR(80),
    ADD COLUMN measurement_unit VARCHAR(20),
    ADD COLUMN measurement_status VARCHAR(20);
