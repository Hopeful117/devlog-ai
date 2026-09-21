package com.hopeful117.devlogai.historicalrelationship;

public interface HistoricalRelationshipQuery {

    HistoricalCommitLookupResult findReferences(HistoricalCommitLookup lookup);
}
