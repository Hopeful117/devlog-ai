package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.docker",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class DockerCollector extends AbstractFileCollector {
    private static final String VERSION = "docker-v2";
    private static final String PATH_METADATA_PREFIX = "path=";
    private static final Pattern URL_HOST = Pattern.compile(
            "(?:[a-z][a-z0-9+.-]*:)+//([a-zA-Z0-9_.-]+)");
    private static final Pattern VARIABLE_DEFAULT = Pattern.compile(
            "\\$\\{[A-Za-z_][A-Za-z0-9_]*(?::-|-)([^}]+)}");
    private static final Pattern HOST_WITH_OPTIONAL_PORT = Pattern.compile(
            "^([a-zA-Z0-9_.-]+)(?::[0-9]+)?(?:/.*)?$");

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
        List<String> lines = file.content().lines().toList();
        Set<String> serviceNames = new LinkedHashSet<>();
        ComposeSection section = ComposeSection.NONE;
        int entryIndent = -1;
        for (String line : lines) {
            String content = withoutComment(line).stripTrailing();
            if (isTopLevel(content)) {
                section = topLevelSection(content);
                entryIndent = -1;
            }
            if (section == ComposeSection.SERVICES) {
                String serviceEntry = composeEntry(content, entryIndent);
                if (serviceEntry != null) {
                    entryIndent = leadingWhitespace(content);
                    serviceNames.add(serviceEntry);
                    facts.add(FactType.DOCKER_SERVICE_DECLARED,
                            "service=" + serviceEntry, file.relativePath());
                }
            } else if (section == ComposeSection.VOLUMES) {
                String entry = composeEntry(content, entryIndent);
                if (entry != null) {
                    entryIndent = leadingWhitespace(content);
                    facts.add(FactType.DOCKER_VOLUME_DECLARED,
                            "volume=" + entry, file.relativePath());
                }
            }
            if (content.stripLeading().equals("healthcheck:")) facts.add(
                    FactType.DOCKER_HEALTHCHECK_DECLARED,
                     "composeHealthcheck=true", file.relativePath());
        }
        parseServiceWiring(lines, serviceNames, file, facts);
    }

    private void parseServiceWiring(List<String> lines, Set<String> serviceNames,
            RepositoryFile file, FactAccumulator facts) {
        ComposeSection section = ComposeSection.NONE;
        String currentService = null;
        int serviceIndent = -1;
        int dependsOnIndent = -1;
        int environmentIndent = -1;
        for (String line : lines) {
            String content = withoutComment(line).stripTrailing();
            if (isTopLevel(content)) {
                section = topLevelSection(content);
                currentService = null;
                serviceIndent = -1;
                dependsOnIndent = -1;
                environmentIndent = -1;
            }
            if (section != ComposeSection.SERVICES) continue;

            String serviceEntry = composeEntry(content, serviceIndent);
            if (serviceEntry != null) {
                serviceIndent = leadingWhitespace(content);
                currentService = serviceEntry;
                dependsOnIndent = -1;
                environmentIndent = -1;
                continue;
            }
            if (currentService == null || content.isBlank()) continue;

            int indent = leadingWhitespace(content);
            String stripped = content.stripLeading();
            if (dependsOnIndent >= 0 && indent <= dependsOnIndent) dependsOnIndent = -1;
            if (environmentIndent >= 0 && indent <= environmentIndent) environmentIndent = -1;

            if (stripped.startsWith("depends_on:")) {
                dependsOnIndent = indent;
                addInlineDependsOn(stripped.substring("depends_on:".length()), currentService,
                        serviceNames, file, facts);
                continue;
            }
            if (stripped.equals("environment:")) {
                environmentIndent = indent;
                continue;
            }
            if (dependsOnIndent >= 0) {
                addDependsOn(stripped, currentService, serviceNames, file, facts);
            }
            if (environmentIndent >= 0) {
                detectServiceReference(stripped, currentService, serviceNames, file, facts);
            }
        }
    }

    private void addInlineDependsOn(String value, String currentService, Set<String> serviceNames,
            RepositoryFile file, FactAccumulator facts) {
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            for (String target : trimmed.substring(1, trimmed.length() - 1).split(",")) {
                addRelationshipFact(FactType.DOCKER_SERVICE_DEPENDS_ON, currentService,
                        unquote(target.trim()), serviceNames, file, facts);
            }
        } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            Matcher keyMatcher = Pattern.compile("(?:^|,)\\s*['\"]?([a-zA-Z0-9_.-]+)['\"]?\\s*:")
                    .matcher(trimmed.substring(1, trimmed.length() - 1));
            while (keyMatcher.find()) {
                addRelationshipFact(FactType.DOCKER_SERVICE_DEPENDS_ON, currentService,
                        keyMatcher.group(1), serviceNames, file, facts);
            }
        }
    }

    private void addDependsOn(String value, String currentService, Set<String> serviceNames,
            RepositoryFile file, FactAccumulator facts) {
        String target = value;
        if (target.startsWith("- ")) target = target.substring(2).trim();
        if (target.endsWith(":")) target = target.substring(0, target.length() - 1).trim();
        addRelationshipFact(FactType.DOCKER_SERVICE_DEPENDS_ON, currentService,
                unquote(target), serviceNames, file, facts);
    }

    private void detectServiceReference(String value, String currentService, Set<String> serviceNames,
            RepositoryFile file, FactAccumulator facts) {
        String environmentValue = environmentValue(value);
        if (environmentValue == null) return;
        Matcher urlMatcher = URL_HOST.matcher(environmentValue);
        while (urlMatcher.find()) {
            String host = urlMatcher.group(1);
            addRelationshipFact(FactType.DOCKER_SERVICE_ENV_REFERENCE, currentService,
                    host, serviceNames, "source=environment", file, facts);
        }
        Matcher defaultMatcher = VARIABLE_DEFAULT.matcher(environmentValue);
        while (defaultMatcher.find()) {
            addHostValue(defaultMatcher.group(1), currentService, serviceNames, file, facts);
        }
        if (!urlMatcher.reset().find() && !defaultMatcher.reset().find()) {
            addHostValue(environmentValue, currentService, serviceNames, file, facts);
        }
    }

    private String environmentValue(String value) {
        String candidate = value;
        if (candidate.startsWith("- ")) candidate = candidate.substring(2).trim();
        int equals = candidate.indexOf('=');
        int colon = candidate.indexOf(':');
        int separator = equals >= 0 && (colon < 0 || equals < colon) ? equals : colon;
        if (separator < 0 || separator == candidate.length() - 1) return null;
        return unquote(candidate.substring(separator + 1).trim());
    }

    private void addHostValue(String value, String currentService, Set<String> serviceNames,
            RepositoryFile file, FactAccumulator facts) {
        Matcher matcher = HOST_WITH_OPTIONAL_PORT.matcher(unquote(value.trim()));
        if (!matcher.matches()) return;
        addRelationshipFact(FactType.DOCKER_SERVICE_ENV_REFERENCE, currentService,
                matcher.group(1), serviceNames, "source=environment", file, facts);
    }

    private void addRelationshipFact(FactType type, String source, String target,
            Set<String> serviceNames, RepositoryFile file, FactAccumulator facts) {
        addRelationshipFact(type, source, target, serviceNames, null, file, facts);
    }

    private void addRelationshipFact(FactType type, String source, String target,
            Set<String> serviceNames, String detail, RepositoryFile file, FactAccumulator facts) {
        if (target == null || target.equals(source) || !serviceNames.contains(target)) return;
        String content = "from=" + source + ",to=" + target;
        if (detail != null) content += "," + detail;
        facts.add(type, content, file.relativePath());
    }

    private int leadingWhitespace(String value) {
        int count = 0;
        while (count < value.length() && Character.isWhitespace(value.charAt(count))) count++;
        return count;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean isTopLevel(String line) {
        return !line.isBlank() && !Character.isWhitespace(line.charAt(0));
    }

    private ComposeSection topLevelSection(String line) {
        if (line.equals("services:")) return ComposeSection.SERVICES;
        if (line.equals("volumes:")) return ComposeSection.VOLUMES;
        return ComposeSection.NONE;
    }

    private String composeEntry(String line, int expectedIndent) {
        int indent = leadingWhitespace(line);
        if (indent == 0 || (expectedIndent >= 0 && indent != expectedIndent)) return null;
        String candidate = line.substring(indent);
        if (!candidate.endsWith(":")) return null;
        String name = unquote(candidate.substring(0, candidate.length() - 1).trim());
        return name.chars().allMatch(this::isComposeNameCharacter) ? name : null;
    }

    private boolean isComposeNameCharacter(int value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '.' || value == '-';
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
