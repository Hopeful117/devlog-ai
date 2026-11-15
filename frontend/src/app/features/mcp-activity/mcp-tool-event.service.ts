import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { McpToolEventPage } from './mcp-tool-event.models';

@Injectable({ providedIn: 'root' })
export class McpToolEventService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;

  byProject(projectId: string, page = 0, size = 20): Observable<McpToolEventPage> {
    return this.http.get<McpToolEventPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/mcp-tool-events?page=${page}&size=${size}`,
    );
  }
}
