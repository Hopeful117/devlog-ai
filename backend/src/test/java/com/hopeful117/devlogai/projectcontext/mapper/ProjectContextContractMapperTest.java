package com.hopeful117.devlogai.projectcontext.mapper;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectContextContractMapperTest {

    private final ProjectContextContractMapper mapper = new ProjectContextContractMapper();

    @Test
    void mapsProjectSnapshotToContract_whenStatusIsActive() {
        var projectSnapshot = new AnalysisContext.ProjectSnapshot(
                UUID.randomUUID(), "Test Project", "test-project",
                "A test project", ProjectStatus.ACTIVE);

        // Use the 8-param constructor (project + 7 category groups, rest empty)
        var snapshot = new ProjectContextSnapshot(
                projectSnapshot,
                null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());

        var contract = mapper.toContract(snapshot);

        assertEquals(projectSnapshot.id(), contract.id());
        assertEquals(projectSnapshot.name(), contract.name());
        assertEquals(projectSnapshot.slug(), contract.slug());
        assertEquals(projectSnapshot.description(), contract.description());
        assertEquals("ACTIVE", contract.status());
    }

    @Test
    void mapsProjectSnapshotToContract_whenStatusIsArchived() {
        var projectSnapshot = new AnalysisContext.ProjectSnapshot(
                UUID.randomUUID(), "Archived Project", "archived-project",
                "An archived project", ProjectStatus.ARCHIVED);

        var snapshot = new ProjectContextSnapshot(
                projectSnapshot,
                null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());

        var contract = mapper.toContract(snapshot);

        assertEquals("ARCHIVED", contract.status());
    }

    @Test
    void mapsProjectSnapshotToContract_whenStatusIsPaused() {
        var projectSnapshot = new AnalysisContext.ProjectSnapshot(
                UUID.randomUUID(), "Paused Project", "paused-project",
                "A paused project", ProjectStatus.PAUSED);

        var snapshot = new ProjectContextSnapshot(
                projectSnapshot,
                null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());

        var contract = mapper.toContract(snapshot);

        assertEquals("PAUSED", contract.status());
    }
}