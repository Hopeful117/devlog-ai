import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  CreateSourceRequest,
  SourceDetail,
  SourceSummary,
  UpdateSourceActivationRequest,
} from './source.models';

@Injectable({ providedIn: 'root' })
export class SourceService {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(APP_ENVIRONMENT);
  private readonly sourcesUrl = `${this.environment.backendBaseUrl}/api/v1/sources`;

  getSourcesByProject(projectId: string): Observable<readonly SourceSummary[]> {
    return this.http.get<readonly SourceSummary[]>(
      `${this.sourcesUrl}/project/${encodeURIComponent(projectId)}`,
    );
  }

  getSource(sourceId: string): Observable<SourceDetail> {
    return this.http.get<SourceDetail>(`${this.sourcesUrl}/${encodeURIComponent(sourceId)}`);
  }

  createSource(request: CreateSourceRequest): Observable<SourceDetail> {
    return this.http.post<SourceDetail>(this.sourcesUrl, request);
  }

  setSourceActive(sourceId: string, active: boolean): Observable<SourceDetail> {
    const request: UpdateSourceActivationRequest = { active };
    return this.http.patch<SourceDetail>(
      `${this.sourcesUrl}/${encodeURIComponent(sourceId)}/activation`,
      request,
    );
  }
}
