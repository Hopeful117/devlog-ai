import { AsyncPipe, JsonPipe } from '@angular/common';
import { Component, ElementRef, inject, ViewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
import { StatusBadge } from '../../shared/components/status-badge';
import { InsightProposalService } from './insight-proposal.service';
import { InsightSeverity, ProposalReviewItem } from './insight.models';
import { ProposalReviewerSessionService } from './proposal-reviewer-session.service';

type Decision = 'ACCEPTED' | 'REJECTED';
@Component({
  selector: 'app-proposal-review-page',
  imports: [AsyncPipe, JsonPipe, ReactiveFormsModule, RouterLink, StatusBadge],
  templateUrl: './proposal-review-page.html',
  styleUrl: './proposal-review-page.scss',
})
export class ProposalReviewPage {
  private readonly route = inject(ActivatedRoute);
  private readonly proposals = inject(InsightProposalService);
  private readonly reviewerSession = inject(ProposalReviewerSessionService);
  private readonly refresh = new Subject<void>();
  private readonly decisions = new Subject<{ item: ProposalReviewItem; decision: Decision }>();
  private page = 0;
  currentId: string | null = null;
  confirmation: Decision | null = null;
  readonly severities: readonly InsightSeverity[] = ['INFO', 'WARNING', 'CRITICAL'];
  @ViewChild('proposalTitle') proposalTitle?: ElementRef<HTMLElement>;
  readonly form = new FormGroup({
    validatedBy: new FormControl(this.reviewerSession.get() ?? '', {
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
  readonly analysisId = this.route.snapshot.paramMap.get('id') ?? '';
  readonly view$ = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.proposals.getProposalReview(this.analysisId, this.page, 10).pipe(
        tap((data) => {
          const current = data.items.find((item) => item.id === this.currentId);
          const next =
            current ?? data.items.find((item) => item.status === 'PROPOSED') ?? data.items[0];
          this.currentId = next?.id ?? null;
        }),
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'proposal') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  readonly action$ = this.decisions.pipe(
    exhaustMap(({ item, decision }) => {
      const validatedBy = this.form.controls.validatedBy.value;
      this.reviewerSession.set(validatedBy);
      const comment = this.form.controls.comment.value.trim() || null;
      const request =
        decision === 'ACCEPTED'
          ? this.proposals.acceptProposal(item.id, {
              validatedBy,
              comment,
              insightSeverity: this.form.controls.severity.value,
            })
          : this.proposals.rejectProposal(item.id, { validatedBy, comment });
      return request.pipe(
        tap(() => {
          this.currentId = null;
          this.confirmation = null;
          this.form.controls.comment.reset('');
          this.form.controls.severity.reset('INFO');
          this.refresh.next();
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
    startWith({ state: 'idle' as const }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  select(item: ProposalReviewItem): void {
    this.currentId = item.id;
    this.confirmation = null;
  }
  current(items: readonly ProposalReviewItem[]): ProposalReviewItem | undefined {
    return items.find((item) => item.id === this.currentId) ?? items[0];
  }
  decide(item: ProposalReviewItem): void {
    if (this.confirmation && this.form.valid)
      this.decisions.next({ item, decision: this.confirmation });
    else this.form.markAllAsTouched();
  }
  generateReviewer(): void {
    const value = crypto.randomUUID();
    this.reviewerSession.set(value);
    this.form.controls.validatedBy.setValue(value);
    this.form.controls.validatedBy.markAsTouched();
  }
  clearReviewer(): void {
    this.reviewerSession.clear();
    this.form.controls.validatedBy.reset('');
  }
  previous(): void {
    if (this.page > 0) {
      this.page--;
      this.currentId = null;
      this.refresh.next();
    }
  }
  next(): void {
    this.page++;
    this.currentId = null;
    this.refresh.next();
  }
}
