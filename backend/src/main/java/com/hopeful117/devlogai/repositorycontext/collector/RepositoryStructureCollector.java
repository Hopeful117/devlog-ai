package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.collection.collector.CollectionContext;
import com.hopeful117.devlogai.collection.collector.CollectorLimits;
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

    private final SecureRepositoryScanner scanner;
    private final CollectorLimits limits;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final EvidenceFactory evidenceFactory;

    public RepositoryStructureCollector(
            SecureRepositoryScanner scanner,
            CollectorLimits limits,
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager,
            EvidenceFactory evidenceFactory
    ) {
        this.scanner = scanner;
        this.limits = limits;
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
            evidence.add(moduleSummaryEvidence(scan, source.getId().toString(), request));
            evidence.addAll(sourceDirectoryEvidence(scan, source.getId().toString(), request));
            evidence.addAll(testDirectoryEvidence(scan, source.getId().toString(), request));
            evidence.addAll(configurationFileEvidence(scan, source.getId().toString(), request));
            evidence.addAll(fileExtensionEvidence(scan, source.getId().toString(), request));

            // Return up to 5 evidence items
            return List.copyOf(evidence.stream().limit(5).toList());
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
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "MODULE_SUMMARY",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:module-summary",
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
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "SOURCE_DIRECTORIES",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:source-directories",
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
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "TEST_DIRECTORIES",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:test-directories",
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
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "CONFIGURATION_FILES",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:config-files",
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
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "FILE_EXTENSIONS",
                reference,
                summary,
                Instant.now(),
                List.of(),
                sourceId,
                null,
                "repository-structure:file-extensions",
                request.budget().maximumSummaryCharacters()
        ));
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "REPOSITORY_STRUCTURE");
    }
}
