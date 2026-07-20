package com.hopeful117.devlogai.documentation.service;

import com.hopeful117.devlogai.documentation.dto.request.CreateDocumentationRequest;
import com.hopeful117.devlogai.documentation.dto.response.DocumentationResponse;
import com.hopeful117.devlogai.documentation.entity.DocumentationType;

import java.util.List;
import java.util.UUID;

public interface DocumentationService {
    DocumentationResponse create(CreateDocumentationRequest request);

    DocumentationResponse getById(UUID id);

    List<DocumentationResponse> getByProject(UUID projectId);

    List<DocumentationResponse> getByProjectAndType(
            UUID projectId,
            DocumentationType type
    );


}
