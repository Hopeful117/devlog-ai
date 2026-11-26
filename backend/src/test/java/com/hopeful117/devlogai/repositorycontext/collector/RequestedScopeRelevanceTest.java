package com.hopeful117.devlogai.repositorycontext.collector;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestedScopeRelevanceTest {

    private static final String QUESTION = "engineering-story-context-analysis How does a Story Context request "
            + "move from Core authorization to the final persisted analysis, and which side owns each invariant? "
            + "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis ai-engine/app/services";

    @Test
    void queryKeepsQuestionTermsAndIgnoresStopWordsAndRequestedPaths() {
        Set<String> query = RequestedScopeRelevance.queryStems(QUESTION);

        assertTrue(query.containsAll(Set.of("autho", "persi", "invar", "analy", "reque", "story", "conte")));
        assertFalse(query.contains("does"));
        assertFalse(query.contains("which"));
        assertFalse(query.contains("backe"), "requested paths are scope, not intent");
        assertFalse(query.contains("servi"), "requested paths are scope, not intent");
    }

    @Test
    void closeWordFormsShareAStem() {
        assertTrue(RequestedScopeRelevance.stems("analyze persistAnalysis", true)
                .containsAll(RequestedScopeRelevance.stems("analysis persisted", false)));
    }

    @Test
    void fileNameAndContentIdentifiersRaiseRelevance() {
        Set<String> query = RequestedScopeRelevance.queryStems(QUESTION);
        String useCase = "class AnalyzeStoryContextUseCase { void authorizePrincipal() {} void persistResult() {} "
                + "void validateInvariant(Request request) {} }";
        String controller = "class InvestigationBudget { int steps; }";

        int useCaseScore = RequestedScopeRelevance.score(
                "backend/x/usecase/AnalyzeStoryContextUseCase.java", query, useCase);
        int unrelatedScore = RequestedScopeRelevance.score(
                "backend/x/investigation/InvestigationBudget.java", query, controller);

        assertTrue(useCaseScore > unrelatedScore);
        assertEquals(0, RequestedScopeRelevance.score("a/B.java", Set.of(), "anything"));
        assertTrue(useCaseScore <= RequestedScopeRelevance.MAX_SCORE);
    }

    @Test
    void scoringIsDeterministic() {
        Set<String> query = RequestedScopeRelevance.queryStems(QUESTION);
        String content = "persist authorize request";

        assertEquals(RequestedScopeRelevance.score("a/StoryContextCallbackService.java", query, content),
                RequestedScopeRelevance.score("a/StoryContextCallbackService.java", query, content));
    }
}
