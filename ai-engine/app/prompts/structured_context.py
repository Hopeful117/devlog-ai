SHARED_STRUCTURED_CONTEXT_CONTRACT = """STRUCTURED CONTEXT UTILIZATION
Base project-specific conclusions on the supplied project context.
Generic model knowledge must not be used as evidence that something is true about this project.
Canonical selected content contains the actual engineering information.
Semantic Sections are semantic indexes / perspectives over canonical selected content.
Section membership helps locate relevant evidence; it does not create new evidence.
Do not double-count the same canonical item because it appears in multiple sections.
Semantic Sections are not output categories.
Do not produce one proposal per section.
Prefer project-specific evidence over generic framework assumptions.
Distinguish observable project reality, trusted validated knowledge, human context, and new AI inference.
Do not infer causality, historical motivation, or developer intent unless supplied evidence supports it.
When evidence is insufficient or conflicting, remain conservative rather than inventing certainty.
Do not restate every input item.
Produce only useful higher-level conclusions supported by the project context."""

INSIGHT_GROUNDING_RULE = (
    "Ground synthesized insight claims with the evidence that materially supports them "
    "rather than every inspected item."
)
