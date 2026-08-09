import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  CreateProjectRequest,
  ProjectDetail,
  ProjectSummary,
  UpdateProjectRequest,
} from './project.models';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(APP_ENVIRONMENT);
  private readonly projectsUrl = `${this.environment.backendBaseUrl}/api/v1/projects`;

  getProjects(): Observable<readonly ProjectSummary[]> {
    return this.http.get<readonly ProjectSummary[]>(this.projectsUrl);
  }

  getProject(identifier: string): Observable<ProjectDetail> {
    return this.http.get<ProjectDetail>(`${this.projectsUrl}/${encodeURIComponent(identifier)}`);
  }

  createProject(request: CreateProjectRequest): Observable<ProjectDetail> {
    return this.http.post<ProjectDetail>(this.projectsUrl, request);
  }

  updateProject(identifier: string, request: UpdateProjectRequest): Observable<ProjectDetail> {
    return this.http.put<ProjectDetail>(
      `${this.projectsUrl}/${encodeURIComponent(identifier)}`,
      request,
    );
  }

  deleteProject(identifier: string): Observable<void> {
    return this.http.delete<void>(`${this.projectsUrl}/${encodeURIComponent(identifier)}`);
  }
}
