package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.knowledge.selection.SemanticSection.SectionId;
import com.hopeful117.devlogai.knowledge.selection.SemanticSection.PromptSemanticSection;
import com.hopeful117.devlogai.knowledge.selection.SemanticSection.PromptSemanticSectionItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Component
public class SemanticSectionComposer {

    private static final String SECTION_TITLE_PROJECT_STATE = "Project State";
    private static final String SECTION_TITLE_ARCHITECTURE = "Architecture";
    private static final String SECTION_TITLE_DECISIONS = "Decisions";
    private static final String SECTION_TITLE_VALIDATED_KNOWLEDGE = "Validated Knowledge";
    private static final String SECTION_TITLE_HISTORY = "History";
    private static final String SECTION_TITLE_REPOSITORY_CHANGES = "Repository Changes";
    private static final String SECTION_TITLE_HUMAN_CONTEXT = "Human Context";

    public List<PromptSemanticSection> compose(SelectedKnowledge selectedKnowledge) {
        Map<SectionId, List<PromptSemanticSectionItem>> sections = new EnumMap<>(SectionId.class);
        for (SectionId id : SectionId.values()) {
            sections.put(id, new ArrayList<>());
        }

        classifyFacts(selectedKnowledge.selectedFacts(), sections);
        classifyObservations(selectedKnowledge.selectedObservations(), sections);
        classifyInsights(selectedKnowledge.selectedInsights(), sections);
        classifyExistingArchitectureKnowledge(selectedKnowledge.existingArchitectureKnowledge(), sections);
        classifyEngineeringEvents(selectedKnowledge.selectedEngineeringEvents(), sections);
        classifyHumanContext(selectedKnowledge.selectedHumanContextInputs(), sections);
        classifyRepositoryEvidence(selectedKnowledge.repositoryContext(), sections);
        classifyProjectIdentity(selectedKnowledge.project(), sections);
        classifyAnalysisIdentity(selectedKnowledge.analysis(), sections);
        classifyProjectProfile(selectedKnowledge.projectProfile(), sections);
        classifyEvolutionContext(selectedKnowledge.evolutionContext(), sections);

        return buildSections(sections);
    }

    private void classifyFacts(List<AnalysisContext.FactSnapshot> facts,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        for (AnalysisContext.FactSnapshot fact : facts) {
            Set<SectionId> memberships = SemanticSection.classifyFact(fact.type());
            for (SectionId section : memberships) {
                sections.get(section).add(new PromptSemanticSectionItem(
                        "FACT", fact.id().toString(), fact.type().name()));
            }
        }
    }

    private void classifyObservations(List<AnalysisContext.ObservationSnapshot> observations,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        for (AnalysisContext.ObservationSnapshot observation : observations) {
            Set<SectionId> memberships = SemanticSection.classifyObservation(observation.type());
            for (SectionId section : memberships) {
                sections.get(section).add(new PromptSemanticSectionItem(
                        "OBSERVATION", observation.id().toString(), observation.type().name()));
            }
        }
    }

    private void classifyInsights(List<SelectedKnowledge.InsightSnapshot> insights,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        for (SelectedKnowledge.InsightSnapshot insight : insights) {
            Set<SectionId> memberships = SemanticSection.classifyInsight(insight.type());
            for (SectionId section : memberships) {
                sections.get(section).add(new PromptSemanticSectionItem(
                        "INSIGHT", insight.id().toString(), insight.type().name()));
            }
        }
    }

    private void classifyExistingArchitectureKnowledge(
            List<SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot> archKnowledge,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        for (SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot knowledge : archKnowledge) {
            sections.get(SectionId.ARCHITECTURE).add(new PromptSemanticSectionItem(
                    "ARCHITECTURE_KNOWLEDGE", knowledge.insightId().toString(),
                    knowledge.title() != null ? knowledge.title() : knowledge.normalizedType().name()));
        }
    }

    private void classifyEngineeringEvents(
            List<ProjectContextSnapshot.EngineeringEventSnapshot> events,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        for (ProjectContextSnapshot.EngineeringEventSnapshot event : events) {
            sections.get(SectionId.VALIDATED_KNOWLEDGE).add(new PromptSemanticSectionItem(
                    "ENGINEERING_EVENT", event.id().toString(),
                    event.title() != null ? event.title() : event.category()));
        }
    }

    private void classifyHumanContext(
            List<ProjectContextSnapshot.HumanContextInputSnapshot> humanContextInputs,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        for (ProjectContextSnapshot.HumanContextInputSnapshot input : humanContextInputs) {
            Set<SectionId> memberships = SemanticSection.classifyHumanContext(input.type());
            for (SectionId section : memberships) {
                sections.get(section).add(new PromptSemanticSectionItem(
                        "HUMAN_CONTEXT", input.id().toString(), input.type().name()));
            }
        }
    }

    private void classifyRepositoryEvidence(com.hopeful117.devlogai.repositorycontext.RepositoryContext repositoryContext,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        if (repositoryContext == null) {
            return;
        }
        for (RepositoryEvidence evidence : repositoryContext.evidence()) {
            Set<SectionId> memberships = SemanticSection.classifyRepositoryLayer(evidence.layer());
            for (SectionId section : memberships) {
                sections.get(section).add(new PromptSemanticSectionItem(
                        "REPOSITORY_EVIDENCE", evidence.reference(),
                        evidence.kind()));
            }
        }
    }

    private void classifyProjectIdentity(AnalysisContext.ProjectSnapshot project,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        if (project != null) {
            sections.get(SectionId.PROJECT_STATE).add(new PromptSemanticSectionItem(
                    "PROJECT", project.id().toString(), project.name()));
        }
    }

    private void classifyAnalysisIdentity(AnalysisContext.AnalysisSnapshot analysis,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        if (analysis != null) {
            sections.get(SectionId.PROJECT_STATE).add(new PromptSemanticSectionItem(
                    "ANALYSIS", analysis.id().toString(),
                    analysis.intentId() != null ? analysis.intentId() : "ANALYSIS"));
        }
    }

    private void classifyProjectProfile(com.hopeful117.devlogai.profile.dto.ProjectProfileResponse profile,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        if (profile != null) {
            sections.get(SectionId.PROJECT_STATE).add(new PromptSemanticSectionItem(
                    "PROJECT_PROFILE", profile.id().toString(), "PROFILE"));
        }
    }

    private void classifyEvolutionContext(AnalysisContext.EvolutionContext evolutionContext,
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        if (evolutionContext != null) {
            sections.get(SectionId.HISTORY).add(new PromptSemanticSectionItem(
                    "EVOLUTION_CONTEXT", evolutionContext.sourceId().toString(), "EVOLUTION"));
        }
    }

    private List<PromptSemanticSection> buildSections(
            Map<SectionId, List<PromptSemanticSectionItem>> sections) {
        List<PromptSemanticSection> result = new ArrayList<>();
        for (SectionId sectionId : SectionId.values()) {
            List<PromptSemanticSectionItem> items = sections.get(sectionId);
            if (!items.isEmpty()) {
                List<PromptSemanticSectionItem> sorted = items.stream()
                        .sorted(Comparator.comparing(PromptSemanticSectionItem::itemType)
                                .thenComparing(PromptSemanticSectionItem::label)
                                .thenComparing(PromptSemanticSectionItem::itemId))
                        .toList();
                result.add(new PromptSemanticSection(
                        sectionId.name(),
                        sectionTitle(sectionId),
                        sorted));
            }
        }
        return result;
    }

    private String sectionTitle(SectionId sectionId) {
        return switch (sectionId) {
            case PROJECT_STATE -> SECTION_TITLE_PROJECT_STATE;
            case ARCHITECTURE -> SECTION_TITLE_ARCHITECTURE;
            case DECISIONS -> SECTION_TITLE_DECISIONS;
            case VALIDATED_KNOWLEDGE -> SECTION_TITLE_VALIDATED_KNOWLEDGE;
            case HISTORY -> SECTION_TITLE_HISTORY;
            case REPOSITORY_CHANGES -> SECTION_TITLE_REPOSITORY_CHANGES;
            case HUMAN_CONTEXT -> SECTION_TITLE_HUMAN_CONTEXT;
        };
    }
}
