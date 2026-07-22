package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.spring",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringCollector extends AbstractFileCollector {
    private static final String VERSION = "spring-v1";
    private static final Pattern TYPE_NAME = Pattern.compile(
            "(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Map<String, FactType> DEPENDENCIES = new LinkedHashMap<>();
    static {
        DEPENDENCIES.put("spring-boot", FactType.SPRING_BOOT_DETECTED);
        DEPENDENCIES.put("spring-cloud", FactType.SPRING_CLOUD_DETECTED);
        DEPENDENCIES.put("spring-security", FactType.SPRING_SECURITY_DETECTED);
        DEPENDENCIES.put("spring-data", FactType.SPRING_DATA_DETECTED);
        DEPENDENCIES.put("spring-web", FactType.SPRING_WEB_DETECTED);
        DEPENDENCIES.put("spring-boot-starter-actuator", FactType.SPRING_ACTUATOR_DETECTED);
    }

    public SpringCollector(SecureRepositoryScanner scanner, CollectorLimits limits) {
        super(scanner, limits);
    }
    @Override public CollectorType type() { return CollectorType.SPRING; }
    @Override public String version() { return VERSION; }

    @Override
    public CollectionResult collect(CollectionContext context) {
        RepositoryScan scan = scan(context, this::springRelevant);
        FactAccumulator facts = accumulator(context, scan);
        for (RepositoryFile file : scan.files()) {
            if (file.content() == null) continue;
            String path = file.relativePath();
            String name = fileName(path).toLowerCase(Locale.ROOT);
            if (name.equals("application.properties") || name.equals("application.yml")
                    || name.equals("application.yaml")) {
                facts.add(FactType.SPRING_CONFIGURATION_FILE_PRESENT,
                        "configurationFile=" + path, path);
                continue;
            }
            if (name.equals("pom.xml") || name.endsWith(".gradle") || name.endsWith(".gradle.kts")) {
                detectDependencies(file, facts);
            } else if (name.endsWith(".java") || name.endsWith(".kt")) {
                detectStereotypes(file, facts);
            }
        }
        return result(facts);
    }

    private boolean springRelevant(String path) {
        String name = fileName(path).toLowerCase(Locale.ROOT);
        return name.equals("pom.xml") || name.endsWith(".gradle") || name.endsWith(".gradle.kts")
                || name.endsWith(".java") || name.endsWith(".kt")
                || name.equals("application.properties") || name.equals("application.yml")
                || name.equals("application.yaml");
    }

    private void detectDependencies(RepositoryFile file, FactAccumulator facts) {
        String lower = file.content().toLowerCase(Locale.ROOT);
        DEPENDENCIES.forEach((token, type) -> {
            if (lower.contains(token)) facts.add(type, "declaration=" + token, file.relativePath());
        });
        Matcher bootVersion = Pattern.compile(
                "<spring-boot.version>\\s*([^<]+)\\s*</spring-boot.version>|" +
                        "org\\.springframework\\.boot[^0-9]{0,20}([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)")
                .matcher(file.content());
        if (bootVersion.find()) {
            String value = bootVersion.group(1) != null ? bootVersion.group(1) : bootVersion.group(2);
            facts.add(FactType.SPRING_BOOT_VERSION_DECLARED,
                    "version=" + value.trim(), file.relativePath());
        }
    }

    private void detectStereotypes(RepositoryFile file, FactAccumulator facts) {
        String content = file.content();
        String typeName = declaredType(content);
        if ((content.contains("@RestController") || content.contains("@Controller")) && typeName != null) {
            facts.add(FactType.REST_CONTROLLER_DECLARED,
                    "type=" + typeName, file.relativePath());
        }
        if (content.contains("@Configuration") && typeName != null) {
            facts.add(FactType.SPRING_CONFIGURATION_CLASS_DECLARED,
                    "type=" + typeName, file.relativePath());
        }
    }

    private String declaredType(String content) {
        Matcher matcher = TYPE_NAME.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }
}
