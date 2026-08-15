import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  MaintenanceEvaluationResponse,
  MaintenanceFinding,
  MaintenanceFindingActionRequest,
} from './maintenance-finding.models';

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

  evaluate(projectId: string): Observable<MaintenanceEvaluationResponse> {
    return this.http.post<MaintenanceEvaluationResponse>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/evaluations`,
      {},
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

  refreshProjection(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/refresh-projection`,
      request,
    );
  }

  archiveStaleHumanContext(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/archive-context-input`,
      request,
    );
  }

  refreshMissingProjection(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/refresh-missing-projection`,
      request,
    );
  }

  refreshProjectUnderstanding(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/refresh-understanding`,
      request,
    );
  }

  mergeDuplicate(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/merge-duplicate`,
      request,
    );
  }

  resolveSemanticDuplicate(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/resolve-semantic-duplicate`,
      request,
    );
  }

  resolveOverlapReview(
    projectId: string,
    findingId: string,
    request: MaintenanceFindingActionRequest,
  ): Observable<MaintenanceFinding> {
    return this.http.post<MaintenanceFinding>(
      `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/resolve-overlap`,
      request,
    );
  }
}
