package com.hopeful117.devlogai.contracts.engineeringcontext;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;

import java.util.List;

public record EngineeringContext(

        ProjectContext project,
        String intent,
        List<EngineeringEvidence> evidence,
        EngineeringContextMetadata metadata,
        List<ContextSection> sections,
        ContextRequestEcho requestEcho
) {
}