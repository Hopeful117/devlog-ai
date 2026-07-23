import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  catchError,
  exhaustMap,
  map,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { LoadingIndicator } from '../../shared/components/loading-indicator';
import { CreateDeliverableRequest } from './deliverable.models';
import { DeliverableForm } from './deliverable-form';
import { DeliverableService } from './deliverable.service';

@Component({
  selector: 'app-project-deliverables-section',
  imports: [AsyncPipe, DatePipe, RouterLink, DeliverableForm, LoadingIndicator],
  templateUrl: './project-deliverables-section.html',
  styleUrl: './project-deliverables-section.scss',
})
export class ProjectDeliverablesSection {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(DeliverableService);
  private readonly router = inject(Router);
  private readonly refresh = new Subject<void>();
  private readonly requests = new Subject<CreateDeliverableRequest>();
  readonly deliverables$ = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.service.getByProject(this.projectId).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'deliverable') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  readonly generation$ = this.requests.pipe(
    exhaustMap((request) =>
      this.service.generate(request).pipe(
        tap((created) => {
          this.refresh.next();
          void this.router.navigate(['/deliverables', created.id]);
        }),
        map(() => ({ state: 'idle' as const })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'deliverable') }),
        ),
        startWith({ state: 'pending' as const }),
      ),
    ),
    startWith({ state: 'idle' as const }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  generate(request: CreateDeliverableRequest): void {
    this.requests.next(request);
  }
}
