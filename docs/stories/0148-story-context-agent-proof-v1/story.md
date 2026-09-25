# Story 0148 — Mettre le Story Context Agent en état de preuve produit V1

## Objectif

Rendre le Story Context Agent démontrable de bout en bout sur une Engineering
Story réelle : REST/MCP → contexte canonique → `AiTask` → prompt Python →
callback Core → validation déterministe → snapshot immuable → lecture REST.

## Preuves couvertes

- `POST /api/v1/projects/{projectSlug}/stories/{storyId}/analyze-context`
  crée et soumet un `AiTask` `STORY_CONTEXT_ANALYSIS`.
- Le `PromptRequest` transporte `userGuidance`, le digest de contexte, le
  digest de projection et le grounding contract Core-owned. Le prompt Python
  rend ces éléments et la guidance dans ses traces.
- Le callback `POST /api/v1/ai/tasks/{correlationId}/result` est verrouillé par
  corrélation, vérifie les identités Core, le schéma, le grounding/trust,
  les relations et les digests. Aucun `ValidatableProposal` n'est produit.
- Le snapshot `StoryContextAnalysis` est unique par `ai_task_id`, ses colonnes
  de résultat sont `updatable=false`, et un callback terminal dupliqué est
  acknowledgé sans seconde écriture.
- `GET /api/v1/ai/tasks/{aiTaskId}/story-context-analysis` relit le résultat
  validé et sa fraîcheur capturée.

## Contrat de démonstration

Le provider réel n'est pas requis pour la preuve déterministe : les tests
utilisent un provider/mock et un callback structuré avec les identités émises
par Core. Une exécution humaine sur la story 0146 est requise avant de classer
la démonstration comme acceptée produit ; cette validation n'est pas simulée
par cette story.

## Limites

L'immuabilité applicative est renforcée par la contrainte JPA insert-only ; la
base ne reçoit pas de trigger PostgreSQL dans cette tranche. La validation
humaine et le callback d'un provider externe restent des étapes opérateur.
