package com.hopeful117.devlogai.storybriefing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryChangeBriefingTest {
    @Test
    void exposesTypedRepositoryEvidenceAndNonCausalStatus() {
        var reference = new AiReference(AiReferenceType.REPOSITORY_EVIDENCE,
                "git:source:0123456789012345678901234567890123456789",
                AiReferenceScope.REPOSITORY);
        var briefing = new StoryChangeBriefing("story-change-briefing-v1", UUID.randomUUID(),
                "context-digest", "projection-digest", "NOT_ESTABLISHED", "observed",
                List.of(new StoryChangeBriefing.Change(reference, "COMMIT", "summary", null, null)),
                List.of(), null);
        assertEquals(AiReferenceType.REPOSITORY_EVIDENCE, briefing.changes().getFirst().reference().type());
        assertEquals(AiReferenceScope.REPOSITORY, briefing.changes().getFirst().reference().scope());
    }

    @Test
    void rejectsCausalStatus() {
        assertThrows(IllegalArgumentException.class, () -> new StoryChangeBriefing(
                "story-change-briefing-v1", UUID.randomUUID(), "context", "projection",
                "ESTABLISHED", "causal", List.of(), List.of(), null));
    }
}
