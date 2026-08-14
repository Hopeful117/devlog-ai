import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { MaintenanceFinding, MaintenanceFindingActionRequest } from './maintenance-finding.models';

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

  acknowledge(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/acknowledgements`,
      request,
    );
  }

  dismiss(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/dismissals`,
      request,
    );
  }

  resolve(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/resolutions`,
      request,
    );
  }
}
