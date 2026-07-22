import { AsyncPipe, DatePipe, JsonPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  exhaustMap,
  forkJoin,
  map,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { InsightProposalService } from './insight-proposal.service';
import { InsightService } from './insight.service';
import { InsightSeverity } from './insight.models';
import { StatusBadge } from '../../shared/components/status-badge';
type Action = 'ACCEPTED' | 'REJECTED';
interface DecisionAction {
  readonly decision: Action;
  readonly analysisId: string;
}
@Component({
  selector: 'app-proposal-detail-page',
  imports: [AsyncPipe, DatePipe, JsonPipe, ReactiveFormsModule, RouterLink, StatusBadge],
  templateUrl: './proposal-detail-page.html',
  styleUrl: './proposal-detail-page.scss',
})
export class ProposalDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly proposals = inject(InsightProposalService);
  private readonly insights = inject(InsightService);
  private readonly refresh = new Subject<void>();
  private readonly actions = new Subject<DecisionAction>();
  confirmation: Action | null = null;
  readonly severities: readonly InsightSeverity[] = ['INFO', 'WARNING', 'CRITICAL'];
  readonly form = new FormGroup({
    validatedBy: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(
          /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
        ),
      ],
    }),
    comment: new FormControl('', { nonNullable: true, validators: Validators.maxLength(2000) }),
    severity: new FormControl<InsightSeverity>('INFO', { nonNullable: true }),
  });
  private readonly id$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  readonly view$ = this.id$.pipe(
    switchMap((id) =>
      this.refresh.pipe(
        startWith(undefined),
        switchMap(() =>
          this.proposals.getProposal(id).pipe(
            switchMap((proposal) =>
              forkJoin({
                proposal: of(proposal),
                insights: this.insights.getInsightsByAnalysis(proposal.analysisId),
                decision:
                  proposal.status === 'PROPOSED'
                    ? of(null)
                    : this.proposals.getDecision(proposal.id),
              }),
            ),
            map((data) => ({ state: 'loaded' as const, data })),
            catchError((error: unknown) => {
              const mapped = toRequestError(error, 'proposal');
              return of(
                mapped.kind === 'not-found'
                  ? { state: 'not-found' as const }
                  : { state: 'error' as const, error: mapped },
              );
            }),
            startWith({ state: 'loading' as const }),
          ),
        ),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  readonly action$ = this.id$.pipe(
    switchMap((id) =>
      this.actions.pipe(
        exhaustMap(({ decision, analysisId }) => {
          const comment = this.form.controls.comment.value.trim() || null;
          const reviewer = this.form.controls.validatedBy.value;
          const request$ =
            decision === 'ACCEPTED'
              ? this.proposals.acceptProposal(id, {
                  validatedBy: reviewer,
                  comment,
                  insightSeverity: this.form.controls.severity.value,
                })
              : this.proposals.rejectProposal(id, { validatedBy: reviewer, comment });
          return request$.pipe(
            tap(() => {
              this.confirmation = null;
              this.refresh.next();
              void this.router.navigate(['/analyses', analysisId], {
                fragment: 'insight-proposals',
              });
            }),
            map(() => ({ state: 'success' as const })),
            catchError((error: unknown) => {
              const mapped = toRequestError(error, 'proposal');
              if (mapped.kind === 'conflict') this.refresh.next();
              return of({ state: 'error' as const, error: mapped });
            }),
            startWith({ state: 'pending' as const }),
          );
        }),
      ),
    ),
    startWith({ state: 'idle' as const }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  decide(analysisId: string): void {
    if (this.confirmation && this.form.valid)
      this.actions.next({ decision: this.confirmation, analysisId });
    else this.form.markAllAsTouched();
  }

  generateLocalReviewerId(): void {
    this.form.controls.validatedBy.setValue(crypto.randomUUID());
    this.form.controls.validatedBy.markAsTouched();
  }
}
