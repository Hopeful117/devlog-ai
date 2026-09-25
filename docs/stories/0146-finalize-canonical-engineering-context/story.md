# Story 0146 — Finaliser la migration canonique du Story Context Agent

## Statut

Implémentée et validée; ADR-069 accepté et applicable.

## Objectif

Clôturer le reliquat de migration du Story Context Agent vers la frontière
`CanonicalEngineeringContext` introduite par Story 0145, en supprimant les
dépendances de sélection legacy qui ne sont plus utilisées par le use case.

## Périmètre

- conserver une construction Core puis une projection `StoryContextAgentProjection`;
- supprimer les dépendances mortes `KnowledgeSelectionService`,
  `SelectedKnowledgePromptProjectionService` et `AnalysisContextService` du use case;
- préserver les trois digests, le snapshot et les validations fail-closed existants;
- verrouiller par les tests existants que le use case ne reconstruit ni ne sélectionne
  un second contexte.

## Hors périmètre

Pas de nouvelle source d’autorité, de changement de budget métier, de suppression
d’endpoint legacy, de migration externe, de RAG, embeddings, vector store,
mémoire générique, orchestration OpenClaw ou UI.

## Critères d’acceptation

1. ✅ Le use case ne dépend plus d’un service de sélection ou de projection legacy.
2. ✅ La projection SCA et son snapshot restent dérivés du contexte Core canonique.
3. ✅ Les tests ciblés et la compilation backend passent; aucun contrat de digest n’est
   affaibli.

## ADR applicables

ADR-063, ADR-067, ADR-068 et ADR-069; ADR-006 reste applicable aux sorties IA.
