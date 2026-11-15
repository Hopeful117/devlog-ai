package com.hopeful117.devlogai.mcptoolobservability;

import java.util.List;

public record McpOperationPageResponse(
        List<McpOperationView> content,
        long totalOperations,
        int page,
        int size
) {}
