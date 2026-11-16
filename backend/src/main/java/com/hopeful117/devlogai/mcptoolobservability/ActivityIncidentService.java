package com.hopeful117.devlogai.mcptoolobservability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityIncidentService {

    private final McpToolEventRepository repository;

    @Transactional(readOnly = true)
    public ActivityIncidentPageResponse listByProject(UUID projectId, int windowMinutes, int page, int size) {
        int clampedWindow = Math.clamp(windowMinutes, 1, 1440);
        int clampedPage   = Math.max(0, page);
        int clampedSize   = Math.clamp(size, 1, 50);
        Instant windowStart = Instant.now().minus(clampedWindow, ChronoUnit.MINUTES);

        long total = repository.countIncidentsByProject(projectId, windowStart);
        List<McpToolEventRepository.ActivityIncidentProjection> rows =
                repository.findIncidentsByProject(projectId, windowStart, clampedPage * clampedSize, clampedSize);

        List<ActivityIncidentView> content = rows.stream()
                .map(p -> toView(p))
                .toList();

        long errorCount = content.stream().filter(v -> "ERROR".equals(v.severity())).count();
        long warnCount  = content.stream().filter(v -> "WARN".equals(v.severity())).count();

        return new ActivityIncidentPageResponse(
                content, total, errorCount, warnCount,
                clampedWindow, windowStart, clampedPage, clampedSize);
    }

    static ActivityIncidentView toView(McpToolEventRepository.ActivityIncidentProjection p) {
        String severity      = p.isHasError() ? "ERROR" : "WARN";
        String incidentId    = computeIncidentId(p.getToolName(), p.getReasonCode());
        List<String> recentAlertIds = parseRecentAlertIds(p.getRecentAlertIdsRaw());
        return new ActivityIncidentView(
                incidentId,
                severity,
                p.getToolName(),
                p.getReasonCode(),
                p.getOccurrenceCount(),
                p.getFirstOccurredAt(),
                p.getLastOccurredAt(),
                recentAlertIds);
    }

    static String computeIncidentId(String toolName, String reasonCode) {
        String input = (toolName != null ? toolName : "\u0001") + "\u0000" + (reasonCode != null ? reasonCode : "\u0001");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static List<String> parseRecentAlertIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .limit(3)
                .toList();
    }
}
