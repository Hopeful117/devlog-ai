# Story 0060 — Define Maintenance Agent Assessment Model — Implementation Plan

## Overview

Implement Story `0060` as the **domain foundation for AI-assisted maintenance
assessments**.

The goal is not to deliver AI inference logic or reasoning domains yet.

The goal is to establish a first-class persisted assessment model that later
Stories can reuse for:

* duplicate ambiguity resolution (`0061`);
* cross-surface pattern detection (`0062`);
* lifecycle integration (`0063`);
* confidence filtering (`0064`);
* validation and test coverage (`0065`).

The implementation should therefore:

* introduce a dedicated assessment domain in the `contextmaintenance` package;
* keep assessments separate from findings, trusted knowledge, and proposal
  history;
* define a narrow initial taxonomy aligned with ADR-054 requirements;
* provide enough persistence and retrieval for later reasoning domains without
  encoding inference logic now.

## Final Implementation Strategy

The preferred implementation is:

1. create assessment entity, enums, repository, service, DTOs, mapper,
   and controller following existing `contextmaintenance` conventions;
2. use a separate table with project-scoped indexing;
3. keep supporting signals flexible with JSON serialization;
4. defer finding-response enrichment to Story `0063`;
5. write comprehensive tests covering persistence, retrieval, and API
   contracts;
6. update canonical documentation with the new assessment domain.

This Story should stop at assessment model readiness.

It should not yet implement:

* AI inference logic for generating assessments;
* duplicate ambiguity resolution reasoning;
* cross-surface pattern detection;
* confidence threshold filtering;
* assessment display in the maintenance cockpit.

## Step 1 — Define assessment enums

Targets:

* new enum files under `contextmaintenance/entity/`

Goals:

* satisfy ADR-054 §5 with a bounded classification model;
* establish stable vocabulary for later reasoning domains;
* follow existing enum naming conventions.

Implementation direction:

Introduce three enums:

### `MaintenanceAssessmentConfidenceLevel`

```text
HIGH        — strong evidence supports the assessment
MEDIUM      — moderate evidence, reasonable confidence
LOW         — weak evidence, uncertain assessment
VERY_LOW    — minimal evidence, assessment likely unreliable
```

### `MaintenanceAssessmentSemanticClassification`

```text
LIKELY_DUPLICATE       — candidate records likely express the same knowledge
LIKELY_ENRICHMENT      — candidate records likely represent legitimate extension
UNCERTAIN              — overlap is ambiguous, human judgment preferred
CORRELATED_STALENESS   — multiple signals suggest broader context degradation
ISOLATED_SIGNAL        — single finding without cross-surface correlation
NOT_APPLICABLE         — assessment does not apply semantic classification
```

### `MaintenanceAssessmentRecommendedAction`

```text
RESOLVE     — finding can likely be resolved based on assessment
DISMISS     — finding can likely be dismissed as false positive
ESCALATE    — requires human judgment, ambiguous case
MONITOR     — informational, no immediate action needed
NO_ACTION   — assessment does not recommend any action
```

Rationale:

These enums provide enough vocabulary for the first two reasoning domains
(duplicate ambiguity and cross-surface patterns) while remaining narrow enough
to avoid speculative overreach.

## Step 2 — Create the assessment entity

Targets:

* new entity file under `contextmaintenance/entity/`
* Flyway migration `V41`

Goals:

* satisfy Story AC-1 with a first-class persisted assessment model;
* establish a stable record shape for later reasoning and retrieval;
* preserve explicit project scoping and audit timestamps.

Implementation direction:

Create `MaintenanceAssessment` entity with fields:

* `id` — UUID, generated
* `finding` — `@ManyToOne(fetch = FetchType.LAZY)` to `MaintenanceFinding`
* `projectId` — UUID, denormalized from finding at creation time
* `confidenceLevel` — `@Enumerated(EnumType.STRING)`
* `semanticClassification` — `@Enumerated(EnumType.STRING)`
* `recommendedAction` — `@Enumerated(EnumType.STRING)`
* `rationale` — `TEXT`, required
* `supportingSignals` — `TEXT`, JSON-serialized, optional
* `createdAt` — `Instant`, audit timestamp
* `updatedAt` — `Instant`, audit timestamp

Follow the exact entity pattern from `MaintenanceFinding`:

* `@Entity` / `@Table(name = "maintenance_assessments")`
* `@GeneratedValue(strategy = GenerationType.UUID)`
* `@Enumerated(EnumType.STRING)` for all enums
* `@CreatedDate` / `@LastModifiedDate` for auditing
* `@Builder` / `@Getter` / `@Setter` / `@NoArgsConstructor` / `@AllArgsConstructor`
* `@EntityListeners(AuditingEntityListener.class)`

Important:

* do NOT add `@OneToMany` from `MaintenanceFinding` to `MaintenanceAssessment`;
  keep the relationship unidirectional through the assessment repository;
* denormalize `project_id` from the finding at creation time for efficient
  project-scoped queries.

Rationale:

A dedicated entity with its own table keeps the assessment domain cleanly
separated and queryable, consistent with how `MaintenanceFindingAction` is
modeled.

## Step 3 — Create the database migration

Targets:

* `backend/src/main/resources/db/migration/V41__create_maintenance_assessments_table.sql`

Goals:

* create durable storage for maintenance assessments;
* keep schema explicit and auditable;
* preserve foreign key relationship to findings and projects.

Implementation direction:

```sql
CREATE TABLE maintenance_assessments
(
    id                       UUID PRIMARY KEY,
    finding_id               UUID         NOT NULL REFERENCES maintenance_findings (id) ON DELETE CASCADE,
    project_id               UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    confidence_level         VARCHAR(20)  NOT NULL,
    semantic_classification  VARCHAR(40)  NOT NULL,
    recommended_action       VARCHAR(20)  NOT NULL,
    rationale                TEXT         NOT NULL,
    supporting_signals       TEXT,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maintenance_assessments_project_created
    ON maintenance_assessments (project_id, created_at DESC, id DESC);

CREATE INDEX idx_maintenance_assessments_finding_created
    ON maintenance_assessments (finding_id, created_at DESC, id DESC);
```

Rationale:

The schema follows existing migration patterns with UUID primary keys,
foreign key constraints, enum-backed varchar columns, and project-scoped
indexes.

## Step 4 — Create the repository

Targets:

* `contextmaintenance/repository/MaintenanceAssessmentRepository.java`

Goals:

* provide project-scoped and finding-scoped retrieval;
* follow Spring Data JPA conventions from `MaintenanceFindingRepository`.

Implementation direction:

```java
public interface MaintenanceAssessmentRepository
        extends JpaRepository<MaintenanceAssessment, UUID> {

    List<MaintenanceAssessment> findByProject_IdOrderByCreatedAtDescIdDesc(
            UUID projectId
    );

    List<MaintenanceAssessment> findByFinding_IdAndProject_IdOrderByCreatedAtDescIdDesc(
            UUID findingId,
            UUID projectId
    );

    Optional<MaintenanceAssessment> findByIdAndProject_Id(
            UUID id,
            UUID projectId
    );
}
```

Rationale:

The repository provides the minimum query surface needed by later Stories
without overcommitting to speculative query patterns.

## Step 5 — Create DTOs

Targets:

* `contextmaintenance/dto/request/CreateMaintenanceAssessmentRequest.java`
* `contextmaintenance/dto/response/MaintenanceAssessmentResponse.java`

Goals:

* satisfy Story AC-4 with retrievable assessment data;
* follow existing DTO conventions from `MaintenanceFindingResponse`.

Implementation direction:

### `CreateMaintenanceAssessmentRequest`

```java
public record CreateMaintenanceAssessmentRequest(
        UUID findingId,
        MaintenanceAssessmentConfidenceLevel confidenceLevel,
        MaintenanceAssessmentSemanticClassification semanticClassification,
        MaintenanceAssessmentRecommendedAction recommendedAction,
        String rationale,
        String supportingSignals
) {}
```

### `MaintenanceAssessmentResponse`

```java
public record MaintenanceAssessmentResponse(
        UUID id,
        UUID projectId,
        UUID findingId,
        MaintenanceAssessmentConfidenceLevel confidenceLevel,
        MaintenanceAssessmentSemanticClassification semanticClassification,
        MaintenanceAssessmentRecommendedAction recommendedAction,
        String rationale,
        String supportingSignals,
        Instant createdAt,
        Instant updatedAt
) {}
```

Rationale:

The DTOs keep the API contract explicit and independent from the entity
model, consistent with existing `contextmaintenance` conventions.

## Step 6 — Create the mapper

Targets:

* `contextmaintenance/mapper/MaintenanceAssessmentMapper.java`

Goals:

* map between entity and DTO using MapStruct;
* follow existing mapper conventions from `MaintenanceFindingMapper`.

Implementation direction:

```java
@Mapper(componentModel = "spring")
public interface MaintenanceAssessmentMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "findingId", source = "finding.id")
    MaintenanceAssessmentResponse toResponse(MaintenanceAssessment assessment);

    List<MaintenanceAssessmentResponse> toResponse(List<MaintenanceAssessment> assessments);
}
```

Rationale:

MapStruct with Spring component model provides type-safe, compile-time
mapping consistent with the existing codebase.

## Step 7 — Create the service

Targets:

* `contextmaintenance/service/MaintenanceAssessmentService.java` (interface)
* `contextmaintenance/service/MaintenanceAssessmentServiceImpl.java`
  (implementation)

Goals:

* provide create, retrieve-by-project, and retrieve-by-finding operations;
* validate that the referenced finding exists and belongs to the project;
* follow the interface/implementation split from `MaintenanceFindingService`.

Implementation direction:

### Interface

```java
public interface MaintenanceAssessmentService {

    MaintenanceAssessmentResponse create(
            UUID projectId,
            CreateMaintenanceAssessmentRequest request
    );

    List<MaintenanceAssessmentResponse> getByProject(UUID projectId);

    List<MaintenanceAssessmentResponse> getByFinding(
            UUID projectId,
            UUID findingId
    );
}
```

### Implementation

The service should:

* validate that the finding exists and belongs to the project;
* denormalize `projectId` from the finding at creation time;
* persist the assessment through the repository;
* map to response using the mapper.

Rationale:

A minimal service boundary provides enough behavior for later reasoning
domain Stories and controller integration.

## Step 8 — Create the controller

Targets:

* `contextmaintenance/controller/MaintenanceAssessmentController.java`

Goals:

* expose assessment creation and retrieval through REST endpoints;
* follow existing controller conventions from `MaintenanceFindingController`.

Implementation direction:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/maintenance-assessments")
public class MaintenanceAssessmentController {

    private final MaintenanceAssessmentService service;

    @GetMapping
    public ResponseEntity<List<MaintenanceAssessmentResponse>> getByProject(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(service.getByProject(projectId));
    }

    @GetMapping("/findings/{findingId}")
    public ResponseEntity<List<MaintenanceAssessmentResponse>> getByFinding(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId
    ) {
        return ResponseEntity.ok(service.getByFinding(projectId, findingId));
    }

    @PostMapping
    public ResponseEntity<MaintenanceAssessmentResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMaintenanceAssessmentRequest request
    ) {
        return ResponseEntity.ok(service.create(projectId, request));
    }
}
```

Rationale:

The controller provides the minimum API surface needed for later Stories
without overcommitting to speculative endpoints.

## Step 9 — Write tests

Targets:

* `MaintenanceAssessmentPostgresIntegrationTest.java`
* `MaintenanceAssessmentServiceTest.java`
* `MaintenanceAssessmentControllerWebMvcTest.java`

Goals:

* satisfy Story AC-5 and AC-6 with comprehensive backend coverage;
* prove persistence, retrieval, and API contract correctness;
* guard the separation from findings and other knowledge models.

Implementation direction:

### Integration test

* persist an assessment linked to an existing finding;
* verify project-scoped retrieval;
* verify finding-scoped retrieval;
* verify audit timestamps are set correctly.

### Service test

* create assessment for valid finding;
* reject assessment for non-existent finding;
* reject assessment for finding belonging to different project;
* retrieve assessments by project;
* retrieve assessments by finding;
* verify response mapping correctness.

### Controller test

* GET `/api/v1/projects/{projectId}/maintenance-assessments` returns list;
* GET `/api/v1/projects/{projectId}/maintenance-assessments/findings/{findingId}`
  returns list;
* POST `/api/v1/projects/{projectId}/maintenance-assessments` creates assessment;
* POST with invalid request returns validation error;
* POST with non-existent finding returns not found.

Validation commands:

```bash
cd backend
./mvnw -Dtest=MaintenanceAssessmentPostgresIntegrationTest,MaintenanceAssessmentServiceTest,MaintenanceAssessmentControllerWebMvcTest test
```

Rationale:

Tests validate the full stack from persistence through API, ensuring the
assessment model is ready for later reasoning domain integration.

## Step 10 — Update canonical documentation

Targets:

* `docs/knowledge-model.md`

Goals:

* satisfy Story AC-7 by documenting the assessment model boundaries;
* describe the relationship between findings and assessments;
* record that assessments are advisory artifacts, not lifecycle transitions.

Implementation direction:

Update the knowledge model to explain:

* maintenance assessments are AI-assisted interpretation artifacts;
* they reference findings but do not modify finding lifecycle;
* they include confidence, classification, rationale, and recommended action;
* they are advisory and reviewable, not authoritative.

Rationale:

Documentation makes the assessment domain explicit and available for later
Stories and future consumers.

## Planned Validation Commands

Backend:

```bash
cd backend
./mvnw -Dtest=MaintenanceAssessmentPostgresIntegrationTest,MaintenanceAssessmentServiceTest,MaintenanceAssessmentControllerWebMvcTest test
```

Full backend suite:

```bash
cd backend
./mvnw test -DskipITs
```

## Expected Outcome

After Story `0060`:

* DevLog has a first-class persisted maintenance-assessment model in Core;
* assessments reference findings with explicit confidence, classification,
  rationale, and recommended action;
* assessments are retrievable through project-scoped and finding-scoped
  API endpoints;
* the assessment domain is cleanly separated from findings and other
  knowledge models;
* comprehensive tests validate persistence, retrieval, and API contracts;
* canonical documentation records the assessment model boundaries.

It should not yet have:

* AI inference logic for generating assessments;
* duplicate ambiguity resolution reasoning;
* cross-surface pattern detection;
* confidence threshold filtering;
* assessment display in the maintenance cockpit.

Implementation Plan ready for human review.
