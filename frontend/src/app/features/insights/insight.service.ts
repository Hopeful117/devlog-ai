import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { InsightDetail, InsightSeverity, InsightSummary, InsightType } from './insight.models';
@Injectable({ providedIn: 'root' })
export class InsightService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1/insights`;
  getInsightsByAnalysis(id: string): Observable<readonly InsightSummary[]> {
    return this.http.get<readonly InsightSummary[]>(
      `${this.base}/analysis/${encodeURIComponent(id)}`,
    );
  }
  getInsightsByProject(
    id: string,
    type?: InsightType,
    severity?: InsightSeverity,
  ): Observable<readonly InsightSummary[]> {
    const suffix =
      type && severity
        ? `/type/${type}/severity/${severity}`
        : type
          ? `/type/${type}`
          : severity
            ? `/severity/${severity}`
            : '';
    return this.http.get<readonly InsightSummary[]>(
      `${this.base}/project/${encodeURIComponent(id)}${suffix}`,
    );
  }
  getInsight(id: string): Observable<InsightDetail> {
    return this.http.get<InsightDetail>(`${this.base}/${encodeURIComponent(id)}`);
  }
}
