# Repository Analysis — Story 0029 Engineering Story Identity and Git Evolution Tracking

## Story Summary

Introduce an `EngineeringStory` entity that establishes the first missing edge in the feedback loop between the engineering workflow and the knowledge model. This entity enables deterministic tracking of Engineering Stories and their Git evolution, allowing DevLog to answer "which Git evolution implemented Story X?" and provide Story-aware context to agents.

## Current State

### Existing Knowledge Entities

| Entity | Package | Table | Status |
|--------|---------|-------|--------|
| `Decision` | `decision/` | `decisions` | ✅ Complete — CRUD API, tests |
| `EngineeringEvent` | `engineeringevent/` | `engineering_events` | ✅ Complete — validated, immutable |
| `Challenge` | `challenge/` | `challenges` | ✅ Complete — CRUD API, tests |
| `KnowledgeRelation` | `knowledge/relation/` | `knowledge_relations` | ✅ Complete — V35 migration |
| `Insight` | `insight/` | `insights` | ✅ Complete — validated proposals |
| `KnowledgeEvent` | `knowledge/entity/` | `knowledge_events` | ⚠️ Legacy raw occurrence layer |

### Entity Count

- **46 entities** total under `com.hopeful117.devlogai`
- Challenge is the most recent entity (V34/V35)

### Architecture Notes

- All entities belong to their own package under `com.hopeful117.devlogai`
- `Project` relationship is always `@ManyToOne(fetch = LAZY, optional = false)` with `@JoinColumn`
- `@CreatedDate` / `@LastModifiedDate` from Spring Data JPA auditing
- UUID primary keys with `@GeneratedValue(strategy = UUID)`
- DTOs are Java records (response) or Lombok `@Data` classes (request)
- Controllers use `@RequiredArgsConstructor` for constructor injection
- Tests extend `ControllerWebMvcTestSupport` for WebMvc tests
- Status enums use `@Enumerated(EnumType.STRING)` with CHECK constraints in PostgreSQL

### Database

- **Current latest migration: V35** (`V35__create_knowledge_relations_table.sql`)
- **Next migration: V36** (this story)
- PostgreSQL 17 with Flyway
- Tables use `UUID PRIMARY KEY`, `TIMESTAMP WITH TIME ZONE`, check constraints for enums

## Entity Pattern Reference

The `Challenge` entity is the closest reference for a new knowledge entity:

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "challenges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Challenge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String title;
    // ... other fields with AuditingEntityListener timestamps
}
```

**Key patterns:**
- Lombok: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- JPA: `@Entity`, `@Table`, `@ManyToOne(fetch = LAZY, optional = false)`
- Timing: `@CreatedDate`, `@LastModifiedDate` via `@EntityListeners(AuditingEntityListener.class)`
- UUID primary keys with `GenerationType.UUID`
- Project FK always `NOT NULL` with `ON DELETE CASCADE`

## Service Pattern Reference

**Interface:**
```java
public interface ChallengeService {
    ChallengeResponse create(CreateChallengeRequest request);
    ChallengeResponse getById(UUID id);
    List<ChallengeResponse> getByProject(UUID projectId);
    ChallengeResponse update(UUID id, UpdateChallengeRequest request);
}
```

**Implementation:**
```java
@RequiredArgsConstructor
@Service
public class ChallengeServiceImpl implements ChallengeService {
    private final ChallengeRepository challengeRepository;
    private final ProjectRepository projectRepository;
    private final ChallengeMapper challengeMapper;

    @Override
    public ChallengeResponse create(CreateChallengeRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("Project", request.getProjectId()));
        // ... business logic
    }
}
```

**Key patterns:**
- `@RequiredArgsConstructor` for constructor injection
- Project existence validation via `EntityNotFoundException`
- Mapper-based entity ↔ DTO conversion
- Spring Data JPA repository for persistence

## Controller Pattern Reference

```java
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @PostMapping
    public ResponseEntity<ChallengeResponse> create(@Valid @RequestBody CreateChallengeRequest request) {
        ChallengeResponse response = challengeService.create(request);
        URI location = URI.create("/api/v1/challenges/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChallengeResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(challengeService.getById(id));
    }
    // ... other endpoints
}
```

**Key patterns:**
- `@RestController` with `@RequestMapping` versioned path
- `@PostMapping`, `@GetMapping`, `@PutMapping` for CRUD
- `@Valid` for request validation
- `@PathVariable` for URL parameters
- POST returns `201 Created` with Location header
- `URI.create()` for location construction

## DTO Pattern Reference

**Request DTO (Lombok @Data):**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChallengeRequest {
    @NotNull
    private UUID projectId;

    @NotBlank
    private String title;

    private String description;
    private String impact;
    private ChallengeStatus status;
    private String resolution;
}
```

**Response DTO (Java record):**
```java
public record ChallengeResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        String impact,
        ChallengeStatus status,
        String resolution,
        Instant createdAt,
        Instant updatedAt
) {}
```

**Key patterns:**
- Request: Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Response: Java record (immutable, concise)
- Validation: Jakarta annotations `@NotNull`, `@NotBlank`, `@Size`, etc.

## Mapper Pattern Reference

```java
@Mapper(componentModel = "spring")
public interface ChallengeMapper {

    @Mapping(target = "projectId", source = "project.id")
    ChallengeResponse toResponse(Challenge challenge);

    Challenge toEntity(CreateChallengeRequest request);
}
```

**Key patterns:**
- MapStruct `@Mapper(componentModel = "spring")` for auto-registration
- `@Mapping` for field transformations (nested to flat)
- Component model = "spring" for automatic injection

## Migration Pattern Reference

```sql
CREATE TABLE challenges (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_challenge_project
        FOREIGN KEY (project_id)
            REFERENCES projects(id) ON DELETE CASCADE,

    CONSTRAINT ck_challenge_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'ACCEPTED', 'MITIGATED'))
);

CREATE INDEX idx_challenges_project_id
    ON challenges(project_id);
```

**Key patterns:**
- V-numbering with descriptive name: `V36__create_engineering_stories_table.sql`
- `UUID PRIMARY KEY` with PostgreSQL
- `TIMESTAMP WITH TIME ZONE` for temporal fields
- FK constraints with `ON DELETE CASCADE`
- CHECK constraints for enum validation
- Indexes on FK columns

## Test Pattern Reference

### Service Unit Test
```java
@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    ChallengeRepository challengeRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    ChallengeMapper challengeMapper;

    @InjectMocks
    ChallengeServiceImpl challengeService;

    @Test
    void shouldCreateChallengeSuccessfully() {
        // Arrange
        UUID projectId = UUID.randomUUID();
        CreateChallengeRequest request = new CreateChallengeRequest();
        request.setProjectId(projectId);
        // ... setup mocks

        // Act
        ChallengeResponse result = challengeService.create(request);

        // Assert
        assertNotNull(result);
        assertEquals(response, result);
        verify(projectRepository).findById(projectId);
    }
}
```

### Controller WebMvc Test
```java
class ChallengeControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllChallengeRoutes() throws Exception {
        ChallengeService service = mock(ChallengeService.class);
        MockMvc mvc = mockMvc(new ChallengeController(service));

        mvc.perform(post("/api/v1/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"DB migration failure"}
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/challenges/" + id));
    }
}
```

**Key patterns:**
- `@ExtendWith(MockitoExtension.class)` for unit tests
- `@Mock` for dependencies, `@InjectMocks` for SUT
- `ControllerWebMvcTestSupport` base class for endpoint tests
- MockMvc for HTTP testing with JSON content
- Status assertions (`isCreated()`, `isOk()`)
- Header assertions (`header().string()`)

## Snapshot Enrichment Target

### ProjectContextSnapshot (Current)

```java
public record ProjectContextSnapshot(
        AnalysisContext.ProjectSnapshot project,
        ProjectProfileResponse latestProjectProfile,
        List<AnalysisContext.KnowledgeEventSnapshot> recentKnowledgeEvents,
        List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals,
        List<AnalysisContext.ArtifactSnapshot> architectureArtifacts,
        List<AnalysisContext.DecisionSnapshot> relatedDecisions,
        List<AnalysisContext.MilestoneSnapshot> recentMilestones,
        List<AnalysisContext.AnalysisSnapshot> recentAnalyses,
        List<EngineeringEventSnapshot> validatedEngineeringEvents,
        List<ChallengeSnapshot> openChallenges,
        List<KnowledgeRelationSnapshot> knowledgeRelations
) {
    // Snapshot inner records...
    public record EngineeringEventSnapshot(UUID id, String category, String title,
            String summary, UUID sourceId, String baseCommit, String targetCommit,
            Instant occurredAt, UUID proposalId) { }

    public record ChallengeSnapshot(UUID id, String title, String description,
            String impact, String status, String resolution, Instant createdAt) { }

    // ... KnowledgeRelationSnapshot
}
```

### AnalysisContext (Current)

```java
public record AnalysisContext(
        ProjectSnapshot project,
        AnalysisSnapshot analysis,
        ProjectProfileResponse projectProfile,
        List<FactSnapshot> facts,
        List<ObservationSnapshot> observations,
        List<KnowledgeEventSnapshot> recentKnowledgeEvents,
        List<AnalysisSnapshot> relatedAnalyses,
        List<ArtifactSnapshot> architectureArtifacts,
        List<DecisionSnapshot> relatedDecisions,
        List<MilestoneSnapshot> recentMilestones,
        List<ValidatedProposalSnapshot> validatedProposals,
        EvolutionContext evolutionContext,
        List<EngineeringEventSnapshot> validatedEngineeringEvents,
        List<ChallengeSnapshot> openChallenges,
        List<KnowledgeRelationSnapshot> knowledgeRelations
) {
    public record EvolutionContext(
            String contextVersion, UUID projectId, UUID sourceId,
            String baseCommit, String targetCommit, String comparisonPolicy, boolean mergeCommit,
            Instant targetCommittedAt, CommitDiffAnalysisContext commitDiff) { }
    // ... other snapshot records
}
```

### Enrichment Pattern

The `ProjectContextProviderImpl.build()` method aggregates various entity snapshots:
- Recent knowledge events (limited to 20)
- Validated proposals (limited to 20)
- Architecture artifacts (limited to 20)
- Related decisions (limited to 20)
- Recent milestones (limited to 10)
- Related analyses (filtered for type)
- Engineering events with commit info (limited to 10)
- Challenges (limited to 20)
- Knowledge relations (limited to 50)

## Files to Create

| File | Purpose |
|------|---------|
| `entity/EngineeringStory.java` | JPA entity with id, project, storyNumber, title, status, storyPath, baseCommit, targetCommit, createdAt, completedAt |
| `entity/StoryStatus.java` | Enum: REGISTERED, IN_PROGRESS, COMPLETED |
| `repository/EngineeringStoryRepository.java` | Spring Data JPA repository with `findByProjectId`, `findByProjectIdAndStoryNumber` |
| `service/EngineeringStoryService.java` | Interface with `register`, `startImplementation`, `complete`, `getById`, `getByProject` |
| `service/EngineeringStoryServiceImpl.java` | Implementation with validation and business logic |
| `controller/EngineeringStoryController.java` | REST API at `/api/v1/projects/{projectId}/stories` |
| `dto/request/CreateEngineeringStoryRequest.java` | Request with storyNumber, title, storyPath |
| `dto/request/UpdateEngineeringStoryRequest.java` | Request for status transitions |
| `dto/request/StartStoryRequest.java` | Request with baseCommit |
| `dto/request/CompleteStoryRequest.java` | Request with targetCommit |
| `dto/response/EngineeringStoryResponse.java` | Response record with all fields |
| `mapper/EngineeringStoryMapper.java` | MapStruct mapper for entity ↔ DTO |
| `V36__create_engineering_stories_table.sql` | Flyway migration |
| `EngineeringStoryServiceTest.java` | Unit tests with Mockito |
| `EngineeringStoryControllerWebMvcTest.java` | WebMvc endpoint tests |

## Files to Modify

| File | Modifications |
|------|---------------|
| `ProjectContextSnapshot.java` | Add `List<EngineeringStorySnapshot> recentEngineeringStories` field and `EngineeringStorySnapshot` record |
| `ProjectContextProviderImpl.java` | Add constant `MAX_ENGINEERING_STORIES`, inject `EngineeringStoryRepository`, implement `toEngineeringStorySnapshot()` method |
| `RepositoryContextAdapter.java` | Pass new snapshot field to `synthesizeAnalysisContext()` if needed |
| `AnalysisContext.java` | Add `List<EngineeringStorySnapshot> engineeringStories` field and include in all constructors |
| `AnalysisContextServiceImpl.java` | Pass new snapshot field from `projectContext` to `AnalysisContext` |

## Database Schema Requirements

**Table: `engineering_stories`**
```sql
CREATE TABLE engineering_stories (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    story_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    story_path VARCHAR(500),
    base_commit VARCHAR(64),
    target_commit VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_engineering_story_project
        FOREIGN KEY (project_id)
            REFERENCES projects(id) ON DELETE CASCADE,

    CONSTRAINT uk_engineering_story_project_number
        UNIQUE (project_id, story_number),

    CONSTRAINT ck_engineering_story_status
        CHECK (status IN ('REGISTERED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_engineering_stories_project_id
    ON engineering_stories(project_id);
```

## Missing Information

- None — the Challenge entity provides a complete reference pattern for all new artifacts