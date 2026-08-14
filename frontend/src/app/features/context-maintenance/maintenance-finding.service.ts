import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { MaintenanceFinding } from './maintenance-finding.models';

@Injectable({ providedIn: 'root' })
export class MaintenanceFindingService {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(APP_ENVIRONMENT);
  private readonly projectsUrl = `${this.environment.backendBaseUrl}/api/v1/projects`;

  getByProject(projectId: string): Observable<readonly MaintenanceFinding[]> {
    return this.http.get<readonly MaintenanceFinding[]>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings`,
    );
  }
}
