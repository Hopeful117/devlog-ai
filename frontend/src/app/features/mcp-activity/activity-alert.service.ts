import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ActivityAlertPage } from './activity-alert.models';

@Injectable({ providedIn: 'root' })
export class ActivityAlertService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;

  byProject(projectId: string, page = 0, size = 20): Observable<ActivityAlertPage> {
    return this.http.get<ActivityAlertPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/activity-alerts?page=${page}&size=${size}`,
    );
  }
}
