import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  AcceptProposalRequest,
  InsightProposalDetail,
  InsightProposalSummary,
  ProposalDecisionRequest,
  ProposalDecisionResult,
  RejectProposalRequest,
} from './insight.models';
@Injectable({ providedIn: 'root' })
export class InsightProposalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;
  getProposalsByAnalysis(id: string): Observable<readonly InsightProposalSummary[]> {
    return this.http.get<readonly InsightProposalSummary[]>(
      `${this.base}/proposals/analysis/${encodeURIComponent(id)}`,
    );
  }
  getProposal(id: string): Observable<InsightProposalDetail> {
    return this.http.get<InsightProposalDetail>(`${this.base}/proposals/${encodeURIComponent(id)}`);
  }
  getDecision(id: string): Observable<ProposalDecisionResult> {
    return this.http.get<ProposalDecisionResult>(
      `${this.base}/validations/proposal/${encodeURIComponent(id)}`,
    );
  }
  acceptProposal(id: string, request: AcceptProposalRequest): Observable<ProposalDecisionResult> {
    return this.decide({ proposalId: id, decision: 'ACCEPTED', ...request });
  }
  rejectProposal(id: string, request: RejectProposalRequest): Observable<ProposalDecisionResult> {
    return this.decide({ proposalId: id, decision: 'REJECTED', ...request, insightSeverity: null });
  }
  private decide(request: ProposalDecisionRequest): Observable<ProposalDecisionResult> {
    return this.http.post<ProposalDecisionResult>(`${this.base}/validations`, request);
  }
}
