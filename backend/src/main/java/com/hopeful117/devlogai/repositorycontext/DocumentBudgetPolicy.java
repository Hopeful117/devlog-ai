package com.hopeful117.devlogai.repositorycontext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for repository document body retrieval budget.
 * All values externally configurable per ADR-063 §42.7.
 */
@Component
public class DocumentBudgetPolicy {

    public static final String POLICY_ID = "document-body-budget-v1";

    private final int maxSelectedDocuments;
    private final int maxCharactersPerDocument;
    private final int maxTotalCharacters;

    public DocumentBudgetPolicy(
            @Value("${devlog.repository-context.documents.max-selected:5}") int maxSelectedDocuments,
            @Value("${devlog.repository-context.documents.max-characters-per-document:4000}") int maxCharactersPerDocument,
            @Value("${devlog.repository-context.documents.max-total-characters:12000}") int maxTotalCharacters
    ) {
        if (maxSelectedDocuments < 1) throw new IllegalArgumentException("maxSelectedDocuments must be >= 1");
        if (maxCharactersPerDocument < 1) throw new IllegalArgumentException("maxCharactersPerDocument must be >= 1");
        if (maxTotalCharacters < 1) throw new IllegalArgumentException("maxTotalCharacters must be >= 1");
        this.maxSelectedDocuments = maxSelectedDocuments;
        this.maxCharactersPerDocument = maxCharactersPerDocument;
        this.maxTotalCharacters = maxTotalCharacters;
    }

    public int maxSelectedDocuments() { return maxSelectedDocuments; }
    public int maxCharactersPerDocument() { return maxCharactersPerDocument; }
    public int maxTotalCharacters() { return maxTotalCharacters; }
}
