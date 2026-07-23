import { AsyncPipe } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, exhaustMap, map, of, shareReplay, startWith, Subject, tap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { LoadingIndicator } from '../../shared/components/loading-indicator';
import { CreateDeliverableRequest } from './deliverable.models';
import { DeliverableForm } from './deliverable-form';
import { DeliverableService } from './deliverable.service';

@Component({
  selector: 'app-analysis-deliverable-panel',
  imports: [AsyncPipe, DeliverableForm, LoadingIndicator],
  templateUrl: './analysis-deliverable-panel.html',
  styleUrl: './analysis-deliverable-panel.scss',
})
export class AnalysisDeliverablePanel {
  @Input({ required: true }) projectId = '';
  @Input({ required: true }) analysisId = '';
  private readonly service = inject(DeliverableService);
  private readonly router = inject(Router);
  private readonly requests = new Subject<CreateDeliverableRequest>();
  showForm = false;
  readonly generation$ = this.requests.pipe(
    exhaustMap((request) =>
      this.service.generate(request).pipe(
        tap((created) => void this.router.navigate(['/deliverables', created.id])),
        map(() => ({ state: 'success' as const })),
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
