import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { McpOperationPage } from './mcp-operation.models';

@Injectable({ providedIn: 'root' })
export class McpOperationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;

  byProject(projectId: string, page = 0, size = 20): Observable<McpOperationPage> {
    return this.http.get<McpOperationPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/mcp-operations?page=${page}&size=${size}`,
    );
  }
}
