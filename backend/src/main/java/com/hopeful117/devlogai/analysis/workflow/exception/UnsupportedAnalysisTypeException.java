package com.hopeful117.devlogai.analysis.workflow.exception;

import com.hopeful117.devlogai.analysis.entity.AnalysisType;

public class UnsupportedAnalysisTypeException extends RuntimeException {

    public UnsupportedAnalysisTypeException(AnalysisType analysisType) {
        super("Analysis type is not supported by the V1 AI workflow: " + analysisType);
    }
}
