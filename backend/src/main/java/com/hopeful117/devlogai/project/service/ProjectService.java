package com.hopeful117.devlogai.project.service;

import com.hopeful117.devlogai.project.dto.request.CreateProjectRequest;
import com.hopeful117.devlogai.project.dto.request.UpdateProjectRequest;
import com.hopeful117.devlogai.project.dto.response.ProjectResponse;
import com.hopeful117.devlogai.project.entity.Project;

import java.util.List;

public interface ProjectService {
   ProjectResponse create(CreateProjectRequest request);

   ProjectResponse getBySlug(String slug);

   List<ProjectResponse> getAll();

   ProjectResponse update(String slug, UpdateProjectRequest request);

   void archive(String slug);

}
