package com.hopeful117.devlogai.decision.dto.response;


import java.time.Instant;
import java.util.UUID;


public record DecisionResponse(

        UUID id,

        UUID projectId,

        UUID proposalId,

        String title,

        String context,

        String choice,

        String rationale,

        String consequences,

        Instant createdAt,

        Instant updatedAt


)


{
}
