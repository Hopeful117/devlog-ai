# Implementation Plan — Bugfix: JaCoCo Coverage Pipeline Failure

## Approved Repository Analysis

Bugfix scope: JaCoCo configuration change in `backend/pom.xml` to exclude MapStruct-generated code from coverage check.

---

## Implementation Steps

### Step 1: Configure JaCoCo excludes in pom.xml

**File**: `backend/pom.xml`

Add `<excludes>` element to the `jacoco-maven-plugin` `check` execution:

```xml
<execution>
    <id>check</id>
    <goals>
        <goal>check</goal>
    </goals>
    <configuration>
        <excludes>
            <exclude>**/*MapperImpl.class</exclude>
        </excludes>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

**Key detail**: `<excludes>` goes at the goal configuration level, NOT inside `<rules>`. Earlier attempts failed because it was placed inside `<rules>`.

### Step 2: Validate the fix

1. Run `./mvnw verify -Dtest="!DevlogAiBackendApplicationTests" -DfailIfNoTests=false`
2. Confirm "All coverage checks have been met." in output
3. Confirm BUILD SUCCESS
4. Verify coverage report shows 391 classes analyzed (down from 409)

### Step 3: Rebuild Docker container

1. `docker compose build backend`
2. `docker compose up -d backend`
3. Verify container starts successfully

---

## Files Modified

| File | Change |
|------|--------|
| `backend/pom.xml` | Added `<excludes><exclude>**/*MapperImpl.class</exclude></excludes>` to JaCoCo check goal |

## Tests

- No test changes required (configuration-only fix)
- All 533 existing tests must continue to pass
- JaCoCo check must pass with BUILD SUCCESS

## Documentation

- No documentation changes required (CI/CD configuration only)

---

## Acceptance Criteria

1. `./mvnw verify` passes with BUILD SUCCESS
2. JaCoCo check reports "All coverage checks have been met."
3. All 533 tests pass
4. Docker backend container starts successfully
