import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { DecisionDetail } from './decision.models';

@Injectable({ providedIn: 'root' })
export class DecisionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1/decisions`;

  getDecision(id: string): Observable<DecisionDetail> {
    return this.http.get<DecisionDetail>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }
}
