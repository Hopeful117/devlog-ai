import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  exhaustMap,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  switchMap,
  takeWhile,
  timer,
} from 'rxjs';
import { APP_ENVIRONMENT } from '../../../core/config/app-environment';
import { RequestError, toRequestError } from '../../../core/http/request-error';
import { AnalysisDeliverablePanel } from '../../deliverables/analysis-deliverable-panel';
import {
  AnalysisResult,
  EvidenceCategory,
  NextAction,
  ProposalSummary,
  TrustedArtifact,
} from '../analysis.models';
import { AnalysisService } from '../analysis.service';

type LoadState<T> =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly data: T }
  | { readonly state: 'not-found' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-analysis-result-page',
  imports: [AsyncPipe, DatePipe, DecimalPipe, RouterLink, AnalysisDeliverablePanel],
  templateUrl: './analysis-result-page.html',
  styleUrl: './analysis-result-page.scss',
})
export class AnalysisResultPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(AnalysisService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly routeId$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly view$: Observable<LoadState<AnalysisResult>> = this.routeId$.pipe(
    switchMap((id) =>
      timer(0, this.intervalMs).pipe(
        exhaustMap(() =>
          this.service.getResult(id).pipe(
            map((data) => ({ state: 'loaded' as const, data })),
            catchError((error: unknown) => {
              const mapped = toRequestError(error, 'analysis');
              return of(
                mapped.kind === 'not-found'
                  ? { state: 'not-found' as const }
                  : { state: 'error' as const, error: mapped },
              );
            }),
          ),
        ),
        takeWhile(
          (result) => result.state === 'loaded' && result.data.execution.success === null,
          true,
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  scopeLabel(result: AnalysisResult): string {
    if (result.analysis.scope === 'REPOSITORY_SCOPE') {
      return `Repository: ${result.analysis.repositoryName ?? 'Unknown repository'}`;
    }
    const sources = result.analysis.sourcesAnalyzed.length;
    return `Entire Project (${sources} source${sources === 1 ? '' : 's'})`;
  }

  proposalActionLabel(proposal: ProposalSummary): string {
    return proposal.status === 'PROPOSED' ? 'Review proposal' : 'View proposal';
  }

  trustedArtifactActionLabel(trustedArtifact: TrustedArtifact): string {
    switch (trustedArtifact.type) {
      case 'INSIGHT':
        return 'View insight';
      case 'DECISION':
        return 'View decision';
      case 'ENGINEERING_EVENT':
        return 'View engineering event';
    }
  }

  trustedArtifactRoute(trustedArtifact: TrustedArtifact): readonly string[] | null {
    if (
      trustedArtifact.availability !== 'AVAILABLE' ||
      !trustedArtifact.detailAvailable ||
      !trustedArtifact.id
    ) {
      return null;
    }

    switch (trustedArtifact.type) {
      case 'INSIGHT':
        return ['/insights', trustedArtifact.id];
      case 'DECISION':
        return ['/decisions', trustedArtifact.id];
      case 'ENGINEERING_EVENT':
        return ['/engineering-events', trustedArtifact.id];
    }
  }

  trustedArtifactUnavailableLabel(trustedArtifact: TrustedArtifact): string {
    switch (trustedArtifact.type) {
      case 'INSIGHT':
        return 'Trusted Insight unavailable';
      case 'DECISION':
        return 'Trusted Decision unavailable';
      case 'ENGINEERING_EVENT':
        return 'Trusted Engineering Event unavailable';
    }
  }

  evidenceCategories(
    result: AnalysisResult,
  ): readonly { key: string; label: string; value: EvidenceCategory }[] {
    return [
      { key: 'facts', label: 'Facts', value: result.evidence.facts },
      { key: 'observations', label: 'Observations', value: result.evidence.observations },
      { key: 'priorInsights', label: 'Prior Insights', value: result.evidence.priorInsights },
      {
        key: 'architectureKnowledge',
        label: 'Architecture Knowledge',
        value: result.evidence.architectureKnowledge,
      },
      {
        key: 'engineeringEvents',
        label: 'Engineering Events',
        value: result.evidence.engineeringEvents,
      },
      { key: 'humanContext', label: 'Human Context', value: result.evidence.humanContext },
      {
        key: 'evolutionContext',
        label: 'Evolution Context',
        value: result.evidence.evolutionContext,
      },
      {
        key: 'repositoryEvidence',
        label: 'Repository Evidence',
        value: result.evidence.repositoryEvidence,
      },
    ];
  }

  nextActionRoute(action: NextAction, analysisId: string): readonly string[] | null {
    switch (action.action) {
      case 'REVIEW_PROPOSALS':
        return ['/analyses', analysisId, 'proposal-review'];
      case 'VIEW_DIAGNOSTICS':
        return ['/analyses', analysisId, 'diagnostics'];
      default:
        return null;
    }
  }

  showDeliverableAction(result: AnalysisResult): boolean {
    return result.nextActions.some(
      (action) => action.action === 'GENERATE_DELIVERABLE' && action.available,
    );
  }

  trackProposal(_: number, proposal: ProposalSummary): string {
    return proposal.id;
  }
}
