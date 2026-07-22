import { AsyncPipe } from '@angular/common';
import { Component, inject, Input, OnChanges } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, Subject, switchMap } from 'rxjs';
import { AiTaskStatus } from '../analyses/analysis.models';
import { toRequestError } from '../../core/http/request-error';
import { InsightProposalService } from './insight-proposal.service';
import { InsightService } from './insight.service';
import { ProposalCountPipe } from './proposal-count.pipe';
import { StatusBadge } from '../../shared/components/status-badge';
import { AnalysisDeliverablePanel } from '../deliverables/analysis-deliverable-panel';
@Component({
  selector: 'app-analysis-insights-section',
  imports: [AsyncPipe, RouterLink, ProposalCountPipe, StatusBadge, AnalysisDeliverablePanel],
  templateUrl: './analysis-insights-section.html',
  styleUrl: './analysis-insights-section.scss',
})
export class AnalysisInsightsSection implements OnChanges {
  @Input({ required: true }) analysisId = '';
  @Input() executionStatus: AiTaskStatus | null = null;
  @Input() provider: string | null = null;
  @Input() refreshToken = '';
  private readonly proposals = inject(InsightProposalService);
  private readonly insights = inject(InsightService);
  private readonly refresh = new Subject<void>();
  readonly proposals$ = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.proposals.getProposalsByAnalysis(this.analysisId).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'proposal') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );
  readonly insights$ = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.insights.getInsightsByAnalysis(this.analysisId).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'insight') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );

  ngOnChanges(): void {
    this.refresh.next();
  }
}
