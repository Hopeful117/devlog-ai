import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ActivityIncidentPage } from './activity-incident.models';

@Injectable({ providedIn: 'root' })
export class ActivityIncidentService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;

  byProject(
    projectId: string,
    windowMinutes = 60,
    page = 0,
    size = 20,
  ): Observable<ActivityIncidentPage> {
    return this.http.get<ActivityIncidentPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/activity-incidents?windowMinutes=${windowMinutes}&page=${page}&size=${size}`,
    );
  }
}
