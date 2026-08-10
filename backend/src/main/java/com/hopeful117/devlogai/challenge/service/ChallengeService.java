package com.hopeful117.devlogai.challenge.service;

import com.hopeful117.devlogai.challenge.dto.request.CreateChallengeRequest;
import com.hopeful117.devlogai.challenge.dto.request.UpdateChallengeRequest;
import com.hopeful117.devlogai.challenge.dto.response.ChallengeResponse;

import java.util.List;
import java.util.UUID;

public interface ChallengeService {

    ChallengeResponse create(CreateChallengeRequest request);

    ChallengeResponse getById(UUID id);

    List<ChallengeResponse> getByProject(UUID projectId);

    ChallengeResponse update(UUID id, UpdateChallengeRequest request);
}
