package com.hopeful117.devlogai.mcptoolobservability;

import java.util.List;

public record ActivityAlertPageResponse(
        List<ActivityAlertView> content,
        long totalAlerts,
        int  page,
        int  size
) {}
