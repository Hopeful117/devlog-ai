import { AsyncPipe } from '@angular/common';
import { Component, inject, Input, OnChanges } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {
  catchError,
  exhaustMap,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  tap,
} from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { AnalysisSummary } from '../analyses/analysis.models';
import { SourceSummary } from './source.models';
import { ProjectUnderstandingService } from './project-understanding.service';

type ExecutionState =
  | { readonly state: 'idle' }
  | { readonly state: 'pending' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-understanding-section',
  imports: [AsyncPipe, ReactiveFormsModule],
  templateUrl: './project-understanding-section.html',
  styleUrl: './project-understanding-section.scss',
})
export class ProjectUnderstandingSection implements OnChanges {
  @Input({ required: true }) projectId = '';
  @Input({ required: true }) sources: readonly SourceSummary[] = [];
  @Input({ required: true }) analyses: readonly AnalysisSummary[] = [];

  private readonly service = inject(ProjectUnderstandingService);
  private readonly router = inject(Router);
  private readonly executions = new Subject<{ sourceId: string; targetRevision?: string }>();

  readonly form = new FormGroup({
    sourceId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    targetRevision: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(255),
    }),
  });

  readonly state$: Observable<ExecutionState> = this.executions.pipe(
    exhaustMap((request) =>
      this.service.execute(this.projectId, request).pipe(
        tap((response) => void this.router.navigate(['/analyses', response.analysisId])),
        map((): ExecutionState => ({ state: 'idle' })),
        catchError((error: unknown) =>
          of<ExecutionState>({
            state: 'error',
            error: toRequestError(error, 'analysis'),
          }),
        ),
        startWith<ExecutionState>({ state: 'pending' }),
      ),
    ),
    startWith<ExecutionState>({ state: 'idle' }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  get compatibleSources(): readonly SourceSummary[] {
    return this.sources.filter((source) => source.active && source.type === 'GIT_REPOSITORY');
  }

  get refresh(): boolean {
    return this.analyses.some(
      (analysis) => analysis.intentId === 'describe-project' && analysis.intentVersion === 'v1',
    );
  }

  ngOnChanges(): void {
    const compatible = this.compatibleSources;
    if (compatible.length === 1) this.form.controls.sourceId.setValue(compatible[0].id);
    else if (!compatible.some((source) => source.id === this.form.controls.sourceId.value))
      this.form.controls.sourceId.setValue('');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const revision = value.targetRevision.trim();
    this.executions.next({
      sourceId: value.sourceId,
      ...(revision ? { targetRevision: revision } : {}),
    });
  }
}
