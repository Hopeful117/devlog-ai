import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  exhaustMap,
  forkJoin,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

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
import { ProjectUnderstandingSection } from './project-understanding-section';
import { ProjectFreshnessSection } from './project-freshness-section';
import { ProjectService } from './project.service';
import { SourceSummary } from './source.models';
import { SourceService } from './source.service';
import { ProjectEngineeringEventsSection } from '../engineering-events/project-engineering-events-section';
import { ProjectMaintenanceSection } from '../context-maintenance/project-maintenance-section';

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

type ProjectActionState =
  | { readonly state: 'idle' }
  | { readonly state: 'pending' }
  | { readonly state: 'success' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-detail-page',
  imports: [
    AsyncPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    DashboardCard,
    ProjectAnalysesSection,
    ProjectUnderstandingSection,
    ProjectFreshnessSection,
    ProjectEngineeringEventsSection,
    ProjectMaintenanceSection,
  ],
  templateUrl: './project-detail-page.html',
  styleUrl: './project-detail-page.scss',
})
export class ProjectDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly projectService = inject(ProjectService);
  private readonly sourceService = inject(SourceService);
  private readonly analysisService = inject(AnalysisService);
  private readonly deliverableService = inject(DeliverableService);
  private readonly insightService = inject(InsightService);
  private readonly projectChanges = new Subject<ProjectDetail>();
  private readonly updates = new Subject<{
    readonly project: ProjectDetail;
    readonly name: string;
    readonly description: string;
  }>();
  private readonly deletions = new Subject<ProjectDetail>();
  showEdit = false;
  showDelete = false;
  preferredUnderstandingSourceId = '';

  requestUnderstanding(sourceId: string): void {
    this.preferredUnderstandingSourceId = sourceId;
    document.getElementById('project-understanding-title')?.focus();
  }

  readonly editForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(5000),
    }),
  });

  readonly deleteForm = new FormGroup({
    confirmation: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

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
          }).pipe(
            switchMap((data) =>
              this.projectChanges.pipe(
                startWith(project),
                map((currentProject) => ({
                  state: 'loaded' as const,
                  project: currentProject,
                  ...data,
                })),
              ),
            ),
          ),
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
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly updateState$: Observable<ProjectActionState> = this.updates.pipe(
    exhaustMap(({ project, name, description }) =>
      this.projectService.updateProject(project.slug, { name, description }).pipe(
        tap((updated) => {
          this.projectChanges.next(updated);
          this.showEdit = false;
          this.editForm.reset();
          this.deleteForm.reset();
        }),
        map((): ProjectActionState => ({ state: 'success' })),
        catchError((error: unknown) =>
          of<ProjectActionState>({ state: 'error', error: toRequestError(error, 'project') }),
        ),
        startWith<ProjectActionState>({ state: 'pending' }),
      ),
    ),
    startWith<ProjectActionState>({ state: 'idle' }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly deleteState$: Observable<ProjectActionState> = this.deletions.pipe(
    exhaustMap((project) =>
      this.projectService.deleteProject(project.slug).pipe(
        tap(() => void this.router.navigate(['/projects'])),
        map((): ProjectActionState => ({ state: 'success' })),
        catchError((error: unknown) =>
          of<ProjectActionState>({ state: 'error', error: toRequestError(error, 'project') }),
        ),
        startWith<ProjectActionState>({ state: 'pending' }),
      ),
    ),
    startWith<ProjectActionState>({ state: 'idle' }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  beginEdit(project: ProjectDetail): void {
    this.showDelete = false;
    this.showEdit = true;
    this.editForm.setValue({ name: project.name, description: project.description ?? '' });
  }

  cancelEdit(): void {
    this.showEdit = false;
    this.editForm.reset();
  }

  updateProject(project: ProjectDetail): void {
    const value = this.editForm.getRawValue();
    const name = value.name.trim();
    if (!name) this.editForm.controls.name.setErrors({ required: true });
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.updates.next({ project, name, description: value.description.trim() });
  }

  beginDelete(): void {
    this.showEdit = false;
    this.showDelete = true;
    this.deleteForm.reset();
  }

  cancelDelete(): void {
    this.showDelete = false;
    this.deleteForm.reset();
  }

  deleteProject(project: ProjectDetail): void {
    if (this.deleteForm.controls.confirmation.value !== project.name) {
      this.deleteForm.controls.confirmation.setErrors({ projectNameMismatch: true });
      this.deleteForm.controls.confirmation.markAsTouched();
      return;
    }
    this.deletions.next(project);
  }

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
