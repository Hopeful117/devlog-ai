package com.hopeful117.devlogai.projectcontext.mapper;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectContextContractMapperTest {

    private final ProjectContextContractMapper mapper = new ProjectContextContractMapper();

    @Test
    void mapsProjectSnapshotToContract_whenStatusIsActiveAndHasNotes() {
        var projectSnapshot = new AnalysisContext.ProjectSnapshot(
                UUID.randomUUID(), "Test Project", "test-project",
                "A test project", ProjectStatus.ACTIVE);

        var humanContextInput = new ProjectContextSnapshot.HumanContextInputSnapshot(
                UUID.randomUUID(), ProjectHumanContextInputType.GOAL,
                "Medium-term goal", "Improve semantic usefulness for humans and agents.",
                "ACTIVE", java.time.Instant.now());

        // Use the 14-param constructor that includes humanContextInputs
        var snapshot = new ProjectContextSnapshot(
                projectSnapshot,
                null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(humanContextInput));

        var contract = mapper.toContract(snapshot);

        assertEquals(projectSnapshot.id(), contract.id());
        assertEquals(projectSnapshot.name(), contract.name());
        assertEquals(projectSnapshot.slug(), contract.slug());
        assertEquals(projectSnapshot.description(), contract.description());
        assertEquals("ACTIVE", contract.status());
        assertEquals(1, contract.notes().size());
        var note = contract.notes().getFirst();
        assertEquals(humanContextInput.id(), note.id());
        assertEquals("GOAL", note.type());
        assertEquals("Medium-term goal", note.title());
        assertEquals("Improve semantic usefulness for humans and agents.", note.contentMarkdown());
        assertEquals("ACTIVE", note.status());
        assertTrue(note.updatedAt() != null);
    }

    @Test
    void mapsProjectSnapshotToContract_whenStatusIsActiveAndNoNotes() {
        var projectSnapshot = new AnalysisContext.ProjectSnapshot(
                UUID.randomUUID(), "Test Project", "test-project",
                "A test project", ProjectStatus.ACTIVE);

        // Use the 8-param constructor (project + 7 category groups, rest empty - no humanContextInputs)
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
        assertTrue(contract.notes().isEmpty());
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
                List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());

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
                List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());

        var contract = mapper.toContract(snapshot);

        assertEquals("PAUSED", contract.status());
    }
}