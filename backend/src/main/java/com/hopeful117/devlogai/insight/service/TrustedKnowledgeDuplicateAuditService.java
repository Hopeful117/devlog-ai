package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.dto.response.*;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrustedKnowledgeDuplicateAuditService {
    private static final Set<String> TITLE_STOP_WORDS = Set.of(
            "the", "and", "or", "of", "for", "with", "using", "use", "project", "application", "present"
    );

    private final InsightRepository insightRepository;

    public InsightDuplicateAuditResponse audit(UUID projectId) {
        List<Insight> insights = insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId);
        List<InsightNode> nodes = insights.stream().map(InsightNode::new).toList();
        List<InsightDuplicateClusterResponse> clusters = buildClusters(nodes);
        return new InsightDuplicateAuditResponse(projectId, insights.size(), clusters.size(), clusters);
    }

    private List<InsightDuplicateClusterResponse> buildClusters(List<InsightNode> nodes) {
        List<InsightDuplicateClusterResponse> clusters = new ArrayList<>();
        Set<UUID> consumed = new HashSet<>();

        exactDuplicateGroups(nodes).forEach((key, members) -> {
            if (members.size() < 2) {
                return;
            }
            consumed.addAll(memberIds(members));
            clusters.add(new InsightDuplicateClusterResponse(
                    key,
                    InsightDuplicateClusterCategory.EXACT_DUPLICATE,
                    InsightDuplicateRecommendation.KEEP_NEWEST_AS_CANONICAL,
                    "Members share the same normalized trusted fingerprint.",
                    toMembers(members)
            ));
        });

        List<InsightNode> remaining = nodes.stream()
                .filter(node -> !consumed.contains(node.insight().getId()))
                .toList();
        clusters.addAll(topicClusters(remaining));

        return clusters.stream()
                .sorted(Comparator.comparing(InsightDuplicateClusterResponse::clusterKey))
                .toList();
    }

    private Map<String, List<InsightNode>> exactDuplicateGroups(List<InsightNode> nodes) {
        return nodes.stream().collect(Collectors.groupingBy(
                InsightNode::exactFingerprint,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private List<InsightDuplicateClusterResponse> topicClusters(List<InsightNode> nodes) {
        List<InsightDuplicateClusterResponse> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        for (InsightNode node : nodes) {
            if (!visited.add(node.insight().getId())) {
                continue;
            }
            List<InsightNode> cluster = new ArrayList<>();
            Deque<InsightNode> queue = new ArrayDeque<>();
            queue.add(node);
            cluster.add(node);

            while (!queue.isEmpty()) {
                InsightNode current = queue.removeFirst();
                for (InsightNode candidate : nodes) {
                    if (visited.contains(candidate.insight().getId())) {
                        continue;
                    }
                    if (sameTopic(current, candidate)) {
                        visited.add(candidate.insight().getId());
                        queue.add(candidate);
                        cluster.add(candidate);
                    }
                }
            }

            if (cluster.size() < 2) {
                continue;
            }
            result.add(classifyTopicCluster(cluster));
        }

        return result;
    }

    private InsightDuplicateClusterResponse classifyTopicCluster(List<InsightNode> cluster) {
        List<InsightNode> members = ordered(cluster);
        InsightNode richest = members.stream()
                .max(Comparator.comparingInt(InsightNode::richnessScore)
                        .thenComparing(InsightNode::createdAt)
                        .thenComparing(InsightNode::insightId))
                .orElseThrow();
        List<InsightNode> byRichness = members.stream()
                .sorted(Comparator.comparingInt(InsightNode::richnessScore).reversed()
                        .thenComparing(InsightNode::createdAt, Comparator.reverseOrder())
                        .thenComparing(InsightNode::insightId, Comparator.reverseOrder()))
                .toList();
        InsightNode second = byRichness.size() > 1 ? byRichness.get(1) : richest;
        boolean richerSuccessor = richest.richnessScore() >= second.richnessScore() + 2
                || (richest.hasRicherProvenanceThan(second) && !Objects.equals(richest.insightId(), second.insightId()));

        if (richerSuccessor) {
            return new InsightDuplicateClusterResponse(
                    topicClusterKey(members),
                    InsightDuplicateClusterCategory.LIKELY_RICHER_SUCCESSOR,
                    InsightDuplicateRecommendation.KEEP_RICHEST_AS_CANONICAL,
                    "Members share the same topic family, and one record is materially richer in provenance or detail.",
                    toMembers(members)
            );
        }

        Set<String> families = members.stream().map(InsightNode::familyKey).collect(Collectors.toCollection(LinkedHashSet::new));
        InsightDuplicateClusterCategory category = families.size() == 1
                ? InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE
                : InsightDuplicateClusterCategory.REVIEW_REQUIRED;
        return new InsightDuplicateClusterResponse(
                topicClusterKey(members),
                category,
                InsightDuplicateRecommendation.REVIEW_MANUALLY,
                category == InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE
                        ? "Members appear semantically close but no single richer canonical record is confidently dominant."
                        : "Members overlap but differ enough in family or provenance to require human review.",
                toMembers(members)
        );
    }

    private boolean sameTopic(InsightNode left, InsightNode right) {
        if (left.insight().getType() != right.insight().getType()) {
            return false;
        }
        Set<String> leftTokens = left.titleTokens();
        Set<String> rightTokens = right.titleTokens();
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return false;
        }
        Set<String> overlap = new LinkedHashSet<>(leftTokens);
        overlap.retainAll(rightTokens);
        if (overlap.size() >= 3) {
            return true;
        }
        if (overlap.size() < 2) {
            return false;
        }
        Set<String> union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        int unionSize = union.size();
        return (double) overlap.size() / (double) unionSize >= 0.4D;
    }

    private String topicClusterKey(List<InsightNode> members) {
        InsightNode first = members.getFirst();
        String topic = members.stream()
                .flatMap(node -> node.titleTokens().stream())
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(Map.Entry::getKey)
                .sorted()
                .limit(4)
                .collect(Collectors.joining("-"));
        if (topic.isBlank()) {
            topic = InsightPayloadSupport.normalize(first.insight().getTitle()).replace(' ', '-');
        }
        return first.familyKey() + "::" + topic;
    }

    private List<InsightDuplicateMemberResponse> toMembers(List<InsightNode> nodes) {
        return ordered(nodes).stream()
                .map(node -> new InsightDuplicateMemberResponse(
                        node.insightId(),
                        node.insight().getProposal().getId(),
                        node.insight().getType(),
                        node.insight().getSeverity(),
                        node.insight().getSourceType(),
                        node.insight().getTitle(),
                        node.insight().getContent(),
                        node.insight().getRationale(),
                        node.insight().getConfidence(),
                        node.insight().getEvidenceReferences() == null ? 0 : node.insight().getEvidenceReferences().size(),
                        node.createdAt()
                ))
                .toList();
    }

    private List<InsightNode> ordered(List<InsightNode> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparing(InsightNode::createdAt, Comparator.reverseOrder())
                        .thenComparing(InsightNode::insightId, Comparator.reverseOrder()))
                .toList();
    }

    private Set<UUID> memberIds(List<InsightNode> members) {
        return members.stream().map(InsightNode::insightId).collect(Collectors.toSet());
    }

    private record InsightNode(Insight insight) {
        UUID insightId() {
            return insight.getId();
        }

        java.time.Instant createdAt() {
            return insight.getCreatedAt();
        }

        String familyKey() {
            String sourceType = insight.getSourceType();
            if (sourceType != null && !sourceType.isBlank()) {
                return sourceType;
            }
            return fallbackSourceType(insight.getType());
        }

        String exactFingerprint() {
            return String.join("::",
                    insight.getType().name(),
                    familyKey(),
                    InsightPayloadSupport.normalize(insight.getTitle()),
                    InsightPayloadSupport.normalize(insight.getContent()),
                    InsightPayloadSupport.normalize(insight.getRationale()));
        }

        Set<String> titleTokens() {
            return Arrays.stream(InsightPayloadSupport.normalize(insight.getTitle()).split("[^a-z0-9]+"))
                    .map(this::canonicalToken)
                    .filter(token -> !token.isBlank())
                    .filter(token -> token.length() >= 4)
                    .filter(token -> !TITLE_STOP_WORDS.contains(token))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        int richnessScore() {
            int score = 0;
            if (insight.getSourceType() != null && !insight.getSourceType().isBlank()) score += 4;
            if (insight.getRationale() != null && !insight.getRationale().isBlank()) score += 3;
            if (insight.getConfidence() != null) score += 1;
            if (insight.getEvidenceReferences() != null && !insight.getEvidenceReferences().isEmpty()) score += 2;
            score += Math.min(3, InsightPayloadSupport.normalize(insight.getContent()).length() / 80);
            return score;
        }

        boolean hasRicherProvenanceThan(InsightNode other) {
            return (insight.getSourceType() != null && other.insight.getSourceType() == null)
                    || (insight.getRationale() != null && other.insight.getRationale() == null)
                    || ((insight.getEvidenceReferences() == null ? 0 : insight.getEvidenceReferences().size())
                    > (other.insight.getEvidenceReferences() == null ? 0 : other.insight.getEvidenceReferences().size()));
        }

        private String canonicalToken(String token) {
            if ("restful".equals(token)) return "rest";
            if (token.endsWith("ies") && token.length() > 4) return token.substring(0, token.length() - 3) + "y";
            if (token.endsWith("s") && token.length() > 4) return token.substring(0, token.length() - 1);
            return token;
        }

        private String fallbackSourceType(InsightType type) {
            return switch (type) {
                case ARCHITECTURAL -> "ARCHITECTURE_DESCRIPTION";
                case TECHNOLOGY -> "TECHNOLOGY_DESCRIPTION";
                case DOCUMENTATION -> "PROJECT_PRESENTATION";
                default -> type.name();
            };
        }
    }
}
