import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  ProjectUnderstandingRequest,
  ProjectUnderstandingResponse,
} from './project-understanding.models';

@Injectable({ providedIn: 'root' })
export class ProjectUnderstandingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1/projects`;

  execute(
    projectId: string,
    request: ProjectUnderstandingRequest,
  ): Observable<ProjectUnderstandingResponse> {
    return this.http.post<ProjectUnderstandingResponse>(
      `${this.baseUrl}/${encodeURIComponent(projectId)}/understanding-executions`,
      request,
    );
  }
}
