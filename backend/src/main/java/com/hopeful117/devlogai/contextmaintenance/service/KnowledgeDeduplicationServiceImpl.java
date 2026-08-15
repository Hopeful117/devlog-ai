package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.insight.service.InsightService;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDeduplicationServiceImpl implements KnowledgeDeduplicationService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final MaintenanceFindingRepository findingRepository;
    private final MaintenanceFindingMapper findingMapper;
    private final InsightService insightService;

    @Override
    @Transactional
    public MaintenanceFindingResponse mergeExactDuplicate(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFinding finding = findingRepository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (finding.getIssueType() != MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE) {
            throw new ConflictException("This action is only available for TRUSTED_KNOWLEDGE_EXACT_DUPLICATE findings.");
        }

        UUID[] insightIds = extractInsightIdsFromDetails(finding.getDetails());
        if (insightIds == null || insightIds.length < 2) {
            throw new ConflictException("Could not extract insight IDs from finding details.");
        }

        UUID canonicalInsightId = insightIds[0];
        for (int i = 1; i < insightIds.length; i++) {
            try {
                insightService.supersedeInsight(insightIds[i], canonicalInsightId);
            } catch (Exception e) {
                log.warn("Failed to supersede insight {}: {}", insightIds[i], e.getMessage());
            }
        }

        applyAction(finding, MaintenanceFindingActionType.RESOLVE, actedBy, comment);
        return findingMapper.toResponse(findingRepository.save(finding));
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse resolveSemanticDuplicate(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFinding finding = findingRepository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (finding.getIssueType() != MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE) {
            throw new ConflictException("This action is only available for TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE findings.");
        }

        UUID[] insightIds = extractInsightIdsFromDetails(finding.getDetails());
        if (insightIds == null || insightIds.length < 2) {
            throw new ConflictException("Could not extract insight IDs from finding details.");
        }

        UUID canonicalInsightId = insightIds[0];
        for (int i = 1; i < insightIds.length; i++) {
            try {
                insightService.supersedeInsight(insightIds[i], canonicalInsightId);
            } catch (Exception e) {
                log.warn("Failed to supersede insight {}: {}", insightIds[i], e.getMessage());
            }
        }

        applyAction(finding, MaintenanceFindingActionType.RESOLVE, actedBy, comment);
        return findingMapper.toResponse(findingRepository.save(finding));
    }

    private void applyAction(
            MaintenanceFinding finding,
            MaintenanceFindingActionType actionType,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFindingAction action = MaintenanceFindingAction.builder()
                .finding(finding)
                .actionType(actionType)
                .actedBy(actedBy)
                .comment(normalizeDetails(comment))
                .build();
        finding.getActions().add(0, action);
        finding.setStatus(targetStatus(actionType));
    }

    private MaintenanceFindingStatus targetStatus(MaintenanceFindingActionType actionType) {
        return switch (actionType) {
            case ACKNOWLEDGE -> MaintenanceFindingStatus.ACKNOWLEDGED;
            case DISMISS -> MaintenanceFindingStatus.DISMISSED;
            case RESOLVE, AUTO_RESOLVE -> MaintenanceFindingStatus.RESOLVED;
        };
    }

    private String normalizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        return details.trim();
    }

    private UUID[] extractInsightIdsFromDetails(String details) {
        if (details == null) {
            return null;
        }
        Matcher matcher = UUID_PATTERN.matcher(details);
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        while (matcher.find()) {
            try {
                ids.add(UUID.fromString(matcher.group()));
            } catch (IllegalArgumentException e) {
                log.warn("Failed to parse UUID from finding details: {}", matcher.group());
            }
        }
        return ids.isEmpty() ? null : ids.toArray(new UUID[0]);
    }
}
