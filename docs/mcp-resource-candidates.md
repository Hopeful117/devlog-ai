# MCP Resource Candidates — Note préparatoire

> **Status:** Note de préparation (story 0087) — **aucune Resource implémentée**
> **Base:** `docs/mcp-server-audit.md` §13 + identifiants stables confirmés pendant l'implémentation
> **Cadre:** ADR-057 (Resources = information adressable ; format exact d'URI à décider à l'implémentation)

Artefacts possédant déjà un identifiant stable et un service applicatif existant,
confirmés comme bons candidats pour le prochain chantier MCP Resources.

| Artifact | Stable identifier | Existing service / API | Potential MCP resource URI |
|---|---|---|---|
| Liste des projets | project slug | `ProjectService.getAll` | `devlog://projects` |
| Contexte projet (identité + notes) | project slug | `GET /api/v1/projects/{slug}/context` (`ProjectContextController`) — **déjà exposé** : `devlog://projects/{slug}/context` | — (existe) |
| Décision / ADR validée | `decision:{uuid}` (déjà présent dans les identifiers MCP) | `DecisionService.getByProject/getById`, payload proposal lié | `devlog://projects/{slug}/decisions/{id}` |
| Insight validé | `insight:{uuid}` | `InsightService.getById` (+ futur `TemporalAssessmentService` pour l'état stale/current) | `devlog://projects/{slug}/insights/{id}` |
| Engineering Story | storyNumber unique par projet (+ `baseCommit`/`targetCommit`) | `EngineeringStoryService`, REST stories | `devlog://projects/{slug}/stories/{number}` |
| Engineering Event validé | `event:{uuid}` + `git:{sourceId}:{baseCommit}`/`{targetCommit}` en relatedReferences | `EngineeringEventQueryService` | `devlog://projects/{slug}/events/{id}` |
| Commit context (diff classifié, candidats ADR/roadmap, warnings) | commit SHA (`git:{sourceId}:{sha}`, `diff:{sha}:{path}`) | `ProjectHistoryServiceImpl.getCommitContext` → REST `/api/v1/project-history/repositories/{id}/commits/{sha}/context` | `devlog://projects/{slug}/commits/{sha}` |
| Timeline projet | project id | `TimelineProjectionServiceImpl.getTimeline` (REST `/api/v1/projects/{projectId}/timeline`) | `devlog://projects/{slug}/timeline` |
| Fraîcheur repo vs baseline | project id (+ source) | `ProjectFreshnessService.summary` (persisté, déjà consommé par le contexte story) | `devlog://projects/{slug}/freshness` |
| Relations de connaissance | paires `(EntityType, uuid)` | `KnowledgeRelationRepository` (getBySource/getByTarget/…) | `devlog://projects/{slug}/relations` |

Remarques issues de l'implémentation :

* Les champs désormais exposés par `get_engineering_context`
  (`identifier`, `relatedReferences`, `extractionMetadata.resolvedRevision`)
  fournissent directement la matière première pour construire ces URIs côté
  serveur MCP sans nouvelle requête.
* `content.revision` / `symbols.revision` pinning la révision exacte de lecture :
  à réutiliser pour la cohérence entre un contexte digesté et la resource lue
  plus tard.
* Candidats écartés pour Resources : Facts/Observations (périmètre d'analyse
  interne), tables internes (contraire à ADR-057 §3).
