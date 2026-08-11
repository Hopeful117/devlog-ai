import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  EngineeringEvent,
  EngineeringEventExecution,
  EngineeringEventPage,
} from './engineering-event.models';

@Injectable({ providedIn: 'root' })
export class EngineeringEventService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;
  execute(
    projectId: string,
    sourceId: string,
    targetCommit: string,
  ): Observable<EngineeringEventExecution> {
    return this.http.post<EngineeringEventExecution>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/engineering-event-executions`,
      { sourceId, targetCommit },
    );
  }
  byProject(projectId: string, page = 0, size = 20): Observable<EngineeringEventPage> {
    return this.http.get<EngineeringEventPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/engineering-events?page=${page}&size=${size}`,
    );
  }
  get(id: string): Observable<EngineeringEvent> {
    return this.http.get<EngineeringEvent>(
      `${this.base}/engineering-events/${encodeURIComponent(id)}`,
    );
  }
}
