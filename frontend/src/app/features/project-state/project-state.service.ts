import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ProjectState } from './project-state.models';

@Injectable({ providedIn: 'root' })
export class ProjectStateService {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(APP_ENVIRONMENT);

  getProjectState(projectId: string): Observable<ProjectState> {
    return this.http.get<ProjectState>(
      `${this.environment.backendBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/state`,
    );
  }
}
