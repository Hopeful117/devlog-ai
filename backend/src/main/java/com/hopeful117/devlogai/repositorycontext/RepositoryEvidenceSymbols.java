package com.hopeful117.devlogai.repositorycontext;

import java.util.List;

public record RepositoryEvidenceSymbols(
        Status status,
        String reason,
        String policyId,
        String policyVersion,
        String extractorId,
        String extractorVersion,
        String revision,
        Integer allocationRank,
        List<String> allocationReasons,
        boolean truncated,
        int returnedSymbolCount,
        Integer availableSymbolCount,
        List<JavaDeclaration> declarations
) {
    public RepositoryEvidenceSymbols {
        allocationReasons = allocationReasons == null
                ? List.of() : List.copyOf(allocationReasons);
        declarations = declarations == null ? List.of() : List.copyOf(declarations);
    }

    public enum Status {
        EXTRACTED,
        NO_SUPPORTED_SYMBOLS,
        SKIPPED,
        UNSUPPORTED,
        UNAVAILABLE,
        FAILED
    }

    public record JavaDeclaration(
            Kind kind,
            String name,
            String owningType,
            List<String> modifiers,
            String returnType,
            List<Parameter> parameters,
            List<String> annotations,
            SourceLocation location
    ) {
        public JavaDeclaration {
            modifiers = List.copyOf(modifiers);
            parameters = List.copyOf(parameters);
            annotations = List.copyOf(annotations);
        }
    }

    public enum Kind {
        CLASS,
        INTERFACE,
        RECORD,
        ENUM,
        ANNOTATION_DECLARATION,
        CONSTRUCTOR,
        METHOD
    }

    public record Parameter(String type, String name) {
    }

    public record SourceLocation(
            int beginLine,
            int beginColumn,
            int endLine,
            int endColumn
    ) {
    }
}
