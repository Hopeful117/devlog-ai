# Implementation Plan — Story 0028

## Story

Story 0028 — Project Snapshot: Enrich the Project Context Snapshot with Challenges and Knowledge Relations.

## Approved Repository Analysis

Add `ChallengeSnapshot` and `KnowledgeRelationSnapshot` to `ProjectContextSnapshot` and populate them in `ProjectContextProviderImpl`.

## Implementation Steps

### Step 1 — Add snapshot records to ProjectContextSnapshot

**File**: `ProjectContextSnapshot.java`

Ajouter deux records internes :

```java
public record ChallengeSnapshot(
    UUID id, String title, String description, String impact,
    String status, String resolution, Instant createdAt
) { }

public record KnowledgeRelationSnapshot(
    UUID id, EntityType sourceEntityType, UUID sourceEntityId,
    EntityType targetEntityType, UUID targetEntityId,
    KnowledgeRelationType relationType, String description,
    Instant createdAt
) { }
```

---

### Step 2 — Add fields to ProjectContextSnapshot

**File**: `ProjectContextSnapshot.java`

Ajouter au record principal :

```java
List<ChallengeSnapshot> openChallenges,
List<KnowledgeRelationSnapshot> knowledgeRelations
```

Adapter le constructeur compact pour copier les listes.

---

### Step 3 — Update ProjectContextProviderImpl

**File**: `ProjectContextProviderImpl.java`

1. Injecter `ChallengeRepository` et `KnowledgeRelationRepository`
2. Ajouter constantes : `MAX_OPEN_CHALLENGES = 20`, `MAX_KNOWLEDGE_RELATIONS = 50`
3. Ajouter méthodes de requête et mapping
4. Mettre à jour `build()` pour peupler les nouveaux champs

---

### Step 4 — Add tests

**File**: `ProjectContextProviderTest.java`

2-3 tests :
1. `shouldIncludeOpenChallengesInSnapshot`
2. `shouldIncludeKnowledgeRelationsInSnapshot`
3. `shouldReturnEmptyListsWhenNoData`

---

### Step 5 — Validation

- `./mvnw compile`
- `./mvnw test -Dtest="ProjectContextProviderTest"`
- `./mvnw test` (full suite)

---

## Files Changed

| File | Change |
|------|--------|
| `ProjectContextSnapshot.java` | +2 records, +2 fields, updated constructor |
| `ProjectContextProviderImpl.java` | +2 injections, +query/mapping methods, updated build() |
| `ProjectContextProviderTest.java` | +2-3 tests |

## No Migration Required

Uses existing tables (V34 for challenges, V35 for knowledge_relations).

## Expected Outcome

- 515+ tests passing
- Project Context Snapshot includes Challenges and Knowledge Relations
