import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { AgentSessionPage } from './agent-session.models';
import { McpOperationPage } from './mcp-operation.models';

@Injectable({ providedIn: 'root' })
export class AgentSessionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;

  byProject(projectId: string, page = 0, size = 20): Observable<AgentSessionPage> {
    return this.http.get<AgentSessionPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/agent-sessions?page=${page}&size=${size}`,
    );
  }

  operationsBySession(
    projectId: string,
    sessionId: string,
    page = 0,
    size = 20,
  ): Observable<McpOperationPage> {
    return this.http.get<McpOperationPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/agent-sessions/${encodeURIComponent(sessionId)}/operations?page=${page}&size=${size}`,
    );
  }
}
