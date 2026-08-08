package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.docker",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class DockerCollector extends AbstractFileCollector {
    private static final String VERSION = "docker-v1";
    private static final String PATH_METADATA_PREFIX = "path=";

    public DockerCollector(SecureRepositoryScanner scanner, CollectorLimits limits) {
        super(scanner, limits);
    }
    @Override public CollectorType type() { return CollectorType.DOCKER; }
    @Override public String version() { return VERSION; }

    @Override
    public CollectionResult collect(CollectionContext context) {
        RepositoryScan scan = scan(context, this::dockerFile);
        FactAccumulator facts = accumulator(context, scan);
        for (RepositoryFile file : scan.files()) {
            if (file.content() == null) continue;
            String name = fileName(file.relativePath()).toLowerCase(Locale.ROOT);
            if (name.equals(".dockerignore")) {
                facts.add(FactType.DOCKERIGNORE_PRESENT,
                        PATH_METADATA_PREFIX + file.relativePath(), file.relativePath());
            } else if (name.equals("dockerfile") || name.startsWith("dockerfile.")) {
                parseDockerfile(file, facts);
            } else {
                parseCompose(file, facts);
            }
        }
        return result(facts);
    }

    private boolean dockerFile(String path) {
        String name = fileName(path).toLowerCase(Locale.ROOT);
        return name.equals("dockerfile") || name.startsWith("dockerfile.")
                || List.of("docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml",
                ".dockerignore").contains(name);
    }

    private void parseDockerfile(RepositoryFile file, FactAccumulator facts) {
        facts.add(FactType.DOCKERFILE_PRESENT,
                PATH_METADATA_PREFIX + file.relativePath(), file.relativePath());
        int fromCount = 0;
        for (String line : file.content().lines().toList()) {
            String trimmed = line.trim();
            int separator = trimmed.indexOf(' ');
            if (separator <= 0) continue;
            String instruction = trimmed.substring(0, separator).toUpperCase(Locale.ROOT);
            String argument = trimmed.substring(separator + 1).trim();
            if (instruction.equals("FROM")) fromCount++;
            if (instruction.equals("USER") && !argument.equals("0") && !argument.equalsIgnoreCase("root")) {
                facts.add(FactType.DOCKER_NON_ROOT_USER_DECLARED, "user=" + safeToken(argument), file.relativePath());
            }
            if (instruction.equals("HEALTHCHECK")) {
                facts.add(FactType.DOCKER_HEALTHCHECK_DECLARED,
                        PATH_METADATA_PREFIX + file.relativePath(), file.relativePath());
            }
            if (instruction.equals("EXPOSE")) {
                facts.add(FactType.DOCKER_EXPOSED_PORT_DECLARED,
                        "exposedPorts=" + safeToken(argument), file.relativePath());
            }
        }
        if (fromCount > 1) facts.add(FactType.DOCKER_MULTI_STAGE_BUILD_PRESENT,
                "stageCount=" + fromCount, file.relativePath());
    }

    private void parseCompose(RepositoryFile file, FactAccumulator facts) {
        facts.add(FactType.DOCKER_COMPOSE_PRESENT,
                PATH_METADATA_PREFIX + file.relativePath(), file.relativePath());
        ComposeSection section = ComposeSection.NONE;
        for (String line : file.content().lines().toList()) {
            String content = withoutComment(line).stripTrailing();
            if (isTopLevel(content)) section = topLevelSection(content);
            String entry = composeEntry(content);
            if (entry != null) addComposeEntry(file, facts, section, entry);
            if (content.stripLeading().equals("healthcheck:")) facts.add(
                    FactType.DOCKER_HEALTHCHECK_DECLARED,
                    "composeHealthcheck=true", file.relativePath());
        }
    }

    private boolean isTopLevel(String line) {
        return !line.isBlank() && !Character.isWhitespace(line.charAt(0));
    }

    private ComposeSection topLevelSection(String line) {
        if (line.equals("services:")) return ComposeSection.SERVICES;
        if (line.equals("volumes:")) return ComposeSection.VOLUMES;
        return ComposeSection.NONE;
    }

    private String composeEntry(String line) {
        if (!line.startsWith("  ") || line.startsWith("   ")) return null;
        String candidate = line.substring(2);
        if (!candidate.endsWith(":")) return null;
        String name = candidate.substring(0, candidate.length() - 1);
        return name.chars().allMatch(this::isComposeNameCharacter) ? name : null;
    }

    private boolean isComposeNameCharacter(int value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '.' || value == '-';
    }

    private void addComposeEntry(RepositoryFile file, FactAccumulator facts,
            ComposeSection section, String entry) {
        if (section == ComposeSection.SERVICES) {
            facts.add(FactType.DOCKER_SERVICE_DECLARED,
                    "service=" + entry, file.relativePath());
        } else if (section == ComposeSection.VOLUMES) {
            facts.add(FactType.DOCKER_VOLUME_DECLARED,
                    "volume=" + entry, file.relativePath());
        }
    }

    private String safeToken(String value) {
        return withoutComment(value).trim();
    }

    private String withoutComment(String value) {
        int comment = value.indexOf('#');
        return comment < 0 ? value : value.substring(0, comment);
    }

    private enum ComposeSection { NONE, SERVICES, VOLUMES }
}
