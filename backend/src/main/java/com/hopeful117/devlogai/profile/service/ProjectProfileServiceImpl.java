package com.hopeful117.devlogai.profile.service;

import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import com.hopeful117.devlogai.profile.model.*;
import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectProfileServiceImpl implements ProjectProfileService {
    public static final String PROFILE_VERSION = "project-profile-v1";
    private final AnalysisRepository analysisRepository;
    private final AnalysisExecutionDiagnosticRepository diagnosticRepository;
    private final ObservationRepository observationRepository;
    private final ProjectProfileSnapshotRepository profileRepository;
    private final ProjectProfileRenderer renderer;

    @Override
    @Transactional
    public ProjectProfileResponse build(UUID analysisId) {
        Optional<ProjectProfileSnapshot> existing = profileRepository.findByAnalysisId(analysisId);
        if (existing.isPresent()) return toResponse(existing.get());

        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        AnalysisExecutionDiagnostic diagnostic = diagnosticRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis diagnostics", analysisId));
        List<Observation> observations = observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId);
        observations.forEach(observation -> validate(observation, analysis));

        List<ProfileSection> sections = buildSections(observations);
        ProfileCompletenessStatus completeness = completeness(diagnostic);
        List<SourceObservationSnapshot> sources = observations.stream()
                .map(o -> new SourceObservationSnapshot(o.getId(), o.getType(), o.getRuleId(), o.getRuleVersion()))
                .sorted(Comparator.comparing(source -> source.id().toString()))
                .toList();
        int characteristicCount = sections.stream().mapToInt(s -> s.characteristics().size()).sum();

        ProjectProfileSnapshot profile = ProjectProfileSnapshot.builder()
                .id(UUID.randomUUID()).project(analysis.getProject()).analysis(analysis)
                .profileVersion(PROFILE_VERSION).rendererVersion(ProjectProfileRenderer.VERSION)
                .generatedAt(Instant.now()).requestedRevision(analysis.getTargetRevision())
                .resolvedRevisions(Map.copyOf(diagnostic.getResolvedRevisions()))
                .completenessStatus(completeness).collectionComplete(diagnostic.isCollectionComplete())
                .truncated(diagnostic.isTruncated()).warningCount(diagnostic.getWarningCount())
                .errorCount(diagnostic.getErrorCount()).successfulCollectorCount(diagnostic.getSuccessfulCollectors())
                .collectorsWithWarningsCount(diagnostic.getCollectorsWithWarnings())
                .failedCollectorCount(diagnostic.getFailedCollectors())
                .sections(toSectionMaps(sections)).deterministicSummary(renderer.render(sections, completeness))
                .sourceObservations(toSourceMaps(sources)).characteristicCount(characteristicCount)
                .build();
        return toResponse(profileRepository.save(profile));
    }

    @Override @Transactional(readOnly = true)
    public ProjectProfileResponse getByAnalysis(UUID analysisId) {
        return profileRepository.findByAnalysisId(analysisId).map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Project profile", analysisId));
    }

    @Override @Transactional(readOnly = true)
    public ProjectProfileResponse getLatestByProject(UUID projectId) {
        return profileRepository.findFirstByProjectIdOrderByGeneratedAtDescIdDesc(projectId)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Project profile", projectId));
    }

    private void validate(Observation observation, Analysis analysis) {
        if (!analysis.getId().equals(observation.getAnalysis().getId()))
            throw new IllegalArgumentException("Observation belongs to another Analysis");
        if (observation.getSupportingFacts().isEmpty())
            throw new IllegalArgumentException("Observation has no supporting Fact: " + observation.getId());
        if (observation.getSupportingFacts().stream().anyMatch(f -> !analysis.getId().equals(f.getAnalysis().getId())))
            throw new IllegalArgumentException("Observation references a Fact from another Analysis");
        if (observation.getRuleId() == null || observation.getRuleVersion() == null)
            throw new IllegalArgumentException("Observation rule traceability is missing");
    }

    private List<ProfileSection> buildSections(List<Observation> observations) {
        Map<ProfileCategory, Map<String, MutableCharacteristic>> grouped = new EnumMap<>(ProfileCategory.class);
        for (Observation observation : observations) {
            List<Mapping> mappings = mappings(observation.getType());
            for (Mapping mapping : mappings) {
                MutableCharacteristic value = grouped
                        .computeIfAbsent(mapping.category(), ignored -> new TreeMap<>())
                        .computeIfAbsent(mapping.code(), ignored -> new MutableCharacteristic(mapping));
                value.add(observation);
            }
        }
        return Arrays.stream(ProfileCategory.values()).filter(grouped::containsKey)
                .map(category -> {
                    List<ProfileCharacteristic> characteristics = grouped.get(category).values().stream()
                            .map(MutableCharacteristic::freeze).toList();
                    return new ProfileSection(category, characteristics,
                            characteristics.stream().map(ProfileCharacteristic::label).collect(java.util.stream.Collectors.joining(", ")));
                }).toList();
    }

    private List<Mapping> mappings(ObservationType type) {
        return switch (type) {
            case SPRING_BOOT_REST_APPLICATION -> List.of(
                    mapping(ProfileCategory.TECHNOLOGIES, "SPRING_BOOT", "Spring Boot", "Spring Boot est utilisé par l’application REST."),
                    mapping(ProfileCategory.ARCHITECTURE, "SPRING_BOOT_REST_APPLICATION", "Application REST Spring Boot", "Une application REST Spring Boot est exposée."),
                    mapping(ProfileCategory.API, "REST_API", "API REST", "Le projet expose des contrôleurs REST."));
            case CONTAINERIZED_PROJECT -> List.of(mapping(ProfileCategory.INFRASTRUCTURE, "CONTAINERIZED_PROJECT", "Projet conteneurisé", "Docker et Docker Compose sont configurés."));
            case ARCHITECTURE_DOCUMENTATION_PRESENT -> List.of(mapping(ProfileCategory.DOCUMENTATION, "ADR_DOCUMENTATION", "Documentation ADR", "Des décisions d’architecture sont documentées sous forme d’ADR."));
            case AUTOMATED_TEST_SUITE_PRESENT -> List.of(mapping(ProfileCategory.TESTING, "AUTOMATED_TESTS", "Tests automatisés", "Une arborescence et des fichiers de tests automatisés sont présents."));
            case INTEGRATION_TEST_SUITE_PRESENT -> List.of(mapping(ProfileCategory.TESTING, "INTEGRATION_TESTS", "Tests d’intégration", "Des fichiers de tests d’intégration sont présents."));
            case MULTI_MODULE_BUILD -> List.of(
                    mapping(ProfileCategory.BUILD, "MULTI_MODULE_BUILD", "Build multi-module", "Le système de build déclare plusieurs modules."),
                    mapping(ProfileCategory.ARCHITECTURE, "MULTI_MODULE_ARCHITECTURE", "Architecture multi-module", "Le projet est structuré en plusieurs modules de build."));
            default -> throw new IllegalArgumentException("Unsupported Observation type for profile v1: " + type);
        };
    }

    private Mapping mapping(ProfileCategory category, String code, String label, String description) {
        return new Mapping(category, code, label, description);
    }

    private ProfileCompletenessStatus completeness(AnalysisExecutionDiagnostic d) {
        return d.isCollectionComplete() && !d.isTruncated() && d.getFailedCollectors() == 0
                && d.getWarningCount() == 0 && d.getErrorCount() == 0
                ? ProfileCompletenessStatus.COMPLETE : ProfileCompletenessStatus.PARTIAL;
    }

    private List<Map<String, Object>> toSectionMaps(List<ProfileSection> sections) {
        return sections.stream().map(section -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("category", section.category().name());
            map.put("characteristics", section.characteristics().stream().map(this::toCharacteristicMap).toList());
            map.put("deterministicSummary", section.deterministicSummary());
            return Map.copyOf(map);
        }).toList();
    }

    private Map<String, Object> toCharacteristicMap(ProfileCharacteristic c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", c.code()); map.put("label", c.label()); map.put("description", c.description());
        map.put("status", c.status().name()); map.put("sourceObservationIds", c.sourceObservationIds());
        map.put("evidenceCount", c.evidenceCount()); map.put("metadata", c.metadata());
        return Map.copyOf(map);
    }

    private List<Map<String, Object>> toSourceMaps(List<SourceObservationSnapshot> sources) {
        return sources.stream().map(source -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", source.id()); map.put("type", source.type().name());
            map.put("ruleId", source.ruleId()); map.put("ruleVersion", source.ruleVersion());
            return Map.copyOf(map);
        }).toList();
    }

    private ProjectProfileResponse toResponse(ProjectProfileSnapshot p) {
        return new ProjectProfileResponse(p.getId(), p.getProject().getId(), p.getAnalysis().getId(),
                p.getProfileVersion(), p.getRendererVersion(), p.getGeneratedAt(), p.getRequestedRevision(),
                p.getResolvedRevisions(), new ProjectProfileResponse.Completeness(p.getCompletenessStatus(),
                p.isCollectionComplete(), p.isTruncated(), p.getWarningCount(), p.getErrorCount(),
                p.getSuccessfulCollectorCount(), p.getCollectorsWithWarningsCount(), p.getFailedCollectorCount()),
                p.getSections(), p.getDeterministicSummary(), p.getSourceObservations(), p.getCharacteristicCount());
    }

    private record Mapping(ProfileCategory category, String code, String label, String description) { }
    private static final class MutableCharacteristic {
        private final Mapping mapping; private final SortedSet<UUID> observations = new TreeSet<>();
        private final Set<String> evidence = new TreeSet<>();
        private MutableCharacteristic(Mapping mapping) { this.mapping = mapping; }
        private void add(Observation observation) {
            observations.add(observation.getId());
            observation.getSupportingFacts().stream().map(Fact::getEvidenceReferences).forEach(evidence::addAll);
        }
        private ProfileCharacteristic freeze() {
            return new ProfileCharacteristic(mapping.code(), mapping.label(), mapping.description(),
                    CharacteristicStatus.CONFIRMED, List.copyOf(observations), evidence.size(), Map.of());
        }
    }
}
