package com.hopeful117.devlogai.collection.collector;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

abstract class AbstractFileCollector implements KnowledgeCollector {
    protected final SecureRepositoryScanner scanner;
    protected final CollectorLimits limits;

    protected AbstractFileCollector(SecureRepositoryScanner scanner, CollectorLimits limits) {
        this.scanner = scanner;
        this.limits = limits;
    }

    protected RepositoryScan scan(CollectionContext context, Predicate<String> contentSelector) {
        return scanner.scan(context, contentSelector);
    }

    protected CollectionResult result(FactAccumulator facts) {
        return CollectionResult.of(type(), version(), facts.facts(), facts.warnings());
    }

    protected FactAccumulator accumulator(CollectionContext context, RepositoryScan scan) {
        FactAccumulator result = new FactAccumulator(
                version(), context.resolvedRevision(), "source:" + context.sourceId(),
                limits.getMaxFactsPerType());
        result.warnings(scan.warnings());
        return result;
    }

    protected String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    protected boolean named(String path, String... names) {
        String file = fileName(path).toLowerCase(Locale.ROOT);
        return List.of(names).stream().map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(file::equals);
    }
}
