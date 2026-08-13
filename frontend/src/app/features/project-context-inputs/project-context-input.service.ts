import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  CreateProjectHumanContextInputRequest,
  ProjectHumanContextInput,
} from './project-context-input.models';

@Injectable({ providedIn: 'root' })
export class ProjectContextInputService {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(APP_ENVIRONMENT);
  private readonly projectsUrl = `${this.environment.backendBaseUrl}/api/v1/projects`;

  getByProject(projectId: string): Observable<readonly ProjectHumanContextInput[]> {
    return this.http.get<readonly ProjectHumanContextInput[]>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/context-inputs`,
    );
  }

  create(
    projectId: string,
    request: CreateProjectHumanContextInputRequest,
  ): Observable<ProjectHumanContextInput> {
    return this.http.post<ProjectHumanContextInput>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/context-inputs`,
      request,
    );
  }

  archive(projectId: string, inputId: string): Observable<ProjectHumanContextInput> {
    return this.http.patch<ProjectHumanContextInput>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/context-inputs/${encodeURIComponent(inputId)}/archive`,
      {},
    );
  }
}
