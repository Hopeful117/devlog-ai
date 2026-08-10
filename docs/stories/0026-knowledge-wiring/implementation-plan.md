# Implementation Plan — Story 0026

## Story

Story 0026 — Knowledge Wiring: Add entity-centric convenience methods to `KnowledgeRelationService`.

## Approved Repository Analysis

Option A retenue : Méthodes de convenance au niveau service uniquement, déléguant à `getBySource()` existant.

## Implementation Steps

### Step 1 — Interface: Add 4 convenience method signatures

**File**: `KnowledgeRelationService.java`

Ajouter après `delete(UUID id)` :

```java
List<KnowledgeRelationResponse> getByChallenge(UUID challengeId);
List<KnowledgeRelationResponse> getByDecision(UUID decisionId);
List<KnowledgeRelationResponse> getByEngineeringEvent(UUID engineeringEventId);
List<KnowledgeRelationResponse> getByInsight(UUID insightId);
```

**Rationale**: Ces méthodes exposent une API domaine-specific sans exiger la connaissance de `EntityType`.

---

### Step 2 — Implementation: Add 4 delegating methods

**File**: `KnowledgeRelationServiceImpl.java`

Ajouter avant l'accolade fermante de la classe :

```java
@Override
public List<KnowledgeRelationResponse> getByChallenge(UUID challengeId) {
    return getBySource(EntityType.CHALLENGE, challengeId);
}

@Override
public List<KnowledgeRelationResponse> getByDecision(UUID decisionId) {
    return getBySource(EntityType.DECISION, decisionId);
}

@Override
public List<KnowledgeRelationResponse> getByEngineeringEvent(UUID engineeringEventId) {
    return getBySource(EntityType.ENGINEERING_EVENT, engineeringEventId);
}

@Override
public List<KnowledgeRelationResponse> getByInsight(UUID insightId) {
    return getBySource(EntityType.INSIGHT, insightId);
}
```

**Rationale**: Chaque méthode délègue à `getBySource()` existant. Pas de logique nouvelle, pas de duplication.

---

### Step 3 — Tests: Add 4 unit tests

**File**: `KnowledgeRelationServiceTest.java`

4 tests à ajouter :

1. `shouldReturnRelationsByChallenge` — vérifie que `getByChallenge()` délègue correctement
2. `shouldReturnRelationsByDecision` — vérifie que `getByDecision()` délègue correctement
3. `shouldReturnRelationsByEngineeringEvent` — vérifie que `getByEngineeringEvent()` délègue correctement
4. `shouldReturnRelationsByInsight` — vérifie que `getByInsight()` délègue correctement

Chaque test suit le pattern existant :
- Mock `knowledgeRelationRepository.findBySourceEntityTypeAndSourceEntityId()`
- Appeler la méthode de convenance
- Vérifier la déléguation et la réponse

---

### Step 4 — Validation

- `./mvnw compile` — vérifier la compilation
- `./mvnw test -Dtest="KnowledgeRelationServiceTest"` — tests unitaires
- `./mvnw test` — suite complète (509+ tests attendus)
- SonarQube (si token disponible)

---

### Step 5 — Documentation Reconciliation

Vérifier si la documentation existante doit être mise à jour :
- `docs/knowledge-model.md` ou équivalent
- API documentation (OpenAPI/Swagger)
- README du module knowledge

Si aucune mise à jour nécessaire, documenter cette conclusion dans l'Implementation Report.

---

## Files Changed

| File | Change |
|------|--------|
| `KnowledgeRelationService.java` | +4 method signatures |
| `KnowledgeRelationServiceImpl.java` | +4 method implementations |
| `KnowledgeRelationServiceTest.java` | +4 tests |

## No Migration Required

Le code déléguant existe déjà dans `getBySource()`. Aucune modification de schéma nécessaire.

## Expected Outcome

- 509+ tests passing
- SonarQube Quality Gate PASSED
- 0 nouvelles violations
- API service enrichie sans changement de controller
