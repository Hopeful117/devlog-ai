package com.hopeful117.devlogai.engineeringevent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitCommitIdentityTest {
    @Test
    void acceptsCompleteSha1AndSha256AndNormalizesCase() {
        assertEquals("a".repeat(40), GitCommitIdentity.normalize("A".repeat(40)).orElseThrow());
        assertEquals("b".repeat(64), GitCommitIdentity.normalize("b".repeat(64)).orElseThrow());
    }

    @Test
    void rejectsAbbreviatedNonHexAndBlankCommitIdentities() {
        assertTrue(GitCommitIdentity.normalize("a".repeat(39)).isEmpty());
        assertTrue(GitCommitIdentity.normalize("z".repeat(40)).isEmpty());
        assertTrue(GitCommitIdentity.normalize(" ").isEmpty());
    }
}
