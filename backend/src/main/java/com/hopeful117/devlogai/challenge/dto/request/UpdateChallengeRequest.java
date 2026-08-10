package com.hopeful117.devlogai.challenge.dto.request;

import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChallengeRequest {

    private String title;

    private String description;

    private String impact;

    private ChallengeStatus status;

    private String resolution;
}
