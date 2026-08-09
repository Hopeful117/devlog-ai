package com.hopeful117.devlogai.proposal.review;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "devlog.proposal-review")
public class ProposalReviewPolicy {
    private int defaultPageSize = 10;
    private int maxPageSize = 20;
    private int maxFactsPerProposal = 10;
    private int maxObservationsPerProposal = 10;
    private int maxEvidenceReferencesPerProposal = 20;
    private int maxContentCharacters = 1_000;

    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int value) { defaultPageSize = positive(value); }
    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int value) { maxPageSize = positive(value); }
    public int getMaxFactsPerProposal() { return maxFactsPerProposal; }
    public void setMaxFactsPerProposal(int value) { maxFactsPerProposal = positive(value); }
    public int getMaxObservationsPerProposal() { return maxObservationsPerProposal; }
    public void setMaxObservationsPerProposal(int value) { maxObservationsPerProposal = positive(value); }
    public int getMaxEvidenceReferencesPerProposal() { return maxEvidenceReferencesPerProposal; }
    public void setMaxEvidenceReferencesPerProposal(int value) {
        maxEvidenceReferencesPerProposal = positive(value);
    }
    public int getMaxContentCharacters() { return maxContentCharacters; }
    public void setMaxContentCharacters(int value) { maxContentCharacters = positive(value); }
    public int effectiveSize(Integer requested) {
        int value = requested == null ? defaultPageSize : requested;
        if (value < 1 || value > maxPageSize) {
            throw new com.hopeful117.devlogai.shared.exception.InvalidParameterException(
                    "size", value);
        }
        return value;
    }
    @PostConstruct
    void validate() {
        if (defaultPageSize > maxPageSize) {
            throw new IllegalStateException(
                    "Proposal review default page size must not exceed its maximum");
        }
    }
    private int positive(int value) {
        if (value < 1) throw new IllegalArgumentException("Proposal review limit must be positive");
        return value;
    }
}
