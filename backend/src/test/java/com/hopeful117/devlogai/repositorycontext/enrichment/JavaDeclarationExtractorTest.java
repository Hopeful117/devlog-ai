package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaDeclarationExtractorTest {
    private final RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
    private final JavaDeclarationExtractor extractor = new JavaDeclarationExtractor();

    @Test
    void extractsModernNestedAndExecutableDeclarationsDeterministically() {
        String source = """
                package sample;
                @Deprecated public class Service<T> {
                    public Service() {}
                    @Deprecated public <R> R execute(T value, int count) { return null; }
                    public String execute(String value) { return value; }
                    interface Port { void send(String value); }
                }
                record Event(String name) {}
                enum State { READY }
                @interface Marker {}
                """;

        var first = extractor.extract(source, policy);
        var second = extractor.extract(source, policy);

        assertEquals(JavaDeclarationExtractor.Outcome.EXTRACTED, first.outcome());
        assertEquals(first, second);
        assertTrue(first.declarations().stream().anyMatch(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.CLASS
                        && value.name().equals("Service")
                        && value.annotations().equals(List.of("Deprecated"))));
        assertTrue(first.declarations().stream().anyMatch(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.INTERFACE
                        && value.owningType().equals("Service.Port")));
        assertTrue(first.declarations().stream().anyMatch(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.RECORD));
        assertTrue(first.declarations().stream().anyMatch(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.ENUM));
        assertTrue(first.declarations().stream().anyMatch(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.ANNOTATION_DECLARATION));
        var method = first.declarations().stream().filter(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.METHOD
                        && value.name().equals("execute")).findFirst().orElseThrow();
        assertEquals("R", method.returnType());
        assertEquals(List.of(new RepositoryEvidenceSymbols.Parameter("T", "value"),
                new RepositoryEvidenceSymbols.Parameter("int", "count")),
                method.parameters());
        assertEquals("Service", method.owningType());
        assertTrue(method.location().beginLine() > 0);
        assertEquals(2, first.declarations().stream().filter(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.METHOD
                        && value.name().equals("execute")).count());
    }

    @Test
    void reportsNoSymbolsAndRejectsMalformedSourceWithoutPartialTruth() {
        assertEquals(JavaDeclarationExtractor.Outcome.NO_SYMBOLS,
                extractor.extract("package sample; import java.util.List;", policy).outcome());
        var malformed = extractor.extract("class Broken { void method( }", policy);
        assertEquals(JavaDeclarationExtractor.Outcome.UNSUPPORTED, malformed.outcome());
        assertTrue(malformed.declarations().isEmpty());
    }

    @Test
    void truncatesAfterExplicitDeterministicOrdering() {
        policy.setMaxSymbolsPerFile(2);
        var result = extractor.extract("class A { void z() {} void a() {} }", policy);

        assertTrue(result.truncated());
        assertEquals(3, result.availableCount());
        assertEquals(2, result.declarations().size());
        assertFalse(result.declarations().getFirst().name().isBlank());
    }
}
