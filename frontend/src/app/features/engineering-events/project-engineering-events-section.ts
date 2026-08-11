import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, exhaustMap, map, of, shareReplay, startWith, Subject, switchMap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { SourceSummary } from '../projects/source.models';
import { EngineeringEventService } from './engineering-event.service';

@Component({
  selector: 'app-project-engineering-events-section',
  imports: [AsyncPipe, DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './project-engineering-events-section.html',
  styleUrl: './project-engineering-events-section.scss',
})
export class ProjectEngineeringEventsSection implements OnChanges {
  @Input({ required: true }) projectId = '';
  @Input({ required: true }) sources: readonly SourceSummary[] = [];
  private readonly service = inject(EngineeringEventService);
  private readonly reload = new Subject<void>();
  private readonly executions = new Subject<{ sourceId: string; targetCommit: string }>();
  readonly form = new FormGroup({
    sourceId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    targetCommit: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^([0-9a-fA-F]{40}|[0-9a-fA-F]{64})$/)],
    }),
  });
  readonly events$ = this.reload.pipe(
    startWith(undefined),
    switchMap(() =>
      this.service.byProject(this.projectId, 0, 5).pipe(
        map((page) => ({ state: 'loaded' as const, page })),
        catchError((error) =>
          of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  readonly execution$ = this.executions.pipe(
    exhaustMap((request) =>
      this.service.execute(this.projectId, request.sourceId, request.targetCommit).pipe(
        map((result) => ({ state: 'success' as const, result })),
        catchError((error) =>
          of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
        ),
        startWith({ state: 'pending' as const }),
      ),
    ),
    startWith({ state: 'idle' as const }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  ngOnChanges(): void {
    if (!this.form.controls.sourceId.value && this.sources[0])
      this.form.controls.sourceId.setValue(this.sources[0].id);
  }
  execute(): void {
    if (this.form.invalid) return this.form.markAllAsTouched();
    this.executions.next({
      sourceId: this.form.controls.sourceId.value,
      targetCommit: this.form.controls.targetCommit.value.toLowerCase(),
    });
  }
}
