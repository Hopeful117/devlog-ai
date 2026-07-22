package com.hopeful117.devlogai.intent.service;

import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntentCatalogTest {
    private final IntentCatalog catalog = new IntentCatalog();

    @Test
    void shouldExposeTheThreeVersionedInitialIntentsInStableOrder() {
        assertEquals(
                java.util.List.of("describe-project-v1", "generate-readme-v1", "architecture-overview-v1"),
                catalog.all().stream().map(intent -> intent.key()).toList());
    }

    @Test
    void shouldResolveImmutableIntentContract() {
        var intent = catalog.resolve("generate-readme-v1");
        assertEquals("generate-readme", intent.id());
        assertEquals("v1", intent.version());
        assertTrue(intent.supportedInsightTypes().contains(InsightType.INSTALLATION));
        assertThrows(UnsupportedOperationException.class,
                () -> intent.supportedInsightTypes().add(InsightType.API_DESCRIPTION));
    }

    @Test
    void shouldRejectUnknownOrFreeFormIntent() {
        assertThrows(EntityNotFoundException.class, () -> catalog.resolve("write-anything"));
    }
}
