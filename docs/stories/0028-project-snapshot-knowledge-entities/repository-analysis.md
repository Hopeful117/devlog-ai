# Repository Analysis — Story 0028

## Story

Story 0028 — Project Snapshot: Enrich the Project Context Snapshot with Challenges and Knowledge Relations.

## Current State

### ProjectContextSnapshot (existing)

Currently includes:
- `AnalysisContext.ProjectSnapshot project`
- `ProjectProfileResponse latestProjectProfile`
- `List<AnalysisContext.KnowledgeEventSnapshot> recentKnowledgeEvents`
- `List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals`
- `List<AnalysisContext.ArtifactSnapshot> architectureArtifacts`
- `List<AnalysisContext.DecisionSnapshot> relatedDecisions`
- `List<AnalysisContext.MilestoneSnapshot> recentMilestones`
- `List<AnalysisContext.AnalysisSnapshot> recentAnalyses`
- `List<EngineeringEventSnapshot> validatedEngineeringEvents`

### What's Missing

- ❌ Challenges (Story 0024 entity)
- ❌ Knowledge Relations (Story 0025 entity)

### Repositories Available

**ChallengeRepository**:
- `findByProjectIdOrderByCreatedAtDesc(UUID projectId)` — returns all challenges
- `findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId, Pageable pageable)` — paginated

**KnowledgeRelationRepository**:
- `findByProjectIdOrderByCreatedAtDesc(UUID projectId)` — returns all relations for project

## Recommendation

### Option A: Add both Challenges and Knowledge Relations (recommended)

- Add `ChallengeSnapshot` record to `ProjectContextSnapshot`
- Add `KnowledgeRelationSnapshot` record to `ProjectContextSnapshot`
- Update `ProjectContextProviderImpl` to populate both
- Add `ChallengeRepository.findByProjectIdAndStatus()` for open challenges only

### Option B: Add Challenges only

- Simpler scope
- Knowledge Relations can be added later

## Proposed Implementation

### Step 1 — Add snapshot records to ProjectContextSnapshot

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

### Step 2 — Add fields to ProjectContextSnapshot

```java
List<ChallengeSnapshot> openChallenges,
List<KnowledgeRelationSnapshot> knowledgeRelations
```

### Step 3 — Update ProjectContextProviderImpl

- Inject `ChallengeRepository` and `KnowledgeRelationRepository`
- Add constants: `MAX_OPEN_CHALLENGES = 20`, `MAX_KNOWLEDGE_RELATIONS = 50`
- Add query methods and mapping methods
- Update `build()` to populate new fields

### Step 4 — Add tests

- Test that snapshot includes challenges
- Test that snapshot includes knowledge relations
- Test empty lists when no data

## Files Changed

| File | Change |
|------|--------|
| `ProjectContextSnapshot.java` | +2 snapshot records, +2 fields |
| `ProjectContextProviderImpl.java` | +2 repository injections, +query/mapping methods, updated build() |
| `ProjectContextProviderTest.java` | +2-3 tests |

## Migration

None. Uses existing tables (V34 for challenges, V35 for knowledge_relations).
