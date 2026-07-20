# Pipeline

## Knowledge Processing Pipeline

DevLog AI processes project evolution through a multi-stage pipeline separating data collection, deterministic analysis, AI interpretation, and human validation.

The objective is to transform raw project activity into reliable and traceable project knowledge.

### Pipeline Stages

### 1. Source Collection

The system collects information from connected knowledge sources.

Examples:

- Git commits,
- code changes,
- repository metadata,
- dependency modifications.

The output of this stage is Raw Activity.

At this stage, DevLog AI knows what happened but does not interpret its meaning.

### 2. Deterministic Analysis

The system analyzes collected activity using objective rules.

Responsibilities:

- identify modified components,
- detect technology changes,
- extract technical metadata,
- analyze structural changes.

This stage provides reliable facts without interpretation.

### 3. Signal Detection

The system evaluates whether observed activity represents a potentially meaningful evolution.

Signals may include:

- architectural changes,
- important dependency changes,
- repeated modifications,
- new capabilities,
- potential technical decisions.

The goal is to avoid unnecessary AI analysis on insignificant changes.

### 4. AI Analysis

The AI Service receives structured context and analyzes potential meaning behind detected signals.

Responsibilities:

- propose Engineering Events,
- identify possible Challenges,
- suggest Engineering Decisions,
- generate contextual explanations.

AI analysis enriches technical facts without replacing them.

### 5. Human Validation

Generated knowledge proposals are reviewed by the developer.

The developer can:

- accept,
- modify,
- reject proposals.

Only validated knowledge becomes part of the official project memory.

### 6. Knowledge Update

Validated information updates the project's knowledge model.

This includes:

- Engineering Events,
- Engineering Decisions,
- Challenges,
- Project Snapshot.

### 7. Documentation Generation

Validated knowledge can be transformed into documentation outputs.

Examples:

- technical articles,
- ADRs,
- architecture documentation.

### Pipeline Principle

Each stage has a clear responsibility.

Raw data provides evidence.

Deterministic analysis provides facts.

AI provides interpretation.

Human validation provides trust.

Together, these layers create a reliable technical memory of a project.
