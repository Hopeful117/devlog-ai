package com.hopeful117.devlogai.mcptoolobservability;

import java.util.List;

public record McpToolEventPageResponse(
        List<McpToolEventResponse> content,
        long totalElements,
        int page,
        int size
) {}
