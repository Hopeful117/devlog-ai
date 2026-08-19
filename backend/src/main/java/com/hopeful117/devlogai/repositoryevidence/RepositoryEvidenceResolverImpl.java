package com.hopeful117.devlogai.repositoryevidence;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.shared.evidence.EvidencePathValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RepositoryEvidenceResolverImpl implements RepositoryEvidenceResolver {

    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<RepositoryEvidenceProjection> resolve(Insight insight) {
        Objects.requireNonNull(insight, "insight must not be null");
        var analysis = insight.getAnalysis();
        if (analysis == null || analysis.getId() == null) {
            throw new RepositoryEvidenceResolutionException(RepositoryEvidenceResolutionException.Reason.LINEAGE_UNAVAILABLE,
                    "Insight has no Analysis");
        }
        ValidatableProposal proposal = insight.getProposal();
        if (proposal == null) {
            return Optional.empty(); // genuine no-lineage → legacy fallback allowed
        }

        UUID analysisId = analysis.getId();

        // --- declare and deduplicate IDs ------------------------------------
        List<UUID> declaredFactIds = proposedList(proposal.getSupportingFactIds());
        List<UUID> declaredObsIds = proposedList(proposal.getSupportingObservationIds());

        boolean noDirect = declaredFactIds.isEmpty();
        boolean noObservations = declaredObsIds.isEmpty();

        if (noDirect && noObservations) {
            return Optional.empty(); // genuine no-lineage → legacy fallback allowed
        }

        // collect direct Facts
        Map<UUID, Fact> mergedFacts = new TreeMap<>(Comparator.comparing(UUID::toString));

        if (!noDirect) {
            Set<UUID> distinctDirect = deduplicate(declaredFactIds);
            List<Fact> directFacts = factRepository.findByAnalysisIdAndIdIn(analysisId, distinctDirect);
            requireComplete(analysisId, distinctDirect, directFacts, "declared supporting fact");
            for (Fact f : directFacts) {
                requireSameAnalysis(analysisId, f.getAnalysis(), "supporting fact " + f.getId());
                mergeFact(mergedFacts, f);
            }
        }

        // collect observation-derived Facts
        if (!noObservations) {
            Set<UUID> distinctObs = deduplicate(declaredObsIds);
            List<Observation> observations =
                    observationRepository.findByAnalysisIdAndIdIn(analysisId, distinctObs);
            requireComplete(analysisId, distinctObs, observations, "declared supporting observation");
            for (Observation o : observations) {
                requireSameAnalysis(analysisId, o.getAnalysis(), "supporting observation " + o.getId());
                Set<Fact> supportingFacts = o.getSupportingFacts();
                if (supportingFacts == null || supportingFacts.isEmpty()) {
                    throw new RepositoryEvidenceResolutionException(RepositoryEvidenceResolutionException.Reason.DATA_INTEGRITY_ERROR,
                            "Observation " + o.getId() + " declares no supporting facts");
                }
                for (Fact f : supportingFacts) {
                    requireSameAnalysis(analysisId, f.getAnalysis(),
                            "observation-derived supporting fact " + f.getId());
                    mergeFact(mergedFacts, f);
                }
            }
        }

        // --- Option E path classification (deterministic ordering) -----------
        List<ResolvedFileEvidence> resolved = new ArrayList<>();
        Set<String> usedPaths = new LinkedHashSet<>();

        // facts in the merged map are already sorted by id asc (TreeMap)
        for (Fact fact : mergedFacts.values()) {
            // evidenceReferences on Fact is @ElementCollection default EAGER
            for (String ref : fact.getEvidenceReferences()) {
                String normalized = EvidencePathValidator.normalize(ref);
                if (normalized.isEmpty()) continue;
                if (EvidencePathValidator.hasNonFileNamespacePrefix(ref)) continue;
                if (!EvidencePathValidator.isValidRelativePath(ref)) continue;
                if (!usedPaths.add(normalized)) continue; // dedupe by path, first wins
                resolved.add(new ResolvedFileEvidence(fact.getId(), normalized));
            }
        }

        // sort by path for deterministic overall output
        resolved.sort(Comparator.comparing(ResolvedFileEvidence::path));

        return Optional.of(new RepositoryEvidenceProjection(
                analysis.getSelectedSource(),
                analysis.getTargetRevision(),
                resolved));
    }

    // ---- helpers --------------------------------------------------------

    private List<UUID> proposedList(List<UUID> ids) {
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    private Set<UUID> deduplicate(List<UUID> ids) {
        Set<UUID> result = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id == null) {
                throw new RepositoryEvidenceResolutionException(RepositoryEvidenceResolutionException.Reason.LINEAGE_UNAVAILABLE,
                        "Declared lineage contains null id");
            }
            result.add(id);
        }
        return result;
    }

    private void requireComplete(UUID analysisId, Set<UUID> declaredIds,
                                 List<?> found, String kind) {
        if (found.size() != declaredIds.size()) {
            throw new RepositoryEvidenceResolutionException(RepositoryEvidenceResolutionException.Reason.LINEAGE_UNAVAILABLE,
                    "Incomplete " + kind + " for analysis " + analysisId
                            + ": found " + found.size() + ", declared " + declaredIds.size());
        }
    }

    private void requireSameAnalysis(UUID analysisId, Analysis factAnalysis, String label) {
        if (factAnalysis == null || factAnalysis.getId() == null) {
            throw new RepositoryEvidenceResolutionException(RepositoryEvidenceResolutionException.Reason.DATA_INTEGRITY_ERROR,
                    label + " has no Analysis reference");
        }
        if (!analysisId.equals(factAnalysis.getId())) {
            throw new RepositoryEvidenceResolutionException(RepositoryEvidenceResolutionException.Reason.DATA_INTEGRITY_ERROR,
                    label + " belongs to different analysis: " + factAnalysis.getId()
                            + " (expected " + analysisId + ")");
        }
    }

    private void mergeFact(Map<UUID, Fact> byId, Fact newFact) {
        UUID id = newFact.getId();
        Fact existing = byId.get(id);
        if (existing == null) {
            byId.put(id, newFact);
        } else {
            // same id present: verify equality of key fields to detect cross-analysis
            // or data integrity issues
            if (!existing.getId().equals(newFact.getId())
                    || !Objects.equals(existing.getType(), newFact.getType())
                    || !Objects.equals(existing.getContent(), newFact.getContent())
                    || !Objects.equals(existing.getSource(), newFact.getSource())
                    || !Objects.equals(existing.getFingerprint(), newFact.getFingerprint())) {
                throw new RepositoryEvidenceResolutionException(RepositoryEvidenceResolutionException.Reason.DATA_INTEGRITY_ERROR,
                        "Duplicate Fact id " + id + " with inconsistent data in analysis");
            }
            // if identical, keep existing (idempotent)
        }
    }
}