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

    private static final Set<String> SOURCE_ROOTS = Set.of(
            "src/main/java", "src/main/kotlin", "src/main/python",
            "src/main/typescript", "src/app", "src/lib");

    private static final Set<String> TEST_ROOTS = Set.of(
            "src/test/", "__tests__/", "test/", "tests/");

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
        return "v1";
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
                    .filter(path -> path.startsWith(root + "/") || path.equals(root))
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
                    .filter(path -> path.contains(root))
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
        for (String root : SOURCE_ROOTS) {
            if (relativePath.startsWith(root + "/") || relativePath.equals(root)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTestRoot(String relativePath) {
        for (String root : TEST_ROOTS) {
            if (relativePath.contains(root)) {
                return true;
            }
        }
        return false;
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
        List<RepositoryEvidence> fileEvidence = new ArrayList<>();

        for (RepositoryFile file : scan.files()) {
            String path = file.relativePath();

            if (isSourceFile(path)) {
                fileEvidence.add(evidenceFactory.create(
                        metadata(),
                        new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.RELATED_SOURCE_CODE,
                        "SOURCE_FILE",
                        "file:" + path,
                        path,
                        Instant.now(),
                        List.of(),
                        sourceId,
                        path,
                        "repository-structure:source-file:" + path),
                        request.budget().maximumSummaryCharacters()
                ));
            } else if (isTestFile(path)) {
                fileEvidence.add(evidenceFactory.create(
                        metadata(),
                        new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.RELATED_SOURCE_CODE,
                        "TEST_FILE",
                        "file:" + path,
                        path,
                        Instant.now(),
                        List.of(),
                        sourceId,
                        path,
                        "repository-structure:test-file:" + path),
                        request.budget().maximumSummaryCharacters()
                ));
            } else if (isConfigFile(path)) {
                fileEvidence.add(evidenceFactory.create(
                        metadata(),
                        new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.RELATED_SOURCE_CODE,
                        "CONFIG_FILE",
                        "config:" + path,
                        path,
                        Instant.now(),
                        List.of(),
                        sourceId,
                        path,
                        "repository-structure:config-file:" + path),
                        request.budget().maximumSummaryCharacters()
                ));
            }
        }

        // Story-term prioritization
        if (!storyTerms.isEmpty()) {
            fileEvidence.sort(Comparator
                    .<RepositoryEvidence, Integer>comparing(e -> {
                        String pathLower = e.provenance().originatingFile().toLowerCase();
                        return (int) storyTerms.stream()
                                .filter(pathLower::contains)
                                .count();
                    }).reversed()
                    .thenComparing(e -> e.provenance().originatingFile()));
        } else {
            fileEvidence.sort(Comparator.comparing(e -> e.provenance().originatingFile()));
        }

        return fileEvidence.stream().limit(MAX_FILE_EVIDENCE_ITEMS).toList();
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
}
