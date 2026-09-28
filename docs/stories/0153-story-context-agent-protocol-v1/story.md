# Story 0153 — Implémenter le Story Context Agent Protocol v1

## Statut

Prête pour implémentation — story contractuelle et additive, sans implémentation incluse.

## Objectif produit

Fournir au Story Context Agent un protocole versionné, asynchrone et auditable pour demander une projection de contexte, suivre son exécution et lire son résultat. Le Core reste l’unique autorité : il construit une fois le contexte canonique, fige un snapshot immuable et émet la tâche AI. REST et MCP exposent la même capacité.

`snapshotId == aiTaskId` est un invariant normatif de v1. Le protocole distingue `contextDigest`, `projectionDigest` et `selectionDigest`, et expose la fraîcheur, le grounding, les budgets et la provenance.

## Périmètre

- Implémenter le contrat wire v1 côté Java et Python, avec validation stricte (`extra="forbid"` côté Python).
- Ajouter la lecture de projection, la soumission asynchrone, le callback dédié et la lecture du snapshot.
- Ajouter les outils MCP correspondants, sans logique métier propre au transport.
- Garantir `snapshotId == aiTaskId`, l’immutabilité, l’idempotence et la cohérence des digests entre tâche, prompt, snapshot et callback.
- Exposer l’accounting de budget et toute troncature déterministe.
- Conserver temporairement `selectedKnowledge` comme compatibilité versionnée, sans nouvelle sélection pour le SCA.
- Ajouter les métriques et logs opérationnels nécessaires.

## Hors périmètre

- Nouvelle collecte, retrieval, RAG, embeddings, vector store ou deuxième sélection sémantique.
- Modification de la composition canonique, des règles de trust ou du grounding ADR-069.
- Suppression immédiate de `selectedKnowledge` ou du callback générique.
- UI, changement de provider AI ou réécriture de snapshots existants.

## Décisions validées

1. **Snapshot** — `snapshotId` est un alias contractuel de `aiTaskId` en v1. Le snapshot est écrit avant soumission au provider et devient immuable à `SUBMITTED`.
2. **Callback** — façade dédiée Story Context Agent au-dessus d’un noyau commun limité à l’enveloppe, au cycle de vie et à l’idempotence. Le callback ne peut ni inventer un digest, ni modifier le snapshot, ni ajouter une référence.
3. **Budget** — troncature déterministe des éléments optionnels ; rejet si le minimum contractuel ne tient pas. L’accounting contient `candidateCount`, `selectedCount`, `discardedCount`, `usedTokens`, `budget`, `truncated` et `warnings`.
4. **MCP** — cycle asynchrone `submit_task → callback → get_snapshot`; la lecture de projection reste synchrone et read-only.
5. **Legacy** — `selectedKnowledge` reste une enveloppe temporaire `compatibility`. `selectionDigest` ne remplace jamais `contextDigest` ou `projectionDigest`, et aucune nouvelle sélection n’est effectuée par le SCA.

## Contrat v1 minimal

La requête contient `projectSlug`, `storyId` nullable, `intent` et `files`. La projection et le snapshot contiennent au minimum :

```json
{
  "protocolVersion": "story-context-agent-protocol/v1",
  "projectionVersion": "sca/v1",
  "aiTaskId": "<uuid>",
  "snapshotId": "<same uuid as aiTaskId>",
  "contextDigest": "<lowercase sha256>",
  "projectionDigest": "<lowercase sha256>",
  "scope": {"projectSlug": "<slug>", "storyId": null, "intent": "<intent>", "files": []},
  "accounting": {"candidateCount": 0, "selectedCount": 0, "discardedCount": 0, "usedTokens": 0, "budget": 0, "truncated": false, "warnings": []},
  "status": "SUBMITTED"
}
```

Les digests sont calculés sur un JSON canonique UTF-8 : clés triées, nombres normalisés, Unicode NFC, collections déterministes, distinction stricte entre null et omission.

## REST et MCP

Les deux transports utilisent le même `StoryContextAgentProtocolService`.

REST :

- `GET /api/v1/story-context-agent/projects/{projectSlug}/context` — projection read-only.
- `POST /api/v1/story-context-agent/projects/{projectSlug}/tasks` — création de tâche et snapshot, réponse logique `202`.
- `POST /api/v1/story-context-agent/tasks/{aiTaskId}/callback` — callback dédié, authentifié et validé contre le snapshot.
- `GET /api/v1/story-context-agent/tasks/{aiTaskId}/snapshot` — lecture du snapshot et de l’état.

MCP :

- `story_context_agent_get_projection`
- `story_context_agent_submit_task`
- `story_context_agent_callback`
- `story_context_agent_get_snapshot`

Les noms, schémas, erreurs et invariants doivent être identiques entre REST et MCP. Aucun outil ne compose localement un contexte ou ne bloque jusqu’au résultat AI.

## Invariants de sécurité et de grounding

- Le projet et la story sont vérifiés ensemble ; projet étranger, story absente, révision inconnue ou scope incohérent échouent fail-closed.
- Les références sont typées ADR-068 et limitées à `scope=PROJECT_REVISION` en v1.
- `authorizedReferences(projection)` est un sous-ensemble des références du contexte canonique.
- Le modèle et le callback ne peuvent ajouter source, relation, trust tier, connaissance, grounding ou autorisation.
- Le snapshot, le prompt et le callback portent les mêmes versions, scope, fraîcheur, digests, allow-list, budgets et warnings.
- Tout mismatch est rejeté sans mutation. `NOT_ESTABLISHED` interdit toute conclusion groundingée.

## Critères d’acceptation

1. Une soumission valide retourne `202`, `aiTaskId`, `snapshotId` égal, les digests, le scope, la fraîcheur et l’accounting.
2. REST et MCP produisent le même payload et le même `projectionDigest` pour une même requête ; aucune seconde construction ni sélection n’est appelée.
3. Le snapshot est créé avant l’appel provider, devient immuable à `SUBMITTED` et toute mutation ultérieure est refusée.
4. Un retry identique de soumission ou de callback terminal est idempotent ; un payload différent retourne `409` sans mutation.
5. Tout callback dont l’identité, la version, le scope, la fraîcheur, le digest ou le grounding diverge est rejeté en `4xx` sans modifier le snapshot.
6. Les budgets respectent `usedTokens <= budget`, exposent la troncature et produisent des digests déterministes.
7. Python valide le contrat avec `extra="forbid"`; `selectedKnowledge` n’apparaît que sous `compatibility` et reste cohérent avec son digest legacy.
8. REST et MCP sont read-only vis-à-vis du contexte canonique, des connaissances et du grounding.
9. Les scénarios sans grounding retournent `NOT_ESTABLISHED` avec avertissements et digests.

## Découpage en tâches

1. Contrats Java/Pydantic, canonicalisation et vecteurs de digest.
2. Service Core partagé : construction unique, projection, budget et allow-list.
3. Snapshot/tâche : alias d’identité, écriture préalable, immutabilité et idempotence.
4. Façade callback dédiée : authentification, provenance, replay et mapping d’erreurs.
5. Adaptateurs REST et MCP branchés sur le service commun.
6. Migration Python et enveloppe `selectedKnowledge` de compatibilité.
7. Tests unitaires, wire, intégration, sécurité et non-régression des Stories 0148–0152.

## Observabilité et migration

Ajouter au minimum : `sca_protocol_requests_total`, `sca_projection_construction_total`, `sca_budget_truncated_total`, `sca_digest_mismatch_total`, `sca_grounding_rejection_total`, `sca_idempotent_retry_total`, `sca_duplicate_terminal_total` et les latences par opération. Ne jamais journaliser prompts, secrets, tokens ou contenu repository.

La migration est additive. Aucun snapshot existant n’est réécrit. Le retrait de la compatibilité legacy reste TBD et nécessite une story séparée, une confirmation produit, 100 % de consommateurs v1-only pendant 14 jours, zéro erreur de contrat et zéro lecture legacy pendant 7 jours. Le rollback désactive v1 sans réécriture et sans fallback vers une nouvelle sélection.

## Décisions restantes

- propriétaire et mécanisme d’authentification du callback ;
- TTL, conservation et politique d’accès des snapshots ;
- codes HTTP finaux pour conflits d’idempotence et callbacks terminaux ;
- budget numérique par intent ;
- date de fin de la compatibilité `selectedKnowledge` ;
- mécanisme de notification MCP, le polling restant le comportement v1.

## Références

- [ADR-068](../../decisions/ADR-068.md)
- [ADR-069](../../decisions/ADR-069.md)
- [Story 0148](../0148-story-context-agent-proof-v1/story.md)
- [Story 0152](../0152-exposer-story-agent-context-read-only-versionne/story.md)
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextAgentProjectionV1.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/controller/AiTaskResultController.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java`
- `ai-engine/app/schemas/story_context_agent_projection.py`
