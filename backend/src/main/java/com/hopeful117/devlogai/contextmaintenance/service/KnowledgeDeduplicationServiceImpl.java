package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.service.InsightService;
import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.proposal.service.ValidatableProposalService;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
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
    private final ValidatableProposalService proposalService;
    private final ValidatableProposalRepository proposalRepository;

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

        return mergeAndResolve(finding, actedBy, comment);
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

        return mergeAndResolve(finding, actedBy, comment);
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse resolveOverlapReview(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFinding finding = findingRepository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (finding.getIssueType() != MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW) {
            throw new ConflictException("This action is only available for TRUSTED_KNOWLEDGE_OVERLAP_REVIEW findings.");
        }

        return mergeAndResolve(finding, actedBy, comment);
    }

    @Transactional
    public MaintenanceFindingResponse mergeAndResolve(
            MaintenanceFinding finding,
            UUID actedBy,
            String comment
    ) {
        UUID[] insightIds = extractInsightIdsFromDetails(finding.getDetails());
        if (insightIds == null || insightIds.length < 2) {
            throw new ConflictException("Could not extract insight IDs from finding details.");
        }

        // Try to fetch insights to determine richness; fall back to insightIds[0] if any are missing
        List<Insight> insights = new ArrayList<>();
        boolean fetchSuccessful = true;
        for (UUID id : insightIds) {
            Optional<Insight> optional = insightService.findById(id);
            if (optional.isPresent()) {
                insights.add(optional.get());
            } else {
                fetchSuccessful = false;
                log.warn("Could not fetch insight {} from repository, falling back to ID-based selection", id);
                break;
            }
        }

        UUID canonicalInsightId;
        if (fetchSuccessful && insights.size() >= 2) {
            Insight canonicalInsight = selectCanonicalInsight(insights, finding.getIssueType());
            boolean contentChanged = enrichCanonicalIfNeeded(canonicalInsight, insights);
            canonicalInsightId = canonicalInsight.getId();

            for (Insight insight : insights) {
                if (!insight.getId().equals(canonicalInsightId)) {
                    insightService.supersedeInsight(insight.getId(), canonicalInsightId);
                }
            }
        } else {
            // Fall back to original behavior: first insight is canonical
            canonicalInsightId = insightIds[0];
            for (int i = 1; i < insightIds.length; i++) {
                insightService.supersedeInsight(insightIds[i], canonicalInsightId);
            }
        }

        applyAction(finding, MaintenanceFindingActionType.RESOLVE, actedBy, comment);

        return findingMapper.toResponse(findingRepository.save(finding));
    }

    private Insight selectCanonicalInsight(List<Insight> insights, MaintenanceFindingIssueType issueType) {
        switch (issueType) {
            case TRUSTED_KNOWLEDGE_EXACT_DUPLICATE:
                return insights.get(0);

            case TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE:
            case TRUSTED_KNOWLEDGE_OVERLAP_REVIEW:
            default:
                return insights.stream()
                        .max(Comparator.comparingInt(this::calculateRichnessScore))
                        .orElse(insights.get(0));
        }
    }

    private int calculateRichnessScore(Insight insight) {
        int score = 0;
        if (insight.getSourceType() != null && !insight.getSourceType().isBlank()) score += 4;
        if (insight.getRationale() != null && !insight.getRationale().isBlank()) score += 3;
        if (insight.getConfidence() != null) score += 1;
        if (insight.getEvidenceReferences() != null && !insight.getEvidenceReferences().isEmpty()) score += 2;
        if (insight.getContent() != null) {
            score += Math.min(3, insight.getContent().length() / 80);
        }
        return score;
    }

    private boolean enrichCanonicalIfNeeded(Insight canonical, List<Insight> allInsights) {
        boolean contentChanged = false;

        if (canonical.getStatus() != InsightStatus.ACTIVE) {
            log.warn("Canonical insight is not ACTIVE (status={}), skipping enrichment", canonical.getStatus());
            return false;
        }

        if (allInsights.size() <= 1) {
            return false;
        }

        List<Insight> others = allInsights.stream()
                .filter(i -> !i.getId().equals(canonical.getId()))
                .toList();

        boolean hasRicher = others.stream()
                .anyMatch(o -> calculateRichnessScore(o) > calculateRichnessScore(canonical));

        if (hasRicher) {
            String mergedContent = mergeContent(allInsights, canonical);
            String mergedRationale = mergeRationale(allInsights, canonical);

            if (mergedContent != null && !mergedContent.isBlank()) {
                InsightResponse updated = insightService.updateInsight(canonical.getId(), mergedContent, canonical.getRationale());
                contentChanged = true;
                log.info("Updated canonical insight {} with merged content, rationale: {}", canonical.getId(), updated.rationale());
            }
            if (mergedRationale != null && !mergedRationale.isBlank() && mergedContent == null) {
                InsightResponse updated = insightService.updateInsight(canonical.getId(), canonical.getContent(), mergedRationale);
                contentChanged = true;
                log.info("Updated canonical insight {} with merged rationale", canonical.getId());
            }
        }

        return contentChanged;
    }

    private String mergeContent(List<Insight> allInsights, Insight canonical) {
        List<String> contentParts = allInsights.stream()
                .map(Insight::getContent)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        if (contentParts.isEmpty()) {
            return null;
        }

        if (contentParts.size() == 1) {
            return contentParts.get(0);
        }

        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < contentParts.size(); i++) {
            if (i > 0) {
                merged.append(" | ");
            }
            merged.append(contentParts.get(i));
        }
        return merged.toString();
    }

    private String mergeRationale(List<Insight> allInsights, Insight canonical) {
        List<String> rationaleParts = allInsights.stream()
                .map(Insight::getRationale)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        if (rationaleParts.isEmpty()) {
            return null;
        }

        if (rationaleParts.size() == 1) {
            return rationaleParts.get(0);
        }

        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < rationaleParts.size(); i++) {
            if (i > 0) {
                merged.append("; ");
            }
            merged.append(rationaleParts.get(i));
        }
        return merged.toString();
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
