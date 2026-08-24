package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.List;

public record EngineeringSymbolDeclaration(

        String kind,
        String name,
        String owningType,
        List<String> modifiers,
        String returnType,
        List<EngineeringSymbolParameter> parameters,
        List<String> annotations,
        EngineeringSymbolLocation location
) {
    public EngineeringSymbolDeclaration {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
    }
}
