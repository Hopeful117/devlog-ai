package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class JavaDeclarationExtractor {
    public static final String EXTRACTOR_ID = "java-declarations";
    public static final String EXTRACTOR_VERSION = "v1";

    public Extraction extract(String source, RepositorySymbolPolicy policy) {
        try {
            JavaParser parser = new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
            ParseResult<CompilationUnit> parsed = parser.parse(source);
            if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                return new Extraction(Outcome.UNSUPPORTED, List.of(), 0, false,
                        "MALFORMED_OR_UNSUPPORTED_SOURCE");
            }
            List<RepositoryEvidenceSymbols.JavaDeclaration> declarations =
                    declarations(parsed.getResult().orElseThrow(), policy);
            int available = declarations.size();
            List<RepositoryEvidenceSymbols.JavaDeclaration> bounded = declarations.stream()
                    .limit(policy.getMaxSymbolsPerFile()).toList();
            return new Extraction(bounded.isEmpty() ? Outcome.NO_SYMBOLS : Outcome.EXTRACTED,
                    bounded, available, available > bounded.size(), null);
        } catch (RuntimeException exception) {
            return new Extraction(Outcome.FAILED, List.of(), 0, false,
                    "EXTRACTION_FAILURE");
        }
    }

    private List<RepositoryEvidenceSymbols.JavaDeclaration> declarations(
            CompilationUnit unit,
            RepositorySymbolPolicy policy
    ) {
        List<RepositoryEvidenceSymbols.JavaDeclaration> result = new ArrayList<>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            RepositoryEvidenceSymbols.Kind kind = typeKind(type);
            if (kind != null) result.add(declaration(type, kind, owner(type), null,
                    List.of(), policy));
        }
        for (ConstructorDeclaration constructor
                : unit.findAll(ConstructorDeclaration.class)) {
            result.add(declaration(constructor,
                    RepositoryEvidenceSymbols.Kind.CONSTRUCTOR,
                    owner(constructor), null, parameters(constructor.getParameters(), policy),
                    policy));
        }
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            result.add(declaration(method, RepositoryEvidenceSymbols.Kind.METHOD,
                    owner(method), cap(method.getType().asString(), policy),
                    parameters(method.getParameters(), policy), policy));
        }
        result.sort(Comparator
                .comparingInt((RepositoryEvidenceSymbols.JavaDeclaration value) ->
                        value.location() == null ? Integer.MAX_VALUE
                                : value.location().beginLine())
                .thenComparingInt(value -> value.location() == null ? Integer.MAX_VALUE
                        : value.location().beginColumn())
                .thenComparing(value -> value.kind().ordinal())
                .thenComparing(value -> value.owningType() == null ? "" : value.owningType())
                .thenComparing(RepositoryEvidenceSymbols.JavaDeclaration::name)
                .thenComparing(value -> value.parameters().toString()));
        return List.copyOf(result);
    }

    private RepositoryEvidenceSymbols.JavaDeclaration declaration(
            Node node,
            RepositoryEvidenceSymbols.Kind kind,
            String owner,
            String returnType,
            List<RepositoryEvidenceSymbols.Parameter> parameters,
            RepositorySymbolPolicy policy
    ) {
        String name = switch (node) {
            case TypeDeclaration<?> type -> type.getNameAsString();
            case ConstructorDeclaration constructor -> constructor.getNameAsString();
            case MethodDeclaration method -> method.getNameAsString();
            default -> throw new IllegalArgumentException("Unsupported declaration node");
        };
        List<String> modifiers = node instanceof NodeWithModifiers<?> withModifiers
                ? withModifiers.getModifiers().stream()
                        .map(value -> value.getKeyword().asString()).sorted().toList()
                : List.of();
        List<String> annotations = node instanceof NodeWithAnnotations<?> withAnnotations
                ? withAnnotations.getAnnotations().stream()
                        .map(value -> cap(value.getNameAsString(), policy)).sorted().toList()
                : List.of();
        RepositoryEvidenceSymbols.SourceLocation location = node.getRange()
                .map(value -> new RepositoryEvidenceSymbols.SourceLocation(
                        value.begin.line, value.begin.column,
                        value.end.line, value.end.column))
                .orElse(null);
        return new RepositoryEvidenceSymbols.JavaDeclaration(kind, cap(name, policy),
                cap(owner, policy), modifiers, returnType, parameters, annotations, location);
    }

    private List<RepositoryEvidenceSymbols.Parameter> parameters(
            com.github.javaparser.ast.NodeList<com.github.javaparser.ast.body.Parameter> values,
            RepositorySymbolPolicy policy
    ) {
        return values.stream().map(value -> new RepositoryEvidenceSymbols.Parameter(
                cap(value.getType().asString(), policy), cap(value.getNameAsString(), policy)))
                .toList();
    }

    private RepositoryEvidenceSymbols.Kind typeKind(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration value) {
            return value.isInterface() ? RepositoryEvidenceSymbols.Kind.INTERFACE
                    : RepositoryEvidenceSymbols.Kind.CLASS;
        }
        if (type instanceof RecordDeclaration) return RepositoryEvidenceSymbols.Kind.RECORD;
        if (type instanceof EnumDeclaration) return RepositoryEvidenceSymbols.Kind.ENUM;
        if (type instanceof AnnotationDeclaration) {
            return RepositoryEvidenceSymbols.Kind.ANNOTATION_DECLARATION;
        }
        return null;
    }

    private String owner(Node node) {
        List<String> names = new ArrayList<>();
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) {
                names.addFirst(type.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        if (node instanceof TypeDeclaration<?> type) names.add(type.getNameAsString());
        return names.isEmpty() ? null : String.join(".", names);
    }

    private String cap(String value, RepositorySymbolPolicy policy) {
        if (value == null || value.length() <= policy.getMaxComponentCharacters()) return value;
        return value.substring(0, policy.getMaxComponentCharacters());
    }

    public record Extraction(
            Outcome outcome,
            List<RepositoryEvidenceSymbols.JavaDeclaration> declarations,
            int availableCount,
            boolean truncated,
            String reason
    ) {
        public Extraction { declarations = List.copyOf(declarations); }
    }

    public enum Outcome { EXTRACTED, NO_SYMBOLS, UNSUPPORTED, FAILED }
}
