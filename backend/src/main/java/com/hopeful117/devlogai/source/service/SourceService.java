package com.hopeful117.devlogai.source.service;

import com.hopeful117.devlogai.source.dto.request.CreateSourceRequest;
import com.hopeful117.devlogai.source.dto.response.SourceResponse;

import java.util.List;
import java.util.UUID;

public interface SourceService {

    SourceResponse create(CreateSourceRequest request);

    SourceResponse getById(UUID id);

    List<SourceResponse> getByProject(UUID projectId);

    SourceResponse setActive(UUID id, boolean active);
}
