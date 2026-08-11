package com.hopeful117.devlogai.projectstate.dto.inner;

import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;

import java.util.UUID;

public record ChallengeSummary(
        UUID id,
        String title,
        ChallengeStatus status,
        String impact
) {
}
