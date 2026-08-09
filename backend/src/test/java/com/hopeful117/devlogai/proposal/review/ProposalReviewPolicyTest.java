package com.hopeful117.devlogai.proposal.review;

import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProposalReviewPolicyTest {
    private final ProposalReviewPolicy policy = new ProposalReviewPolicy();

    @Test
    void shouldUseConfiguredDefaultAndAcceptMaximumPageSize() {
        policy.setDefaultPageSize(7);
        policy.setMaxPageSize(20);

        assertEquals(7, policy.effectiveSize(null));
        assertEquals(20, policy.effectiveSize(20));
    }

    @Test
    void shouldRejectPageSizesOutsideTheBoundedContract() {
        assertThrows(InvalidParameterException.class, () -> policy.effectiveSize(0));
        assertThrows(InvalidParameterException.class, () -> policy.effectiveSize(21));
    }

    @Test
    void shouldRejectAnInvalidConfiguredRange() {
        policy.setDefaultPageSize(20);
        policy.setMaxPageSize(10);

        assertThrows(IllegalStateException.class, policy::validate);
    }
}
