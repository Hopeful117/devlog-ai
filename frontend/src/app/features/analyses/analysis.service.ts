import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  AnalysisDetail,
  AnalysisDiagnostics,
  AnalysisSummary,
  AnalysisWorkflowResult,
  AiTaskDetail,
  CollectionWarning,
  CreateAnalysisRequest,
  JsonValue,
  ProjectProfile,
} from './analysis.models';

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;

  getAnalysesByProject(projectId: string): Observable<readonly AnalysisSummary[]> {
    return this.http.get<readonly AnalysisSummary[]>(
      `${this.baseUrl}/analyses/project/${encodeURIComponent(projectId)}`,
    );
  }
  getAnalysis(analysisId: string): Observable<AnalysisDetail> {
    return this.http.get<AnalysisDetail>(
      `${this.baseUrl}/analyses/${encodeURIComponent(analysisId)}`,
    );
  }
  createAnalysis(request: CreateAnalysisRequest): Observable<AnalysisDetail> {
    return this.http.post<AnalysisDetail>(`${this.baseUrl}/analyses`, request);
  }
  launchAnalysis(analysisId: string): Observable<AnalysisWorkflowResult> {
    return this.http.post<AnalysisWorkflowResult>(
      `${this.baseUrl}/analyses/${encodeURIComponent(analysisId)}/workflow`,
      null,
    );
  }
  getAiTasksByAnalysis(analysisId: string): Observable<readonly AiTaskDetail[]> {
    return this.http.get<readonly AiTaskDetail[]>(
      `${this.baseUrl}/ai-tasks/analysis/${encodeURIComponent(analysisId)}`,
    );
  }
  getDiagnostics(analysisId: string): Observable<AnalysisDiagnostics> {
    return this.http.get<AnalysisDiagnostics>(
      `${this.baseUrl}/analyses/${encodeURIComponent(analysisId)}/diagnostics`,
    );
  }
  getWarnings(analysisId: string): Observable<readonly CollectionWarning[]> {
    return this.http.get<readonly CollectionWarning[]>(
      `${this.baseUrl}/analyses/${encodeURIComponent(analysisId)}/warnings`,
    );
  }
  getContext(analysisId: string): Observable<Readonly<Record<string, JsonValue>>> {
    return this.http.get<Readonly<Record<string, JsonValue>>>(
      `${this.baseUrl}/analyses/${encodeURIComponent(analysisId)}/context`,
    );
  }
  getProfile(analysisId: string): Observable<ProjectProfile> {
    return this.http.get<ProjectProfile>(
      `${this.baseUrl}/analyses/${encodeURIComponent(analysisId)}/profile`,
    );
  }
}
