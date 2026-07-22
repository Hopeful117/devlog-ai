import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { CreateDeliverableRequest, Deliverable } from './deliverable.models';

@Injectable({ providedIn: 'root' })
export class DeliverableService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1/deliverables`;

  generate(request: CreateDeliverableRequest): Observable<Deliverable> {
    return this.http.post<Deliverable>(this.baseUrl, request);
  }
  get(id: string): Observable<Deliverable> {
    return this.http.get<Deliverable>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }
  getByProject(projectId: string): Observable<readonly Deliverable[]> {
    return this.http.get<readonly Deliverable[]>(
      `${this.baseUrl}/project/${encodeURIComponent(projectId)}`,
    );
  }
}
