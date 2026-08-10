# Implementation Plan — Story 0027

## Story

Story 0027 — Decision CRUD Completion: Add update and delete operations to the Engineering Decision API.

## Approved Repository Analysis

Follow the Challenge pattern (Story 0024) for update/delete operations.

## Implementation Steps

### Step 1 — Create UpdateDecisionRequest DTO

**File**: `UpdateDecisionRequest.java` (new)

```java
package com.hopeful117.devlogai.decision.dto.request;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDecisionRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String context;
    @NotBlank
    private String choice;
    @NotBlank
    private String rationale;
    private String consequences;
}
```

**Rationale**: Same fields as CreateDecisionRequest but without `projectId` (project immutability).

---

### Step 2 — Add update() and delete() to DecisionService interface

**File**: `DecisionService.java`

```java
DecisionResponse update(UUID id, UpdateDecisionRequest request);
void delete(UUID id);
```

---

### Step 3 — Implement update() and delete() in DecisionServiceImpl

**File**: `DecisionServiceImpl.java`

```java
@Override
public DecisionResponse update(UUID id, UpdateDecisionRequest request) {
    Decision decision = decisionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Decision", id));
    
    decision.setTitle(request.getTitle());
    decision.setContext(request.getContext());
    decision.setChoice(request.getChoice());
    decision.setRationale(request.getRationale());
    decision.setConsequences(request.getConsequences());
    
    Decision saved = decisionRepository.save(decision);
    return decisionMapper.toResponse(saved);
}

@Override
public void delete(UUID id) {
    Decision decision = decisionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Decision", id));
    decisionRepository.delete(decision);
}
```

**Rationale**: Standard JPA update pattern. `@LastModifiedDate` handles timestamp refresh automatically.

---

### Step 4 — Add PUT and DELETE endpoints to DecisionController

**File**: `DecisionController.java`

```java
@PutMapping("/{id}")
public ResponseEntity<DecisionResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateDecisionRequest request) {
    return ResponseEntity.ok(decisionService.update(id, request));
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable UUID id) {
    decisionService.delete(id);
    return ResponseEntity.noContent().build();
}
```

---

### Step 5 — Add unit tests

**File**: `DecisionServiceTest.java`

4 tests to add:
1. `shouldUpdateDecisionSuccessfully` — update all fields, verify persistence
2. `shouldThrowExceptionWhenUpdatingNonExistentDecision` — EntityNotFoundException
3. `shouldDeleteDecisionSuccessfully` — verify deletion
4. `shouldThrowExceptionWhenDeletingNonExistentDecision` — EntityNotFoundException

---

### Step 6 — Validation

- `./mvnw compile`
- `./mvnw test -Dtest="DecisionServiceTest"`
- `./mvnw test` (full suite)
- SonarQube (if token available)

---

## Files Changed

| File | Change |
|------|--------|
| `UpdateDecisionRequest.java` | New DTO |
| `DecisionService.java` | +2 method signatures |
| `DecisionServiceImpl.java` | +2 method implementations |
| `DecisionController.java` | +2 endpoints |
| `DecisionServiceTest.java` | +4 tests |

## No Migration Required

Uses existing `decisions` table (V5).

## Expected Outcome

- 513+ tests passing (509 + 4 new)
- SonarQube Quality Gate PASSED
- Complete CRUD API for Engineering Decisions
