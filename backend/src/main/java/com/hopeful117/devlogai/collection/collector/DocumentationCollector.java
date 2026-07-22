package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.documentation",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentationCollector extends AbstractFileCollector {
    private static final String VERSION = "documentation-v1";
    private static final Pattern HEADING = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");

    public DocumentationCollector(SecureRepositoryScanner scanner, CollectorLimits limits) {
        super(scanner, limits);
    }
    @Override public CollectorType type() { return CollectorType.DOCUMENTATION; }
    @Override public String version() { return VERSION; }

    @Override
    public CollectionResult collect(CollectionContext context) {
        RepositoryScan scan = scan(context, path -> path.toLowerCase(Locale.ROOT).endsWith(".md"));
        FactAccumulator facts = accumulator(context, scan);
        boolean docsDirectory = false;
        boolean adrDirectory = false;
        for (RepositoryFile file : scan.files()) {
            String path = file.relativePath();
            String lower = path.toLowerCase(Locale.ROOT);
            String name = fileName(lower);
            if (lower.startsWith("docs/") || lower.contains("/docs/")) docsDirectory = true;
            if (lower.contains("/adr/") || lower.contains("/adrs/")
                    || lower.contains("/decisions/")) adrDirectory = true;
            if (file.content() == null) continue;
            String metadata = "path=%s%nsize=%d%ntitle=%s".formatted(
                    path, file.size(), firstHeading(file.content()));
            facts.add(FactType.MARKDOWN_DOCUMENT_PRESENT, metadata, path);
            if (!path.contains("/") && name.startsWith("readme"))
                facts.add(FactType.README_PRESENT, metadata, path);
            if (name.matches("(?:adr[-_])?\\d+.*\\.md") || lower.contains("/decisions/"))
                facts.add(FactType.ADR_DOCUMENT_PRESENT, metadata, path);
            if (name.startsWith("contributing")) facts.add(FactType.CONTRIBUTING_GUIDE_PRESENT, metadata, path);
            if (name.startsWith("changelog") || name.startsWith("changes"))
                facts.add(FactType.CHANGELOG_PRESENT, metadata, path);
            if (lower.contains("api") || lower.contains("openapi") || lower.contains("swagger"))
                facts.add(FactType.API_DOCUMENTATION_PRESENT, metadata, path);
            if (lower.contains("architect")) facts.add(FactType.ARCHITECTURE_DOCUMENTATION_PRESENT, metadata, path);
        }
        if (docsDirectory) facts.add(FactType.DOCUMENTATION_DIRECTORY_PRESENT,
                "documentationDirectory=docs", "docs/");
        if (adrDirectory) facts.add(FactType.ADR_DIRECTORY_PRESENT,
                "adrDirectoryPresent=true", "repository:/");
        return result(facts);
    }

    private String firstHeading(String content) {
        Matcher matcher = HEADING.matcher(content);
        if (!matcher.find()) return "";
        String title = matcher.group(1).trim().replaceAll("\\s+", " ");
        if (title.matches("(?i).*(?:password|secret|token|api[-_ ]?key)\\s*[:=].*")) {
            return "[redacted]";
        }
        return title.substring(0, Math.min(title.length(), 200));
    }
}
