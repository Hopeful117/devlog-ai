package com.hopeful117.devlogai.storycontextanalysis.investigation;

public record InvestigationActionValidation(boolean accepted, String code, String message) {
    public static InvestigationActionValidation success() {
        return new InvestigationActionValidation(true, null, null);
    }

    public static InvestigationActionValidation rejected(String code, String message) {
        return new InvestigationActionValidation(false, code, message);
    }
}
