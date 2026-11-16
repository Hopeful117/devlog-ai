package com.hopeful117.devlogai.mcptoolobservability;

import java.util.List;

public record AgentSessionPageResponse(
        List<AgentSessionView> content,
        long totalSessions,
        int page,
        int size
) {}
