package com.hopeful117.devlogai.contracts.engineeringcontext;

public record EngineeringSymbolLocation(

        int beginLine,
        int beginColumn,
        int endLine,
        int endColumn
) {
}
