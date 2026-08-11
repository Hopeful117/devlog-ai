# Implementation Plan — Story 0033 (Project State v2: Recent Knowledge & Recent Evolution)

## Goal

Enrichir la projection déterministe `ProjectState` de deux sections top-level — `recentKnowledge`
(KnowledgeEvents) et `recentEvolution` (EngineeringEvents validés) — sans LLM, sans nouvelle
persistance, en réutilisant les repos existants. Décisions produit validées (trust level,
sections top-level, limite 5, exposition des commits).

## Approach

### Backend — DTOs
1. `dto/inner/KnowledgeSummary.java` : record `(UUID id, KnowledgeEventType type, String title, Instant createdAt)`.
2. `dto/inner/EvolutionSummary.java` : record `(UUID id, EngineeringEventCategory category, String title, String baseCommit, String targetCommit, Instant occurredAt)`.
3. `dto/response/RecentKnowledgeSection.java` : record `(List<KnowledgeSummary> recentKnowledge)`.
4. `dto/response/RecentEvolutionSection.java` : record `(List<EvolutionSummary> recentEvolution)`.
5. `dto/response/ProjectStateResponse.java` : ajouter `RecentKnowledgeSection recentKnowledge, RecentEvolutionSection recentEvolution`.

### Backend — Mapper
6. `ProjectStateMapper.toResponse(...)` : accepter les 2 sections supplémentaires.
7. Ajouter `toKnowledgeSummary(KnowledgeEvent)` (MapStruct implicite champ-à-champ) et
   `toRecentKnowledgeSection(List<KnowledgeEvent>)`.
8. Ajouter `toEvolutionSummary(EngineeringEvent)` (mapping `category`,`title`,`baseCommit`,
   `targetCommit`,`occurredAt`, `id`) et `toRecentEvolutionSection(List<EngineeringEvent>)`.

### Backend — Service
9. Injecter `KnowledgeEventRepository` et `EngineeringEventRepository`.
10. `buildRecentKnowledge(projectId)` : `findByProjectIdOrderByCreatedAtDesc(projectId)` →
    `subList(0, min(5, size))` → mapper.
11. `buildRecentEvolution(projectId)` : `findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
    projectId, PageRequest.of(0, 5))` → mapper.
12. Passer les 2 sections à `toResponse`.

### Frontend — modèles
13. `project-state.models.ts` : ajouter `KnowledgeSummary`, `EvolutionSummary`,
    `RecentKnowledgeSection`, `RecentEvolutionSection` ; étendre `ProjectState`.

### Frontend — affichage
14. `project-state-page.html` : 2 panneaux réutilisant les styles/empty-state existants.
    - "What have we learned recently?" → `recentKnowledge` (type + title + date).
    - "What recently changed?" → `recentEvolution` (category + title + commits).
15. `project-state-page.spec.ts` : cas rendu + empty-state des 2 sections.

### Tests
16. `ProjectStateProjectionServiceTest` : populated + empty pour les 2 sections.
17. `ProjectStateControllerWebMvcTest` : le endpoint renvoie les 2 nouvelles sections.

## Files Affected

### Backend
- `projectstate/dto/inner/{KnowledgeSummary,EvolutionSummary}.java` (nouveaux)
- `projectstate/dto/response/{RecentKnowledgeSection,RecentEvolutionSection}.java` (nouveaux)
- `projectstate/dto/response/ProjectStateResponse.java`
- `projectstate/mapper/ProjectStateMapper.java`
- `projectstate/service/ProjectStateProjectionServiceImpl.java`
- Tests : `ProjectStateProjectionServiceTest`, `ProjectStateControllerWebMvcTest`

### Frontend
- `features/project-state/project-state.models.ts`
- `features/project-state/project-state-page.html`
- `features/project-state/project-state-page.spec.ts`

### Autre
- Aucun changement de migration, route, service HTTP, ou CI.

## Testing Strategy

- **Backend** : unit (service, populated + empty) + intégration MockMvc (nouvelle sections).
- **Frontend** : spec du composant Overview (rendu + empty-state).
- **Régression** : les 5 sections existantes restent inchangées (backward compatible).

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Interprétation KnowledgeEvent | Résolue (approbation) : wording « recently learned » |
| Record public modifié | Backend + frontend + tests dans le même changement |
| Perf / N+1 | 1 requête/section + bornes (5) ; vérif < 100 ms in integ test |

## Success Criteria

- [ ] `recentKnowledge` : KnowledgeEvents récents (5 max), `id/type/title/createdAt`.
- [ ] `recentEvolution` : EngineeringEvents récents (5 max), `id/category/title/baseCommit/targetCommit/occurredAt`.
- [ ] Empty → listes vides (non null).
- [ ] 5 sections existantes inchangées ; 404 projet inexistant.
- [ ] Backend : tests unit + intégration verts ; suite complète verte.
- [ ] Frontend : modèle étendu ; Overview affiche les 2 sections + empty-states.
- [ ] Aucun LLM, aucune migration, aucun N+1.