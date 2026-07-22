package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.docker",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class DockerCollector extends AbstractFileCollector {
    private static final String VERSION = "docker-v1";
    private static final Pattern INSTRUCTION = Pattern.compile("(?im)^\\s*([A-Z]+)\\s+(.+)$");

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
                facts.add(FactType.DOCKERIGNORE_PRESENT, "path=" + file.relativePath(), file.relativePath());
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
        facts.add(FactType.DOCKERFILE_PRESENT, "path=" + file.relativePath(), file.relativePath());
        int fromCount = 0;
        Matcher matcher = INSTRUCTION.matcher(file.content());
        while (matcher.find()) {
            String instruction = matcher.group(1).toUpperCase(Locale.ROOT);
            String argument = matcher.group(2).trim();
            if (instruction.equals("FROM")) fromCount++;
            if (instruction.equals("USER") && !argument.equals("0") && !argument.equalsIgnoreCase("root")) {
                facts.add(FactType.DOCKER_NON_ROOT_USER_DECLARED, "user=" + safeToken(argument), file.relativePath());
            }
            if (instruction.equals("HEALTHCHECK")) {
                facts.add(FactType.DOCKER_HEALTHCHECK_DECLARED, "path=" + file.relativePath(), file.relativePath());
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
        facts.add(FactType.DOCKER_COMPOSE_PRESENT, "path=" + file.relativePath(), file.relativePath());
        String[] lines = file.content().split("\\R");
        boolean services = false;
        boolean volumes = false;
        for (String line : lines) {
            if (line.matches("^services:\\s*(?:#.*)?$")) { services = true; volumes = false; continue; }
            if (line.matches("^volumes:\\s*(?:#.*)?$")) { volumes = true; services = false; continue; }
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) { services = false; volumes = false; }
            Matcher entry = Pattern.compile("^  ([A-Za-z0-9_.-]+):\\s*(?:#.*)?$").matcher(line);
            if (entry.find()) {
                if (services) facts.add(FactType.DOCKER_SERVICE_DECLARED,
                        "service=" + entry.group(1), file.relativePath());
                if (volumes) facts.add(FactType.DOCKER_VOLUME_DECLARED,
                        "volume=" + entry.group(1), file.relativePath());
            }
            if (line.matches("^\\s+healthcheck:\\s*(?:#.*)?$")) facts.add(
                    FactType.DOCKER_HEALTHCHECK_DECLARED, "composeHealthcheck=true", file.relativePath());
        }
    }

    private String safeToken(String value) {
        return value.replaceAll("\\s+#.*$", "").trim();
    }
}
