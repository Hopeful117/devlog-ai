package com.hopeful117.devlogai.repositorycontext;

/**
 * V1 document status model for repository documents.
 * Status describes authority/currentness, not relevance ranking.
 */
public enum DocumentStatus {
    ACCEPTED,
    PROPOSED,
    UNKNOWN,
    SUPERSEDED,
    REJECTED
}
