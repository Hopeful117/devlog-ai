# Engineering Report

## Story

Story 0033 : enrichir la projection déterministe `ProjectState` (overview projet) de deux sections
top-level — `recentKnowledge` (KnowledgeEvents) et `recentEvolution` (EngineeringEvents validés) —
construites exclusivement à partir de données déjà persistées, sans LLM ni nouvelle persistance.

## Objective

Répondre sur l'overview aux questions « Qu'avons-nous appris récemment ? » et « Que vient-il de
changer ? » en exposant deux sources de connaissance de confiance jusqu'alors invisibles dans la
projection, dans le respect du principe read-model (les domaines restent la source de vérité).

## Repository Analysis Summary

L'analyse a confirmé que les deux entités (`KnowledgeEvent`, `EngineeringEvent`) et leurs requêtes
list-by-project existaient déjà (`findByProjectIdOrderByCreatedAtDesc`,
`findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc`) — **aucune nouvelle requête**,
persistance ni migration nécessaire. Le record `ProjectStateResponse` a été étendu en ajoutant deux
champs, rétro-compatible.

## Implementation Summary

Réalisé en 4 volets :
1. **DTOs** : `KnowledgeSummary`, `EvolutionSummary` (inner), `RecentKnowledgeSection`,
   `RecentEvolutionSection` (response) ; `ProjectStateResponse` étendu.
2. **Mapper** : `toResponse` (+2 paramètres, `@Mapping` explicites pour lever une collision de noms
   MapStruct) ; 6 méthodes de mapping (entity → summary, list → empty-list, section).
3. **Service** : injection des 2 repos ; `buildRecentKnowledge` (≤5) et `buildRecentEvolution`
   (`PageRequest.of(0, 5)`) ; passage des sections à `toResponse`.
4. **Frontend** : modèles étendus + 2 panneaux d'overview + empty-states ; specs mises à jour.

## Modified Files

| Fichier | Changement |
|---|---|
| `backend/.../projectstate/dto/response/ProjectStateResponse.java` | + `recentKnowledge`, `recentEvolution` |
| `backend/.../projectstate/mapper/ProjectStateMapper.java` | `toResponse` étendu + 6 méthodes de mapping |
| `backend/.../projectstate/service/ProjectStateProjectionServiceImpl.java` | 2 repos + 2 méthodes de build des sections |
| `backend/.../projectstate/...ServiceTest.java`, `ControllerWebMvcTest.java`, `MapperTest.java` | populated/empty, JSON, mapping |
| `frontend/.../project-state.models.ts` | 2 types + 2 interfaces de section + extension `ProjectState` |
| `frontend/.../project-state-page.html` | 2 panneaux + empty-states |
| `frontend/.../project-state-page.spec.ts` | +3 tests (rendu knowledge/evolution + empty-states) |
| `frontend/.../project-state.service.spec.ts` | fixture enrichie |

## Created Files

| Fichier | Type |
|---|---|
| `backend/.../dto/inner/KnowledgeSummary.java` | record DTO |
| `backend/.../dto/inner/EvolutionSummary.java` | record DTO |
| `backend/.../dto/response/RecentKnowledgeSection.java` | record DTO |
| `backend/.../dto/response/RecentEvolutionSection.java` | record DTO |

## Architecture Impact

- Backend : uniquement le package `projectstate` (DTOs + mapper + service). Aucune persistance,
  aucune route, aucun changement de schéma ni de config.
- Frontend : uniquement le modèle et la page overview + specs. Aucune route ni service ajouté.
- CI : aucun changement nécessaire.
- Déviation : noms de paramètres `toResponse` distincts du nom de section (`recentKnowledgeSection`
  / `recentEvolutionSection`) + `@Mapping` explicites pour lever la collision MapStruct (sans impact
  sur le contrat JSON). Mapper testé au-delà du plan.

## Validation

```
cd backend && ./mvnw test                           → exit 0 (suite complète)
cd frontend && npm run lint                         → 0 problems
cd frontend && npm run format:check                 → All matched files use Prettier code style
cd frontend && npm run build                        → OK (strict)
cd frontend && npm test -- --watch=false            → 37 files / 162 tests passed
cd frontend && ng test --coverage --watch=false     → Lines ≈ 80.7 % (>= 75 %)
cd frontend && npm run e2e                          → 2 passed
```

## Review Outcome

Code Review sans finding bloquant. Les 15 critères d'acceptation sont vérifiés. Les deux remarques
(collision MapStruct résolue, `KnowledgeEvent` sans statut rendu « recently learned ») sont
traitées/assumées conformément aux décisions approuvées.

**Recommandation : Approved**

## Remaining Work

- Dashboard global (agrégation multi-projets) et heuristiques de blocage (`BLOCKED`/`CANCELLED`,
  « in progress = blocked ») — explicitement hors scope.
- Redéployer la stack Docker pour que l'e2e exerce les nouvelles sections (nettoyage trivial).

## Lessons Learned

1. **Record avec composant homonyme de la section → collision MapStruct.** Quand un composant de
   record porte le même nom que la propriété cible, MapStruct ne résout pas le mapping direct ;
   nommer le paramètre source distinctement et déclarer un `@Mapping(target, source)` explicite.
2. **Ajouter, jamais retirer, à un record publique.** Les champs ajoutés sont rétro-compatibles ;
   la mise à jour lockstep (DTO + mapper + service + frontend + fixtures) évite tout désalignement.
3. **Réutiliser les requêtes existantes = zéro coût de persistance.** Les deux requêtes
   list-by-project préexistaient ; l'ajout au read model n'a ni migration ni nouveau volet, ni LLM.

## Final Status

**Completed**

---

Engineering Story 0033 workflow complete.