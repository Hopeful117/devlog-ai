package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfidenceWireContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canonicalStringDeserializesToCoreConfidenceEnum() throws Exception {
        assertEquals(
                StoryContextAnalysisResult.Confidence.HIGH,
                objectMapper.readValue("\"HIGH\"", StoryContextAnalysisResult.Confidence.class)
        );
    }

    @Test
    void objectShapedConfidenceIsRejectedAtCoreBoundary() {
        assertThrows(Exception.class, () -> objectMapper.readValue(
                "{\"level\":\"HIGH\",\"rationale\":\"bounded\"}",
                StoryContextAnalysisResult.Confidence.class
        ));
    }

    @Test
    void canonicalOutputClassificationWrapperDeserializes() throws Exception {
        StoryContextAnalysisResult.OutputClassification value = objectMapper.readValue(
                "{\"entries\":[]}", StoryContextAnalysisResult.OutputClassification.class);

        assertEquals(List.of(), value.entries());
    }

    @Test
    void arrayShapedOutputClassificationIsRejectedAtCoreBoundary() {
        assertThrows(Exception.class, () -> objectMapper.readValue(
                "[]", StoryContextAnalysisResult.OutputClassification.class));
    }
}
