# Story 0215 - Diagnostics et experience agent/operator MCP

## Statut

IMPLEMENTATION AUTHORIZED - awaiting human review.

The Repository Analysis and Implementation Plan were approved by the human
project owner. Human acceptance of the implementation remains a separate gate.

## Objectif

Rendre la politique d'erreurs MCP exploitable par l'agent et l'operateur sans
modifier les budgets de retry, l'idempotence ou les frontieres d'identite et de
delegation.

## Artefacts gouvernants

- Epic `mcp-error-resilience-policy`.
- ADR-077 - MCP Actor Identity and Human Delegation Boundary.
- ADR-078 - DevLog Internal Agent and External Agent Client Identity.
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy.
- Stories 0211, 0212, 0213 et 0214.

## Perimetre IN

- Definir une projection structurelle des diagnostics MCP avec source,
  categorie, code, couche, retryability et action recommandee.
- Completer le mapping runtime des erreurs JSON-RPC Core, delegation, provider,
  infrastructure et protocole.
- Enrichir `status --json` et `doctor --json` avec des diagnostics operateur
  bornes et non-divulgants.
- Preserver la correlation par `instanceId`, `operationId` et generation quand
  ces identifiants existent deja.
- Ajouter les tests de contrat, redaction, projection et non-regression.
- Documenter le contrat dans `TOOLS.md`.

## Perimetre OUT

- Ajouter un nouveau retry ou modifier un budget existant.
- Modifier l'idempotence, la delegation ou l'identite technique.
- Ajouter une migration, une table ou une nouvelle file de telemetry.
- Implementer la politique `429` au-dela de sa classification existante.
- Creer les runbooks operateur detailles de l'epic suivante.
- Executer des validations avec des credentials reelles.

## Invariants

1. Une erreur Core `401` ne devient jamais une expiration transport.
2. Les diagnostics sont machine-readable, bornes et allowlistes.
3. Aucun secret, bearer, delegation token, payload, prompt ou provider response
   ne figure dans les diagnostics.
4. Les messages humains ne servent jamais de discriminant interne.
5. Les retries existants restent strictement inchanges.
6. Les contrats existants restent compatibles lorsque les champs additionnels
   sont absents.

## Criteres d'acceptation

### AC-1 - Contrat structurel

Les erreurs exposees par MCP et le runtime utilisent, lorsque disponible,
`source`, `category`, `code`, `layer`, `retryable` et `action`.

### AC-2 - Classification complete

Les categories Core authentication, Core authorization, delegation, provider,
protocol, functional, transient infrastructure, rate limited et unknown sont
mappees deterministement.

### AC-3 - Separation des couches

Les erreurs Core, delegation et provider ne declenchent jamais le renouvellement
du bearer transport.

### AC-4 - Diagnostics operateur

`status --json` et `doctor --json` distinguent configuration, token endpoint,
backend, ownership runtime, renouvellement et Core, avec une action bornée.

### AC-5 - Secret safety

Les tests prouvent l'absence de secrets, payloads, messages backend bruts et
tokens dans les journaux et projections.

### AC-6 - Non-regression

Les budgets de retry, les transitions terminales et les contrats d'observabilite
existants restent inchanges.

### AC-7 - Validation

Les suites Python et MCP Java pertinentes, `git diff --check` et les tests
frontend affectes passent.

## Workflow

1. Repository Analysis - approuve.
2. Implementation Plan - approuve.
3. Implementation - en cours.
4. Documentation reconciliation.
5. Revue independante.
6. Acceptance humaine.
