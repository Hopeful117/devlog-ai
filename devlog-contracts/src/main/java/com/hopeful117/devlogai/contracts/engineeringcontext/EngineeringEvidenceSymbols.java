package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.List;

/**
 * Java symbol declarations extracted from the file of a selected evidence item
 * by the repository context symbol enricher. Mirrors the internal enrichment
 * result without introducing a new taxonomy.
 */
public record EngineeringEvidenceSymbols(

        String status,
        boolean truncated,
        Integer returnedSymbolCount,
        Integer availableSymbolCount,
        String extractorId,
        String extractorVersion,
        String revision,
        List<EngineeringSymbolDeclaration> declarations
) {
    public EngineeringEvidenceSymbols {
        declarations = declarations == null
                ? List.of() : List.copyOf(declarations);
    }
}
