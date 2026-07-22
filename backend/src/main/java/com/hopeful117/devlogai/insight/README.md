# Insight Domain

An Insight is immutable, human-validated project knowledge as defined by ADR-029. It is never
created directly by the AI Engine or through an Insight creation endpoint.

The lifecycle is:

```text
AnalysisContext -> Intent -> InsightProposal -> Human validation -> Insight
```

Accepting an `INSIGHT` proposal atomically creates one Insight. Rejecting it creates none. The
human validator supplies the severity because severity expresses business importance, not model
confidence.

Every newly promoted Insight belongs to exactly one Project and one Analysis and keeps unique,
direct references to its source proposal and validation. The proposal links to the AI task, whose
immutable context snapshot and Intent preserve the rest of the provenance chain.

## Types

| Type | Meaning |
| --- | --- |
| `ARCHITECTURAL` | Architecture or infrastructure knowledge |
| `DOCUMENTATION` | Presentation, installation, usage, requirements, or API knowledge |
| `TECHNOLOGY` | Technology knowledge |
| `EVOLUTION` | Project evolution knowledge |
| `TECHNICAL_DEBT` | Technical debt |
| `SECURITY` | Security knowledge |
| `RISK` | Project risk |
| `RECOMMENDATION` | Recommended action |

## Severity

`INFO`, `WARNING`, and `CRITICAL` express business importance. They are independent from the
proposal confidence score.

Insights expose read-only retrieval by identifier, Project, Analysis, type, and severity. Document
generation and other presentation concerns consume Insights but do not own or modify them.
