package com.hopeful117.devlogai.repositorycontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentBudgetPolicyTest {

    @Test
    void defaultPolicyHasCorrectLimits() {
        DocumentBudgetPolicy policy = new DocumentBudgetPolicy(5, 4000, 12000);

        assertEquals(5, policy.maxSelectedDocuments());
        assertEquals(4000, policy.maxCharactersPerDocument());
        assertEquals(12000, policy.maxTotalCharacters());
    }

    @Test
    void rejectsZeroMaxSelected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentBudgetPolicy(0, 4000, 12000));
    }

    @Test
    void rejectsZeroMaxCharsPerDoc() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentBudgetPolicy(5, 0, 12000));
    }

    @Test
    void rejectsZeroMaxTotalChars() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentBudgetPolicy(5, 4000, 0));
    }

    @Test
    void acceptsMinimumValues() {
        DocumentBudgetPolicy policy = new DocumentBudgetPolicy(1, 1, 1);

        assertEquals(1, policy.maxSelectedDocuments());
        assertEquals(1, policy.maxCharactersPerDocument());
        assertEquals(1, policy.maxTotalCharacters());
    }
}
