package com.hopeful117.devlogai.repositorycontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdrStatusParserTest {

    private final AdrStatusParser parser = new AdrStatusParser();

    @Test
    void extractsAcceptedStatus() {
        String markdown = """
                # ADR-001: Use PostgreSQL

                ## Status

                **ACCEPTED**

                ## Context

                We need a database.
                """;

        AdrStatusParser.AdrStatusResult result = parser.parse(markdown);

        assertEquals(DocumentStatus.ACCEPTED, result.status());
        assertNull(result.supersededBy());
    }

    @Test
    void extractsProposedStatus() {
        String markdown = """
                # ADR-002: Use Redis

                ## Status

                **PROPOSED**

                ## Context

                We need caching.
                """;

        AdrStatusParser.AdrStatusResult result = parser.parse(markdown);

        assertEquals(DocumentStatus.PROPOSED, result.status());
        assertNull(result.supersededBy());
    }

    @Test
    void extractsSupersededStatus() {
        String markdown = """
                # ADR-003: Use MySQL

                ## Status

                **SUPERSEDED** by [ADR-005]

                ## Context

                Old database choice.
                """;

        AdrStatusParser.AdrStatusResult result = parser.parse(markdown);

        assertEquals(DocumentStatus.SUPERSEDED, result.status());
        assertEquals("ADR-005", result.supersededBy());
    }

    @Test
    void extractsRejectedStatus() {
        String markdown = """
                # ADR-004: Use SQLite

                ## Status

                **REJECTED**

                ## Context

                Too simple for our needs.
                """;

        AdrStatusParser.AdrStatusResult result = parser.parse(markdown);

        assertEquals(DocumentStatus.REJECTED, result.status());
        assertNull(result.supersededBy());
    }

    @Test
    void returnsUnknownWhenNoStatusHeading() {
        String markdown = """
                # ADR-005: Use MongoDB

                ## Context

                We need a NoSQL database.
                """;

        AdrStatusParser.AdrStatusResult result = parser.parse(markdown);

        assertEquals(DocumentStatus.UNKNOWN, result.status());
        assertNull(result.supersededBy());
    }

    @Test
    void returnsUnknownForNullInput() {
        AdrStatusParser.AdrStatusResult result = parser.parse(null);
        assertEquals(DocumentStatus.UNKNOWN, result.status());
    }

    @Test
    void returnsUnknownForEmptyInput() {
        AdrStatusParser.AdrStatusResult result = parser.parse("");
        assertEquals(DocumentStatus.UNKNOWN, result.status());
    }

    @Test
    void handlesCaseInsensitiveStatus() {
        String markdown = """
                # ADR-006

                ## Status

                **accepted**

                ## Context

                Case test.
                """;

        AdrStatusParser.AdrStatusResult result = parser.parse(markdown);

        assertEquals(DocumentStatus.ACCEPTED, result.status());
    }

    @Test
    void extractsSupersededByFromInline() {
        String markdown = """
                # ADR-007

                ## Status

                **SUPERSEDED** by [ADR-010] - use ADR-010 instead

                ## Context

                Old approach.
                """;

        AdrStatusParser.AdrStatusResult result = parser.parse(markdown);

        assertEquals(DocumentStatus.SUPERSEDED, result.status());
        assertEquals("ADR-010", result.supersededBy());
    }
}
