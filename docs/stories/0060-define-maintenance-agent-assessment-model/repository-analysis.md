# Story 0060 — Define Maintenance Agent Assessment Model — Repository Analysis

## Workflow Status

* Story `0060` is registered in DevLog.
* Repository analysis proceeds from direct repository inspection.

## Story Intent

Story `0060` is asking for the **domain model and persistence layer** for
AI-assisted maintenance assessments.

It is not asking for:

* AI inference logic;
* duplicate ambiguity resolution reasoning;
* cross-surface pattern detection;
* UI integration;
* confidence threshold filtering.

The repository already has:

* a mature `contextmaintenance` package with entities, services, repositories,
  controllers, mappers, and DTOs;
* maintenance findings with full lifecycle (Story `0052`);
* human-reviewed remediation workflows (Story `0056`);
* automatic resolution for deterministic cases (Story `0058`).

The missing piece is a structured domain for AI-assisted assessments that
can interpret ambiguous maintenance situations while remaining advisory and
traceable.

## Relevant Repository Evidence

### 1. The contextmaintenance package is mature and well-structured

The existing package layout:

```text
contextmaintenance/
├── controller/
│   └── MaintenanceFindingController.java
├── dto/
│   ├── request/
│   │   ├── CreateMaintenanceFindingRequest.java
│   │   └── MaintenanceFindingActionRequest.java
│   └── response/
│       ├── MaintenanceEvaluationResponse.java
│       ├── MaintenanceFindingActionResponse.java
│       └── MaintenanceFindingResponse.java
├── entity/
│   ├── MaintenanceContextSurface.java
│   ├── MaintenanceFinding.java
│   ├── MaintenanceFindingAction.java
│   ├── MaintenanceFindingActionType.java
│   ├── MaintenanceFindingIssueType.java
│   ├── MaintenanceFindingSeverity.java
│   ├── MaintenanceFindingStatus.java
│   └── MaintenanceSuggestedActionCategory.java
├── mapper/
│   └── MaintenanceFindingMapper.java
├── repository/
│   └── MaintenanceFindingRepository.java
└── service/
    ├── MaintenanceEvaluationService.java
    ├── MaintenanceEvaluationServiceImpl.java
    ├── MaintenanceFindingService.java
    └── MaintenanceFindingServiceImpl.java
```

This is a clean, consistent pattern. Story `0060` should follow the same
package structure and conventions.

### 2. The existing entity pattern is consistent

`MaintenanceFinding` demonstrates the entity pattern:

* `@Entity` with `@Table`;
* `@GeneratedValue(strategy = GenerationType.UUID)` for id;
* `@ManyToOne(fetch = FetchType.LAZY)` for relationships;
* `@Enumerated(EnumType.STRING)` for enums;
* `@CreatedDate` / `@LastModifiedDate` for auditing;
* `@Builder` / `@Getter` / `@Setter` / `@NoArgsConstructor` / `@AllArgsConstructor`
  from Lombok;
* `@EntityListeners(AuditingEntityListener.class)` for auditing support.

Story `0060` should follow this exact pattern for the assessment entity.

### 3. The existing DTO pattern uses Java records

Response DTOs use Java records with defensive copying:

```java
public record MaintenanceFindingResponse(
        UUID id,
        UUID projectId,
        ...
        List<MaintenanceFindingActionResponse> actionHistory,
        Instant createdAt,
        Instant updatedAt
) {
    public MaintenanceFindingResponse {
        actionHistory = actionHistory == null ? List.of() : List.copyOf(actionHistory);
    }
}
```

Story `0060` should follow this pattern for assessment DTOs.

### 4. The mapper pattern uses MapStruct

`MaintenanceFindingMapper` uses:

* `@Mapper(componentModel = "spring")`;
* `@Mapping(target = "projectId", source = "project.id")` for foreign key
  flattening;
* `@Mapping(target = "actionHistory", source = "actions")` for collection
  mapping.

Story `0060` should follow this pattern for assessment mapping.

### 5. The repository pattern uses Spring Data JPA

`MaintenanceFindingRepository` extends `JpaRepository` with:

* project-scoped queries;
* status-filtered queries;
* id-and-project scoped lookups.

Story `0060` should follow this pattern for assessment repository.

### 6. The migration numbering is sequential

Existing migrations:

* `V39` — maintenance_findings table
* `V40` — maintenance_finding_actions table

Story `0060` should use `V41` for the assessments table.

### 7. The existing service pattern separates interface from implementation

`MaintenanceFindingService` is an interface, `MaintenanceFindingServiceImpl`
is the implementation with `@Service`.

Story `0060` should follow this pattern.

### 8. The maintenance-finding entity has a clear relationship surface

`MaintenanceFinding` currently has:

* `@OneToMany` to `MaintenanceFindingAction` for audit trail;
* `@ManyToOne` to `Project` for project scoping.

Story `0060` needs to add a reverse relationship:

* `MaintenanceAssessment` → `@ManyToOne` to `MaintenanceFinding`;
* `MaintenanceFinding` → potentially `@OneToMany` to `MaintenanceAssessment`
  for read convenience, or keep it lazy and query through the assessment
  repository.

The cleaner approach is to keep assessments queryable through their own
repository and not add a bidirectional relationship, consistent with how
`MaintenanceFindingAction` is modeled.

### 9. The existing controller pattern is RESTful and project-scoped

`MaintenanceFindingController`:

* `@RestController` with `@RequestMapping`;
* `@PathVariable UUID projectId` for project scoping;
* `@Valid @RequestBody` for input validation;
* `ResponseEntity<T>` return types.

Story `0060` should follow this pattern for assessment endpoints.

### 10. ADR-054 defines the assessment output model requirements

From `ADR-054` §5, each assessment must include:

* finding reference;
* interpreted severity;
* semantic classification;
* confidence level;
* rationale;
* recommended action;
* supporting signals.

This maps directly to entity fields and DTO structure.

## Architectural Implications

### A. The assessment entity must reference a maintenance finding

The core relationship is:

* one assessment → exactly one finding;
* one finding → zero or more assessments (over time or across reasoning
  domains).

This is a clean `@ManyToOne` from assessment to finding.

### B. Assessments are advisory artifacts, not lifecycle transitions

Assessments do not modify finding status. They are:

* created by the agent;
* attached to findings for human review;
* retrievable through the API;
* auditable.

They are not:

* lifecycle transitions;
* status changes;
* remediation actions.

### C. The assessment model must be extensible for future reasoning domains

The entity should support:

* multiple reasoning domains (duplicate ambiguity, cross-surface patterns,
  context relevance);
* confidence scoring;
* structured rationale;
* recommended actions;
* supporting signal references.

This suggests a flexible JSON or TEXT field for supporting signals rather
than a rigid relational model.

### D. Assessments should be project-scoped for query efficiency

Like findings, assessments should be queryable by project. This requires:

* a `project_id` denormalized column or a join through the finding;
* project-scoped repository queries.

The cleaner approach is to denormalize `project_id` from the finding, consistent
with how the existing codebase optimizes query patterns.

### E. The assessment table should be a separate table, not embedded

Assessments are a distinct domain concept. They should:

* have their own table;
* have their own entity;
* have their own repository;
* have their own service;
* have their own DTOs.

This follows the existing package structure and keeps the maintenance domain
cleanly separated.

## Likely Implementation Direction

The repository evidence supports the following bounded direction for Story
`0060`:

1. Create `MaintenanceAssessment` entity in `contextmaintenance/entity/`.
   Fields:
   * `id` (UUID, generated);
   * `finding` (`@ManyToOne` to `MaintenanceFinding`);
   * `projectId` (UUID, denormalized from finding);
   * `confidenceLevel` (enum: `HIGH`, `MEDIUM`, `LOW`, `VERY_LOW`);
   * `semanticClassification` (enum: `LIKELY_DUPLICATE`, `LIKELY_ENRICHMENT`,
     `UNCERTAIN`, `CORRELATED_STALENESS`, `ISOLATED_SIGNAL`, `NOT_APPLICABLE`);
   * `recommendedAction` (enum: `RESOLVE`, `DISMISS`, `ESCALATE`, `MONITOR`,
     `NO_ACTION`);
   * `rationale` (TEXT);
   * `supportingSignals` (TEXT, JSON-serialized);
   * `createdAt` (Instant, audit);
   * `updatedAt` (Instant, audit).

2. Create enums:
   * `MaintenanceAssessmentConfidenceLevel`;
   * `MaintenanceAssessmentSemanticClassification`;
   * `MaintenanceAssessmentRecommendedAction`.

3. Create `V41__create_maintenance_assessments_table.sql` migration.

4. Create `MaintenanceAssessmentRepository` with project-scoped queries.

5. Create `MaintenanceAssessmentService` interface and
   `MaintenanceAssessmentServiceImpl` with:
   * `create(UUID projectId, UUID findingId, ...)`;
   * `getByProject(UUID projectId)`;
   * `getByFinding(UUID projectId, UUID findingId)`.

6. Create DTOs:
   * `MaintenanceAssessmentResponse`;
   * `CreateMaintenanceAssessmentRequest`.

7. Create `MaintenanceAssessmentMapper` with MapStruct.

8. Create `MaintenanceAssessmentController` at
   `/api/v1/projects/{projectId}/maintenance-assessments`.

9. Extend `MaintenanceFindingResponse` to include attached assessments
   (optional, could be deferred to Story `0063`).

10. Write tests:
    * entity persistence;
    * repository queries;
    * service create/retrieve;
    * controller endpoints;
    * mapper correctness.

## Expected Affected Areas

### Backend

Likely files to create:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceAssessment.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceAssessmentConfidenceLevel.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceAssessmentSemanticClassification.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceAssessmentRecommendedAction.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/repository/MaintenanceAssessmentRepository.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceAssessmentService.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceAssessmentServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/dto/request/CreateMaintenanceAssessmentRequest.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/dto/response/MaintenanceAssessmentResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/mapper/MaintenanceAssessmentMapper.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceAssessmentController.java`
* `backend/src/main/resources/db/migration/V41__create_maintenance_assessments_table.sql`

Likely files to modify:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/dto/response/MaintenanceFindingResponse.java`
  (add assessments field, deferred to Story `0063`)

Likely tests to create:

* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/MaintenanceAssessmentPostgresIntegrationTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceAssessmentServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceAssessmentControllerWebMvcTest.java`

### Frontend

No frontend changes expected in this Story.

### Documentation

Likely files to update:

* `docs/knowledge-model.md`

## Risks

### 1. Over-engineering the supporting signals model

If `supportingSignals` is modeled as a rigid relational structure, the first
slice will be too complex.

Recommended mitigation:

* use a JSON-serialized TEXT field for supporting signals;
* keep the structure flexible and validated at the service layer;
* defer rigid schema to later Stories when signal types are better understood.

### 2. Adding assessments to the finding response too early

If `MaintenanceFindingResponse` is extended to include assessments in this
Story, it couples the assessment persistence to the finding read path.

Recommended mitigation:

* keep assessment retrieval independent through its own controller/service;
* defer finding-response enrichment to Story `0063`;
* keep this Story focused on the assessment model and persistence.

### 3. Inconsistent enum naming

If assessment enums use different naming conventions than existing maintenance
enums, the codebase will feel inconsistent.

Recommended mitigation:

* follow existing naming patterns:
  * `MaintenanceAssessmentConfidenceLevel` (not `AssessmentConfidence`);
  * `MaintenanceAssessmentSemanticClassification` (not `SemanticType`);
  * `MaintenanceAssessmentRecommendedAction` (not `RecommendedAction`).

### 4. Missing project_id denormalization

If assessments are only queryable through the finding join, project-scoped
queries will be slower and more complex.

Recommended mitigation:

* denormalize `project_id` from the finding at creation time;
* add project-scoped indexes consistent with existing patterns.

## Conclusion

Story `0060` is feasible and well-scoped. The repository has a mature,
consistent `contextmaintenance` package that provides a clear template for
the assessment model.

The strongest implementation approach is:

1. create assessment entity, enums, repository, service, DTOs, mapper,
   controller following existing conventions;
2. use a separate table with project-scoped indexing;
3. keep supporting signals flexible with JSON serialization;
4. defer finding-response enrichment to Story `0063`;
5. write comprehensive tests covering persistence, retrieval, and API
   contracts.

Repository Analysis ready for human review.
