package com.hopeful117.devlogai.challenge.mapper;

import com.hopeful117.devlogai.challenge.dto.request.CreateChallengeRequest;
import com.hopeful117.devlogai.challenge.dto.response.ChallengeResponse;
import com.hopeful117.devlogai.challenge.entity.Challenge;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChallengeMapper {

    @Mapping(target = "projectId", source = "project.id")
    ChallengeResponse toResponse(Challenge challenge);

    Challenge toEntity(CreateChallengeRequest request);
}
