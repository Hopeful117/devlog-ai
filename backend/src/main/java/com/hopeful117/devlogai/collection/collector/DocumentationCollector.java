package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.documentation",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentationCollector extends AbstractFileCollector {
    private static final String VERSION = "documentation-v1";

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
            if (lower.startsWith("docs/") || lower.contains("/docs/")) docsDirectory = true;
            if (lower.contains("/adr/") || lower.contains("/adrs/")
                    || lower.contains("/decisions/")) adrDirectory = true;
            if (file.content() != null) addDocumentFacts(file, facts, lower);
        }
        if (docsDirectory) facts.add(FactType.DOCUMENTATION_DIRECTORY_PRESENT,
                "documentationDirectory=docs", "docs/");
        if (adrDirectory) facts.add(FactType.ADR_DIRECTORY_PRESENT,
                "adrDirectoryPresent=true", "repository:/");
        return result(facts);
    }

    private void addDocumentFacts(RepositoryFile file, FactAccumulator facts, String lowerPath) {
        String path = file.relativePath();
        String name = fileName(lowerPath);
        String metadata = "path=%s%nsize=%d%ntitle=%s".formatted(
                path, file.size(), firstHeading(file.content()));
        facts.add(FactType.MARKDOWN_DOCUMENT_PRESENT, metadata, path);
        if (!path.contains("/") && name.startsWith("readme"))
            facts.add(FactType.README_PRESENT, metadata, path);
        if (isAdrName(name) || lowerPath.contains("/decisions/"))
            facts.add(FactType.ADR_DOCUMENT_PRESENT, metadata, path);
        if (name.startsWith("contributing"))
            facts.add(FactType.CONTRIBUTING_GUIDE_PRESENT, metadata, path);
        if (name.startsWith("changelog") || name.startsWith("changes"))
            facts.add(FactType.CHANGELOG_PRESENT, metadata, path);
        if (lowerPath.contains("api") || lowerPath.contains("openapi")
                || lowerPath.contains("swagger"))
            facts.add(FactType.API_DOCUMENTATION_PRESENT, metadata, path);
        if (lowerPath.contains("architect"))
            facts.add(FactType.ARCHITECTURE_DOCUMENTATION_PRESENT, metadata, path);
    }

    private boolean isAdrName(String name) {
        if (!name.endsWith(".md")) return false;
        String stem = name.substring(0, name.length() - 3);
        if (stem.startsWith("adr-") || stem.startsWith("adr_")) stem = stem.substring(4);
        int digitCount = 0;
        while (digitCount < stem.length() && Character.isDigit(stem.charAt(digitCount))) {
            digitCount++;
        }
        return digitCount > 0;
    }

    private String firstHeading(String content) {
        for (String line : content.lines().toList()) {
            if (!line.startsWith("# ")) continue;
            String title = normalizeWhitespace(line.substring(2));
            return containsSensitiveAssignment(title) ? "[redacted]" :
                    title.substring(0, Math.min(title.length(), 200));
        }
        return "";
    }

    private String normalizeWhitespace(String value) {
        return String.join(" ", value.trim().split("\\s++"));
    }

    private boolean containsSensitiveAssignment(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        int separator = Math.max(lower.indexOf(':'), lower.indexOf('='));
        if (separator < 0) return false;
        String key = lower.substring(0, separator).replace("-", "").replace("_", "")
                .replace(" ", "");
        return key.contains("password") || key.contains("secret")
                || key.contains("token") || key.contains("apikey");
    }
}
