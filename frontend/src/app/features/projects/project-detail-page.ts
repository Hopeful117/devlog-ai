import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, Observable, of, startWith, switchMap } from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { DashboardCard } from '../../shared/components/dashboard-card';
import { AnalysisSummary } from '../analyses/analysis.models';
import { AnalysisService } from '../analyses/analysis.service';
import { Deliverable } from '../deliverables/deliverable.models';
import { DeliverableService } from '../deliverables/deliverable.service';
import { InsightSummary } from '../insights/insight.models';
import { InsightService } from '../insights/insight.service';
import { ProjectDetail } from './project.models';
import { ProjectAnalysesSection } from '../analyses/project-analyses-section';
import { ProjectService } from './project.service';
import { SourceSummary } from './source.models';
import { SourceService } from './source.service';

type ProjectDetailViewState =
  | { readonly state: 'loading' }
  | {
      readonly state: 'loaded';
      readonly project: ProjectDetail;
      readonly sources: readonly SourceSummary[];
      readonly analyses: readonly AnalysisSummary[];
      readonly deliverables: readonly Deliverable[];
      readonly knowledge: readonly InsightSummary[];
    }
  | { readonly state: 'not-found' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-detail-page',
  imports: [AsyncPipe, DatePipe, RouterLink, DashboardCard, ProjectAnalysesSection],
  templateUrl: './project-detail-page.html',
  styleUrl: './project-detail-page.scss',
})
export class ProjectDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly sourceService = inject(SourceService);
  private readonly analysisService = inject(AnalysisService);
  private readonly deliverableService = inject(DeliverableService);
  private readonly insightService = inject(InsightService);

  readonly viewModel$: Observable<ProjectDetailViewState> = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((identifier) =>
      this.projectService.getProject(identifier).pipe(
        switchMap((project) =>
          forkJoin({
            sources: this.sourceService
              .getSourcesByProject(project.id)
              .pipe(catchError(() => of([] as readonly SourceSummary[]))),
            analyses: this.analysisService
              .getAnalysesByProject(project.id)
              .pipe(catchError(() => of([] as readonly AnalysisSummary[]))),
            deliverables: this.deliverableService
              .getByProject(project.id)
              .pipe(catchError(() => of([] as readonly Deliverable[]))),
            knowledge: this.insightService
              .getInsightsByProject(project.id)
              .pipe(catchError(() => of([] as readonly InsightSummary[]))),
          }).pipe(map((data) => ({ state: 'loaded' as const, project, ...data }))),
        ),
        catchError((error: unknown) => {
          const requestError = toRequestError(error);
          return requestError.kind === 'not-found'
            ? of({ state: 'not-found' as const })
            : of({ state: 'error' as const, error: requestError });
        }),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );

  latestAnalysis(items: readonly AnalysisSummary[]): AnalysisSummary | undefined {
    return [...items].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
  }

  activeSources(items: readonly SourceSummary[]): number {
    return items.filter((source) => source.active).length;
  }

  completedAnalyses(items: readonly AnalysisSummary[]): number {
    return items.filter((analysis) => analysis.status === 'COMPLETED').length;
  }
}
