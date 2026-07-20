# Knowledge model

## Knowledge Sources

DevLog AI builds project knowledge from multiple sources of information.

The architecture is designed to be source-agnostic, allowing new sources of knowledge to be integrated without changing the core knowledge model.

A knowledge source represents any origin of information that can contribute to understanding the evolution, context, and decisions of a software project.

### Supported Knowledge Sources

#### Git Repository

The primary source of knowledge for the initial version of DevLog AI.

Git repositories provide technical evidence through:

- commits,
- code changes,
- file history,
- branches,
- tags,
- dependency modifications,
- project structure evolution.

Git activity represents what changed in a project but does not always contain the complete context behind these changes.

#### Human Input

Developers can manually provide additional context when automated analysis cannot reliably determine the intent behind a change.

Human input allows developers to document:

- architectural reasoning,
- technical constraints,
- project goals,
- decisions,
- challenges encountered.

### Future Knowledge Sources

The architecture should support additional sources of knowledge in future versions, including:

- Pull Requests,
- Issues and project management tickets,
- Existing documentation,
- Architecture Decision Records (ADR),
- External collaboration tools.

### Knowledge Source Principle

DevLog AI does not depend on a specific development workflow.

The system should provide value even when projects have lightweight documentation practices, while becoming more powerful as additional sources of knowledge become available.

## Knowledge Storage Model

DevLog AI preserves both raw project information and structured knowledge generated from this information.

The objective is not only to produce a summary of project evolution, but to maintain a reliable and traceable technical memory.

### Raw Knowledge

Raw Knowledge represents the original information collected from project sources.

Examples include:

- Git commits,
- code changes,
- file modifications,
- dependency changes,
- repository metadata,
- developer inputs.

Raw Knowledge acts as the historical evidence supporting future analysis.

### Structured Knowledge

Structured Knowledge represents the interpretation and understanding built from raw information.

Examples include:

- Engineering Events,
- Engineering Challenges,
- Engineering Decisions,
- project evolution history,
- generated documentation context.

Structured Knowledge provides a human-readable representation of the project's evolution.

### Knowledge Traceability

Every piece of structured knowledge should remain connected to its original sources.

This allows DevLog AI to:

- explain why a knowledge item exists,
- re-analyze previous project history,
- improve future analysis capabilities,
- maintain confidence in generated documentation.

### Storage Principle

Raw information and interpreted knowledge serve different purposes and should coexist.

Raw Knowledge preserves evidence.

Structured Knowledge preserves understanding.

Together, they form the technical memory of a project.