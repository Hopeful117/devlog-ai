import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ProjectFreshnessResponse } from './project-freshness.models';

@Injectable({ providedIn: 'root' })
export class ProjectFreshnessService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(APP_ENVIRONMENT).backendBaseUrl;

  getLatest(projectId: string, sourceId: string): Observable<ProjectFreshnessResponse | null> {
    return this.http.get<ProjectFreshnessResponse | null>(
      `${this.baseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/freshness-checks/latest`,
      { params: new HttpParams().set('sourceId', sourceId) },
    );
  }

  check(projectId: string, sourceId: string): Observable<ProjectFreshnessResponse> {
    return this.http.post<ProjectFreshnessResponse>(
      `${this.baseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/freshness-checks`,
      { sourceId },
    );
  }
}
