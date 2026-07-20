# Intelligence

## Intelligence Architecture

DevLog AI follows a hybrid intelligence architecture where deterministic analysis and artificial intelligence work together.

The system separates objective observations from contextual interpretation.

### Deterministic Analysis

Deterministic analysis is responsible for facts that can be objectively extracted from project sources.

This layer is responsible for:

- collecting repository activity,
- analyzing commits and file changes,
- detecting dependency modifications,
- identifying structural changes,
- extracting technical metadata,
- maintaining data consistency.

Deterministic analysis should remain independent of AI models and provide reliable technical evidence.

### AI Interpretation

The AI layer is responsible for understanding the meaning and context behind technical changes.

This layer is responsible for:

- identifying potential Engineering Events,
- evaluating the impact of changes,
- suggesting challenges and decisions,
- generating explanations,
- connecting related knowledge items.

AI interpretation should not replace factual analysis but enrich it with contextual understanding.

### Collaboration Between Layers

The deterministic and AI layers are complementary.

The deterministic layer answers:

> What happened?

The AI layer helps answer:

> What does it mean?

Together, they produce structured project knowledge.

The final knowledge model combines objective evidence with contextual interpretation while maintaining traceability to original sources.

## Human Validation Workflow

DevLog AI follows a human-in-the-loop approach where generated knowledge must be reviewed before becoming part of the official project memory.

Artificial intelligence is responsible for discovering patterns, interpreting changes, and proposing knowledge items. However, final validation remains a developer responsibility.

### Knowledge Creation Flow

The knowledge creation workflow follows these steps:

1. Project activity is collected from supported knowledge sources.
2. Deterministic analysis extracts objective technical information.
3. AI interpretation generates knowledge proposals.
4. The developer reviews and validates proposed knowledge.
5. Validated knowledge becomes part of the project's technical memory.

### Validation Principle

DevLog AI prioritizes knowledge quality over full automation.

An incorrect or misleading knowledge item can negatively impact future documentation and understanding of a project. Human validation ensures that preserved knowledge reflects the developer's actual intent and context.

### Future Evolution

More autonomous workflows may be introduced in future versions through confidence scoring and configurable validation modes.

Possible future modes could include:

* Strict mode: every knowledge item requires validation.
* Assisted mode: high-confidence items can be automatically accepted.
* Autonomous mode: selected categories of knowledge can be generated without manual approval.

The initial version focuses on reliability and trust through mandatory human validation.
