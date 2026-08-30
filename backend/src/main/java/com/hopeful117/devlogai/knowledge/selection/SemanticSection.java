package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SemanticSection {

    private SemanticSection() {
    }

    public enum SectionId {
        PROJECT_STATE,
        ARCHITECTURE,
        DECISIONS,
        VALIDATED_KNOWLEDGE,
        HISTORY,
        REPOSITORY_CHANGES,
        HUMAN_CONTEXT
    }

    public record PromptSemanticSection(
            String sectionId,
            String sectionTitle,
            List<PromptSemanticSectionItem> items
    ) {
    }

    public record PromptSemanticSectionItem(
            String itemType,
            String itemId,
            String label
    ) {
    }

    private static final Map<FactType, Set<SectionId>> FACT_TYPE_MAP = buildFactTypeMap();
    private static final Map<ObservationType, Set<SectionId>> OBSERVATION_TYPE_MAP = buildObservationTypeMap();
    private static final Map<InsightType, Set<SectionId>> INSIGHT_TYPE_MAP = buildInsightTypeMap();
    private static final Map<RepositoryContextLayer, Set<SectionId>> REPOSITORY_LAYER_MAP = buildRepositoryLayerMap();
    private static final Map<ProjectHumanContextInputType, Set<SectionId>> HUMAN_CONTEXT_TYPE_MAP = buildHumanContextTypeMap();

    public static Set<SectionId> classifyFact(FactType factType) {
        return FACT_TYPE_MAP.getOrDefault(factType, EnumSet.noneOf(SectionId.class));
    }

    public static Set<SectionId> classifyObservation(ObservationType observationType) {
        return OBSERVATION_TYPE_MAP.getOrDefault(observationType, EnumSet.noneOf(SectionId.class));
    }

    public static Set<SectionId> classifyInsight(InsightType insightType) {
        return INSIGHT_TYPE_MAP.getOrDefault(insightType, EnumSet.noneOf(SectionId.class));
    }

    public static Set<SectionId> classifyRepositoryLayer(RepositoryContextLayer layer) {
        return REPOSITORY_LAYER_MAP.getOrDefault(layer, EnumSet.noneOf(SectionId.class));
    }

    public static Set<SectionId> classifyHumanContext(ProjectHumanContextInputType type) {
        return HUMAN_CONTEXT_TYPE_MAP.getOrDefault(type, EnumSet.noneOf(SectionId.class));
    }

    private static Map<FactType, Set<SectionId>> buildFactTypeMap() {
        Map<FactType, Set<SectionId>> map = new EnumMap<>(FactType.class);
        putFact(map, FactType.COMMIT, SectionId.HISTORY);
        putFact(map, FactType.COMMIT_DIFF_SUMMARY, SectionId.HISTORY);
        putFact(map, FactType.COMMIT_CHANGES_MODULE, SectionId.HISTORY, SectionId.ARCHITECTURE);
        putFact(map, FactType.COMMIT_ADDS_FEATURE, SectionId.HISTORY);
        putFact(map, FactType.COMMIT_FIXES_BUG, SectionId.HISTORY);
        putFact(map, FactType.COMMIT_REFACTORS_CODE, SectionId.HISTORY, SectionId.ARCHITECTURE);
        putFact(map, FactType.FILE_CHANGE, SectionId.HISTORY);
        putFact(map, FactType.DEPENDENCY_CHANGE, SectionId.HISTORY, SectionId.ARCHITECTURE);
        putFact(map, FactType.CODE_METRIC, SectionId.REPOSITORY_CHANGES);
        putFact(map, FactType.DOCUMENTATION_CHANGE, SectionId.PROJECT_STATE, SectionId.HISTORY);
        putFact(map, FactType.TECHNOLOGY, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putFact(map, FactType.REPOSITORY_REVISION_RESOLVED, SectionId.REPOSITORY_CHANGES);
        putFact(map, FactType.REPOSITORY_STRUCTURE_SUMMARY, SectionId.PROJECT_STATE, SectionId.ARCHITECTURE);
        putFact(map, FactType.SOURCE_DIRECTORY_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.PRIMARY_FILE_EXTENSION, SectionId.PROJECT_STATE);
        putFact(map, FactType.MULTI_MODULE_STRUCTURE_PRESENT, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putFact(map, FactType.CONFIGURATION_FILE_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.BUILD_SYSTEM_DETECTED, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putFact(map, FactType.BUILD_WRAPPER_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.JAVA_VERSION_DECLARED, SectionId.PROJECT_STATE, SectionId.ARCHITECTURE);
        putFact(map, FactType.PROJECT_VERSION_DECLARED, SectionId.PROJECT_STATE);
        putFact(map, FactType.BUILD_MODULE_DECLARED, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putFact(map, FactType.DEPENDENCY_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.BUILD_PLUGIN_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_BOOT_DETECTED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_BOOT_VERSION_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_CLOUD_DETECTED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_SECURITY_DETECTED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_DATA_DETECTED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_WEB_DETECTED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_ACTUATOR_DETECTED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_CONFIGURATION_FILE_PRESENT, SectionId.ARCHITECTURE);
        putFact(map, FactType.REST_CONTROLLER_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.SPRING_CONFIGURATION_CLASS_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.DOCKERFILE_PRESENT, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putFact(map, FactType.DOCKER_COMPOSE_PRESENT, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putFact(map, FactType.DOCKER_SERVICE_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.DOCKER_MULTI_STAGE_BUILD_PRESENT, SectionId.ARCHITECTURE);
        putFact(map, FactType.DOCKER_NON_ROOT_USER_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.DOCKER_HEALTHCHECK_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.DOCKER_EXPOSED_PORT_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.DOCKER_VOLUME_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.DOCKERIGNORE_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.README_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.DOCUMENTATION_DIRECTORY_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.MARKDOWN_DOCUMENT_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.ADR_DIRECTORY_PRESENT, SectionId.PROJECT_STATE, SectionId.DECISIONS);
        putFact(map, FactType.ADR_DOCUMENT_PRESENT, SectionId.DECISIONS, SectionId.PROJECT_STATE);
        putFact(map, FactType.API_DOCUMENTATION_PRESENT, SectionId.PROJECT_STATE, SectionId.ARCHITECTURE);
        putFact(map, FactType.ARCHITECTURE_DOCUMENTATION_PRESENT, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putFact(map, FactType.CONTRIBUTING_GUIDE_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.CHANGELOG_PRESENT, SectionId.PROJECT_STATE, SectionId.HISTORY);
        putFact(map, FactType.TEST_SOURCE_DIRECTORY_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.TEST_FILE_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.TEST_FRAMEWORK_DECLARED, SectionId.PROJECT_STATE, SectionId.ARCHITECTURE);
        putFact(map, FactType.INTEGRATION_TEST_FILE_PRESENT, SectionId.PROJECT_STATE);
        putFact(map, FactType.TESTCONTAINERS_DECLARED, SectionId.ARCHITECTURE);
        putFact(map, FactType.TEST_RESOURCE_DIRECTORY_PRESENT, SectionId.PROJECT_STATE);
        return map;
    }

    private static Map<ObservationType, Set<SectionId>> buildObservationTypeMap() {
        Map<ObservationType, Set<SectionId>> map = new EnumMap<>(ObservationType.class);
        putObservation(map, ObservationType.ASYNCHRONOUS_COMMUNICATION, SectionId.ARCHITECTURE);
        putObservation(map, ObservationType.HTTP_SERVICE_COMMUNICATION, SectionId.ARCHITECTURE);
        putObservation(map, ObservationType.ARCHITECTURE_MODULARIZATION, SectionId.ARCHITECTURE);
        putObservation(map, ObservationType.AUTHENTICATION_LAYER, SectionId.ARCHITECTURE);
        putObservation(map, ObservationType.TEST_COVERAGE_DECREASE, SectionId.PROJECT_STATE);
        putObservation(map, ObservationType.CONTAINERIZED_PROJECT, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putObservation(map, ObservationType.SPRING_BOOT_REST_APPLICATION, SectionId.ARCHITECTURE);
        putObservation(map, ObservationType.ARCHITECTURE_DOCUMENTATION_PRESENT, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        putObservation(map, ObservationType.AUTOMATED_TEST_SUITE_PRESENT, SectionId.PROJECT_STATE);
        putObservation(map, ObservationType.INTEGRATION_TEST_SUITE_PRESENT, SectionId.PROJECT_STATE);
        putObservation(map, ObservationType.MULTI_MODULE_BUILD, SectionId.ARCHITECTURE, SectionId.PROJECT_STATE);
        return map;
    }

    private static Map<InsightType, Set<SectionId>> buildInsightTypeMap() {
        Map<InsightType, Set<SectionId>> map = new EnumMap<>(InsightType.class);
        putInsight(map, InsightType.ARCHITECTURAL, SectionId.VALIDATED_KNOWLEDGE, SectionId.ARCHITECTURE);
        putInsight(map, InsightType.DOCUMENTATION, SectionId.VALIDATED_KNOWLEDGE, SectionId.PROJECT_STATE);
        putInsight(map, InsightType.TECHNOLOGY, SectionId.VALIDATED_KNOWLEDGE, SectionId.ARCHITECTURE);
        putInsight(map, InsightType.EVOLUTION, SectionId.VALIDATED_KNOWLEDGE, SectionId.HISTORY);
        putInsight(map, InsightType.TECHNICAL_DEBT, SectionId.VALIDATED_KNOWLEDGE, SectionId.ARCHITECTURE);
        putInsight(map, InsightType.SECURITY, SectionId.VALIDATED_KNOWLEDGE, SectionId.ARCHITECTURE);
        putInsight(map, InsightType.RISK, SectionId.VALIDATED_KNOWLEDGE);
        putInsight(map, InsightType.RECOMMENDATION, SectionId.VALIDATED_KNOWLEDGE);
        return map;
    }

    private static Map<RepositoryContextLayer, Set<SectionId>> buildRepositoryLayerMap() {
        Map<RepositoryContextLayer, Set<SectionId>> map = new EnumMap<>(RepositoryContextLayer.class);
        putRepositoryLayer(map, RepositoryContextLayer.COMMIT_DIFF, SectionId.REPOSITORY_CHANGES, SectionId.HISTORY);
        putRepositoryLayer(map, RepositoryContextLayer.GIT_HISTORY, SectionId.REPOSITORY_CHANGES, SectionId.HISTORY);
        putRepositoryLayer(map, RepositoryContextLayer.VALIDATED_INSIGHT, SectionId.REPOSITORY_CHANGES, SectionId.VALIDATED_KNOWLEDGE);
        putRepositoryLayer(map, RepositoryContextLayer.PREVIOUS_ANALYSIS, SectionId.REPOSITORY_CHANGES, SectionId.HISTORY);
        putRepositoryLayer(map, RepositoryContextLayer.ROADMAP, SectionId.REPOSITORY_CHANGES);
        putRepositoryLayer(map, RepositoryContextLayer.ADR, SectionId.REPOSITORY_CHANGES, SectionId.DECISIONS);
        putRepositoryLayer(map, RepositoryContextLayer.CURRENT_ANALYSIS, SectionId.REPOSITORY_CHANGES);
        putRepositoryLayer(map, RepositoryContextLayer.RELATED_SOURCE_CODE, SectionId.REPOSITORY_CHANGES, SectionId.ARCHITECTURE);
        putRepositoryLayer(map, RepositoryContextLayer.PROJECT_DOCUMENTATION, SectionId.REPOSITORY_CHANGES, SectionId.PROJECT_STATE);
        return map;
    }

    private static Map<ProjectHumanContextInputType, Set<SectionId>> buildHumanContextTypeMap() {
        Map<ProjectHumanContextInputType, Set<SectionId>> map = new EnumMap<>(ProjectHumanContextInputType.class);
        putHumanContext(map, ProjectHumanContextInputType.GOAL, SectionId.HUMAN_CONTEXT, SectionId.PROJECT_STATE);
        putHumanContext(map, ProjectHumanContextInputType.CONSTRAINT, SectionId.HUMAN_CONTEXT, SectionId.ARCHITECTURE);
        putHumanContext(map, ProjectHumanContextInputType.ASSUMPTION, SectionId.HUMAN_CONTEXT, SectionId.PROJECT_STATE);
        putHumanContext(map, ProjectHumanContextInputType.KNOWN_GAP, SectionId.HUMAN_CONTEXT);
        putHumanContext(map, ProjectHumanContextInputType.DOMAIN_CONTEXT, SectionId.HUMAN_CONTEXT, SectionId.ARCHITECTURE);
        return map;
    }

    private static void putFact(Map<FactType, Set<SectionId>> map, FactType type, SectionId... sections) {
        map.put(type, EnumSet.copyOf(Set.of(sections)));
    }

    private static void putObservation(Map<ObservationType, Set<SectionId>> map, ObservationType type, SectionId... sections) {
        map.put(type, EnumSet.copyOf(Set.of(sections)));
    }

    private static void putInsight(Map<InsightType, Set<SectionId>> map, InsightType type, SectionId... sections) {
        map.put(type, EnumSet.copyOf(Set.of(sections)));
    }

    private static void putRepositoryLayer(Map<RepositoryContextLayer, Set<SectionId>> map, RepositoryContextLayer type, SectionId... sections) {
        map.put(type, EnumSet.copyOf(Set.of(sections)));
    }

    private static void putHumanContext(Map<ProjectHumanContextInputType, Set<SectionId>> map, ProjectHumanContextInputType type, SectionId... sections) {
        map.put(type, EnumSet.copyOf(Set.of(sections)));
    }
}
