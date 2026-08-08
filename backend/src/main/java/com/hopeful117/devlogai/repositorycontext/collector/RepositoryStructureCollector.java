package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.collection.collector.CollectionContext;
import com.hopeful117.devlogai.collection.collector.RepositoryFile;
import com.hopeful117.devlogai.collection.collector.RepositoryScan;
import com.hopeful117.devlogai.collection.collector.SecureRepositoryScanner;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scans the project filesystem and produces {@code RELATED_SOURCE_CODE} evidence
 * about repository file structure: modules, source/test directories, configuration
 * files, and file extension distribution.
 */
@Component
@Order(40)
public class RepositoryStructureCollector implements RepositoryContextCollector {

    private static final Logger log = LoggerFactory.getLogger(RepositoryStructureCollector.class);

    private static final Set<String> MODULE_BUILD_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts");

    private static final List<String> SOURCE_ROOTS = List.of(
            "src/main/java", "src/main/kotlin", "src/main/python",
            "src/main/typescript", "src/app", "src/lib");

    private static final List<String> TEST_ROOTS = List.of(
            "src/test", "__tests__", "test", "tests");

    private static final Set<String> CONFIGURATION_FILE_NAMES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "application.properties", "application.yml", "application.yaml",
            "package.json", "tsconfig.json", "pyproject.toml",
            "requirements.txt", "Dockerfile", "docker-compose.yml",
            ".gitignore");

    private static final int MAX_FILE_EVIDENCE_ITEMS = 40;

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".ts", ".tsx", ".js", ".jsx");

    private final SecureRepositoryScanner scanner;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final EvidenceFactory evidenceFactory;

    public RepositoryStructureCollector(
            SecureRepositoryScanner scanner,
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager,
            EvidenceFactory evidenceFactory
    ) {
        this.scanner = scanner;
        this.sourceRepository = sourceRepository;
        this.workspaceManager = workspaceManager;
        this.evidenceFactory = evidenceFactory;
    }

    @Override
    public String collectorId() {
        return "repository-structure";
    }

    @Override
    public String collectorVersion() {
        return "v2";
    }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        UUID projectId = request.analysisContext().project().id();
        try {
            List<Source> sources = sourceRepository
                    .findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId);
            if (sources.isEmpty()) {
                return List.of();
            }
            Source source = sources.getFirst();
            SynchronizedWorkspace workspace = workspaceManager.synchronize(source, null);

            CollectionContext collectionContext = new CollectionContext(
                    request.analysisContext().analysis().id(),
                    source.getId(),
                    projectId,
                    workspace.path(),
                    workspace.resolvedRevision(),
                    SourceType.GIT_REPOSITORY,
                    Instant.now()
            );

            RepositoryScan scan = scanner.scan(collectionContext, path -> false);

            List<RepositoryEvidence> evidence = new ArrayList<>();
            // Aggregate evidence (existing)
            evidence.add(moduleSummaryEvidence(scan, source.getId().toString(), request));
            evidence.addAll(sourceDirectoryEvidence(scan, source.getId().toString(), request));
            evidence.addAll(testDirectoryEvidence(scan, source.getId().toString(), request));
            evidence.addAll(configurationFileEvidence(scan, source.getId().toString(), request));
            evidence.addAll(fileExtensionEvidence(scan, source.getId().toString(), request));

            // File-level evidence (new)
            evidence.addAll(produceModuleEvidence(scan, source.getId().toString(), request));
            evidence.addAll(produceFileLevelEvidence(scan, source.getId().toString(), request));

            return List.copyOf(evidence);
        } catch (Exception e) {
            log.warn("Repository structure collection failed for project {}: {}",
                    projectId, e.getMessage());
            return List.of();
        }
    }

    private RepositoryEvidence moduleSummaryEvidence(
            RepositoryScan scan,
            String sourceId,
            ContextRequest request
    ) {
        long moduleCount = scan.files().stream()
                .map(RepositoryFile::relativePath)
                .filter(path -> {
                    String fileName = path.contains("/")
                            ? path.substring(path.lastIndexOf('/') + 1)
                            : path;
                    return MODULE_BUILD_FILES.contains(fileName);
                })
                .filter(path -> path.contains("/"))
                .count();

        String reference = "module:summary";
        String summary = moduleCount > 1
                ? "Multi-module repository with " + moduleCount + " modules"
                : "Single-module repository";

        return evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "MODULE_SUMMARY",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:module-summary"),
                request.budget().maximumSummaryCharacters()
        );
    }

    private List<RepositoryEvidence> sourceDirectoryEvidence(
            RepositoryScan scan,
            String sourceId,
            ContextRequest request
    ) {
        Map<String, Integer> sourceDirCounts = new LinkedHashMap<>();
        for (String root : SOURCE_ROOTS) {
            long count = scan.files().stream()
                    .map(RepositoryFile::relativePath)
                    .filter(path -> containsPathRoot(path, root))
                    .count();
            if (count > 0) {
                sourceDirCounts.put(root, (int) count);
            }
        }

        if (sourceDirCounts.isEmpty()) {
            return List.of();
        }

        String reference = "source:directories";
        String summary = "Source directories: "
                + String.join(", ", sourceDirCounts.keySet());

        return List.of(evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "SOURCE_DIRECTORIES",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:source-directories"),
                request.budget().maximumSummaryCharacters()
        ));
    }

    private List<RepositoryEvidence> testDirectoryEvidence(
            RepositoryScan scan,
            String sourceId,
            ContextRequest request
    ) {
        Map<String, Integer> testDirCounts = new LinkedHashMap<>();
        for (String root : TEST_ROOTS) {
            long count = scan.files().stream()
                    .map(RepositoryFile::relativePath)
                    .filter(path -> containsPathRoot(path, root))
                    .count();
            if (count > 0) {
                testDirCounts.put(root, (int) count);
            }
        }

        if (testDirCounts.isEmpty()) {
            return List.of();
        }

        String reference = "test:directories";
        String summary = "Test directories: "
                + String.join(", ", testDirCounts.keySet());

        return List.of(evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "TEST_DIRECTORIES",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:test-directories"),
                request.budget().maximumSummaryCharacters()
        ));
    }

    private List<RepositoryEvidence> configurationFileEvidence(
            RepositoryScan scan,
            String sourceId,
            ContextRequest request
    ) {
        List<String> foundConfigs = scan.files().stream()
                .map(RepositoryFile::relativePath)
                .filter(path -> {
                    String fileName = path.contains("/")
                            ? path.substring(path.lastIndexOf('/') + 1)
                            : path;
                    return CONFIGURATION_FILE_NAMES.contains(fileName);
                })
                .sorted()
                .toList();

        if (foundConfigs.isEmpty()) {
            return List.of();
        }

        String reference = "config:files";
        String summary = "Configuration files: "
                + String.join(", ", foundConfigs);

        return List.of(evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "CONFIGURATION_FILES",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:config-files"),
                request.budget().maximumSummaryCharacters()
        ));
    }

    private List<RepositoryEvidence> fileExtensionEvidence(
            RepositoryScan scan,
            String sourceId,
            ContextRequest request
    ) {
        Map<String, Integer> extensionCounts = new LinkedHashMap<>();
        for (RepositoryFile file : scan.files()) {
            String path = file.relativePath();
            int lastDot = path.lastIndexOf('.');
            if (lastDot > 0 && lastDot < path.length() - 1) {
                String ext = path.substring(lastDot + 1).toLowerCase();
                extensionCounts.merge(ext, 1, Integer::sum);
            }
        }

        if (extensionCounts.isEmpty()) {
            return List.of();
        }

        // Sort descending by count, take top 10
        List<String> topExtensions = extensionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();

        String reference = "extensions:distribution";
        String summary = "File extensions: " + String.join(", ", topExtensions);

        return List.of(evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "FILE_EXTENSIONS",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:file-extensions"),
                request.budget().maximumSummaryCharacters()
        ));
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "REPOSITORY_STRUCTURE");
    }

    // --- File classification helpers ---

    private static boolean isSourceFile(String relativePath) {
        return containsSourceRoot(relativePath) && hasSourceExtension(relativePath);
    }

    private static boolean isTestFile(String relativePath) {
        return containsTestRoot(relativePath) && hasSourceExtension(relativePath);
    }

    private static boolean isConfigFile(String relativePath) {
        String fileName = relativePath.contains("/")
                ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                : relativePath;
        return CONFIGURATION_FILE_NAMES.contains(fileName);
    }

    private static String extractModuleName(String relativePath) {
        // If path has no directory separators, it's at the root
        if (!relativePath.contains("/")) {
            return "root";
        }
        String firstSegment = relativePath.substring(0, relativePath.indexOf('/'));
        // If the first segment is a known source root, this is a single-module repo
        if (SOURCE_ROOTS.contains(firstSegment) || TEST_ROOTS.stream().anyMatch(firstSegment::startsWith)) {
            return "root";
        }
        // If the first segment is a build file name, it's root-level
        if (MODULE_BUILD_FILES.contains(firstSegment)) {
            return "root";
        }
        return firstSegment;
    }

    private static Set<String> extractStoryTerms(com.hopeful117.devlogai.repositorycontext.ContextRequest request) {
        String objective = request.intent().objective();
        if (objective == null || objective.isBlank()) {
            return Set.of();
        }
        // Split by whitespace and non-alphanumeric characters, normalize to lowercase
        String[] rawTerms = objective.toLowerCase().split("[^a-z0-9]+");
        Set<String> terms = new java.util.LinkedHashSet<>();
        for (String term : rawTerms) {
            if (term.length() >= 3) {
                terms.add(term);
            }
        }
        return terms;
    }

    private static boolean containsSourceRoot(String relativePath) {
        return SOURCE_ROOTS.stream().anyMatch(root -> containsPathRoot(relativePath, root));
    }

    private static boolean containsTestRoot(String relativePath) {
        return TEST_ROOTS.stream().anyMatch(root -> containsPathRoot(relativePath, root));
    }

    private static boolean containsPathRoot(String relativePath, String root) {
        return relativePath.equals(root)
                || relativePath.startsWith(root + "/")
                || relativePath.contains("/" + root + "/")
                || relativePath.endsWith("/" + root);
    }

    private static boolean hasSourceExtension(String relativePath) {
        for (String ext : SOURCE_EXTENSIONS) {
            if (relativePath.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    // --- File-level evidence production ---

    private List<RepositoryEvidence> produceFileLevelEvidence(
            RepositoryScan scan,
            String sourceId,
            com.hopeful117.devlogai.repositorycontext.ContextRequest request
    ) {
        Set<String> storyTerms = extractStoryTerms(request);
        Map<FileEvidenceKind, List<RepositoryEvidence>> candidates =
                collectFileCandidates(scan, sourceId, request);
        sortFileCandidates(candidates, storyTerms);
        return allocateFileCandidates(candidates);
    }

    private Map<FileEvidenceKind, List<RepositoryEvidence>> collectFileCandidates(
            RepositoryScan scan,
            String sourceId,
            ContextRequest request
    ) {
        Map<FileEvidenceKind, List<RepositoryEvidence>> candidates =
                new EnumMap<>(FileEvidenceKind.class);
        for (FileEvidenceKind kind : FileEvidenceKind.values()) {
            candidates.put(kind, new ArrayList<>());
        }

        for (RepositoryFile file : scan.files()) {
            String path = file.relativePath();
            FileEvidenceKind kind = classify(path);
            if (kind != null) candidates.get(kind).add(fileEvidence(
                    kind, path, sourceId, request));
        }
        return candidates;
    }

    private void sortFileCandidates(
            Map<FileEvidenceKind, List<RepositoryEvidence>> candidates,
            Set<String> storyTerms
    ) {
        Comparator<RepositoryEvidence> priority = Comparator
                .<RepositoryEvidence, Integer>comparing(value -> storyTermMatches(
                        value.provenance().originatingFile(), storyTerms))
                .reversed()
                .thenComparing(value -> value.provenance().originatingFile());
        candidates.values().forEach(values -> values.sort(priority));
    }

    private List<RepositoryEvidence> allocateFileCandidates(
            Map<FileEvidenceKind, List<RepositoryEvidence>> candidates
    ) {
        List<RepositoryEvidence> allocated = new ArrayList<>();
        for (int index = 0; allocated.size() < MAX_FILE_EVIDENCE_ITEMS; index++) {
            boolean added = false;
            for (FileEvidenceKind kind : FileEvidenceKind.values()) {
                List<RepositoryEvidence> values = candidates.get(kind);
                if (index < values.size()) {
                    allocated.add(values.get(index));
                    added = true;
                    if (allocated.size() == MAX_FILE_EVIDENCE_ITEMS) break;
                }
            }
            if (!added) break;
        }
        return List.copyOf(allocated);
    }

    private FileEvidenceKind classify(String path) {
        if (isTestFile(path)) return FileEvidenceKind.TEST;
        if (isSourceFile(path)) return FileEvidenceKind.SOURCE;
        if (isConfigFile(path)) return FileEvidenceKind.CONFIGURATION;
        return null;
    }

    private RepositoryEvidence fileEvidence(
            FileEvidenceKind kind,
            String path,
            String sourceId,
            ContextRequest request
    ) {
        return evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.RELATED_SOURCE_CODE,
                        kind.evidenceKind,
                        kind.referencePrefix + path,
                        path,
                        Instant.now(),
                        List.of(),
                        sourceId,
                        path,
                        "repository-structure:" + kind.identifierSegment + ":" + path),
                request.budget().maximumSummaryCharacters());
    }

    private int storyTermMatches(String path, Set<String> storyTerms) {
        String normalized = path.toLowerCase();
        return (int) storyTerms.stream().filter(normalized::contains).count();
    }

    private List<RepositoryEvidence> produceModuleEvidence(
            RepositoryScan scan,
            String sourceId,
            com.hopeful117.devlogai.repositorycontext.ContextRequest request
    ) {
        Map<String, Long> modules = new LinkedHashMap<>();
        for (RepositoryFile file : scan.files()) {
            String moduleName = extractModuleName(file.relativePath());
            modules.merge(moduleName, 1L, Long::sum);
        }

        List<RepositoryEvidence> moduleEvidence = new ArrayList<>();
        for (Map.Entry<String, Long> entry : modules.entrySet()) {
            String moduleName = entry.getKey();
            long fileCount = entry.getValue();
            moduleEvidence.add(evidenceFactory.create(
                    metadata(),
                    new EvidenceFactory.EvidenceInput(
                    RepositoryContextLayer.RELATED_SOURCE_CODE,
                    "MODULE",
                    "module:" + moduleName,
                    "Module: " + moduleName + " \u2014 " + fileCount + " files",
                    Instant.now(),
                    List.of(),
                    sourceId,
                    null,
                    "repository-structure:module:" + moduleName),
                    request.budget().maximumSummaryCharacters()
            ));
        }

        return moduleEvidence;
    }

    private enum FileEvidenceKind {
        SOURCE("SOURCE_FILE", "file:", "source-file"),
        TEST("TEST_FILE", "file:", "test-file"),
        CONFIGURATION("CONFIG_FILE", "config:", "config-file");

        private final String evidenceKind;
        private final String referencePrefix;
        private final String identifierSegment;

        FileEvidenceKind(
                String evidenceKind,
                String referencePrefix,
                String identifierSegment
        ) {
            this.evidenceKind = evidenceKind;
            this.referencePrefix = referencePrefix;
            this.identifierSegment = identifierSegment;
        }
    }
}
