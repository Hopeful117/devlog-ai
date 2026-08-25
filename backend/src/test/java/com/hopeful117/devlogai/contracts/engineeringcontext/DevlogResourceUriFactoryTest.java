package com.hopeful117.devlogai.contracts.engineeringcontext;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for the single source of truth of the DevLog MCP resource URI
 * space (the class lives in devlog-contracts; tests run from the backend
 * module which depends on it).
 */
class DevlogResourceUriFactoryTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String SHA =
            "3cd3723206eae38d518eb696a1dd50c0476264d0";

    @Test
    void shouldBuildEveryArtifactUri() {
        assertThat(DevlogResourceUriFactory.projects())
                .isEqualTo("devlog://projects");
        assertThat(DevlogResourceUriFactory.decision(SLUG, ID))
                .isEqualTo("devlog://projects/devlog-ai/decisions/" + ID);
        assertThat(DevlogResourceUriFactory.insight(SLUG, ID))
                .isEqualTo("devlog://projects/devlog-ai/insights/" + ID);
        assertThat(DevlogResourceUriFactory.story(SLUG, ID))
                .isEqualTo("devlog://projects/devlog-ai/stories/" + ID);
        assertThat(DevlogResourceUriFactory.engineeringEvent(SLUG, ID))
                .isEqualTo("devlog://projects/devlog-ai/engineering-events/" + ID);
        assertThat(DevlogResourceUriFactory.commit(SLUG, SHA))
                .isEqualTo("devlog://projects/devlog-ai/commits/" + SHA);
        assertThat(DevlogResourceUriFactory.freshness(SLUG))
                .isEqualTo("devlog://projects/devlog-ai/freshness");
    }

    @Test
    void shouldNormalizeUuidAndShaCase() {
        String upperSha = SHA.toUpperCase();
        assertThat(DevlogResourceUriFactory.commit(SLUG, upperSha))
                .isEqualTo("devlog://projects/devlog-ai/commits/" + SHA);
        assertThat(DevlogResourceUriFactory.decision(SLUG,
                UUID.fromString(ID.toString().toUpperCase())))
                .endsWith("/decisions/" + ID);
    }

    @Test
    void shouldAccept64CharacterSha() {
        String sha256style = "a".repeat(64);
        assertThat(DevlogResourceUriFactory.commit(SLUG, sha256style))
                .endsWith("/commits/" + sha256style);
    }

    @Test
    void shouldRejectInvalidInputs() {
        assertThatThrownBy(() -> DevlogResourceUriFactory.decision(null, ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DevlogResourceUriFactory.decision("bad slug!", ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DevlogResourceUriFactory.decision(SLUG, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DevlogResourceUriFactory.commit(SLUG, "abc123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DevlogResourceUriFactory.commit(SLUG, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
