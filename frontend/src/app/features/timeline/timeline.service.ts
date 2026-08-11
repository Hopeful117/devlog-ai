import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { TimelineResponse } from './timeline.models';

@Injectable({ providedIn: 'root' })
export class TimelineService {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(APP_ENVIRONMENT);

  getTimeline(projectId: string): Observable<TimelineResponse> {
    return this.http.get<TimelineResponse>(
      `${this.environment.backendBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/timeline`,
    );
  }
}
