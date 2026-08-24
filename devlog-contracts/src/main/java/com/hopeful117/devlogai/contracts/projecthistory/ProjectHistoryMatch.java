package com.hopeful117.devlogai.contracts.projecthistory;

/**
 * Why a commit matched the search. {@code matchedValue} is a bounded excerpt
 * of the actual matching content (message or path), never a diff.
 */
public record ProjectHistoryMatch(

        ProjectHistoryMatchedOn matchedOn,
        String matchedValue
) {
}
