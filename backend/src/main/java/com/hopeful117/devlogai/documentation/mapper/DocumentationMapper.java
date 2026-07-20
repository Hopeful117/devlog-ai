package com.hopeful117.devlogai.documentation.mapper;

import com.hopeful117.devlogai.documentation.dto.request.CreateDocumentationRequest;
import com.hopeful117.devlogai.documentation.dto.response.DocumentationResponse;
import com.hopeful117.devlogai.documentation.entity.Documentation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentationMapper {
    @Mapping(
            target = "projectId",
            source = "project.id"
    )
    DocumentationResponse toResponse(
            Documentation documentation
    );


    Documentation toEntity(
            CreateDocumentationRequest request
    );

}
