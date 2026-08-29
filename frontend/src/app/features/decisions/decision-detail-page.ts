import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { DecisionService } from './decision.service';

@Component({
  imports: [AsyncPipe, DatePipe, RouterLink],
  template: `<a routerLink="/projects">← Back to Projects</a>
    @if (view$ | async; as view) {
      @if (view.state === 'loading') {
        <p role="status">Loading Decision…</p>
      } @else if (view.state === 'not-found') {
        <h1>Decision not found</h1>
      } @else if (view.state === 'error') {
        <p role="alert">{{ view.error.message }}</p>
      } @else {
        <article>
          <h1>{{ view.data.title }}</h1>
          <h2>Context</h2>
          <p>{{ view.data.context }}</p>
          <h2>Choice</h2>
          <p>{{ view.data.choice }}</p>
          <h2>Rationale</h2>
          <p>{{ view.data.rationale }}</p>
          @if (view.data.consequences; as consequences) {
            <h2>Consequences</h2>
            <p>{{ consequences }}</p>
          }
          <dl>
            <dt>Created</dt>
            <dd>
              {{ view.data.createdAt ? (view.data.createdAt | date: 'medium') : 'Unavailable' }}
            </dd>
            <dt>Updated</dt>
            <dd>
              {{ view.data.updatedAt ? (view.data.updatedAt | date: 'medium') : 'Unavailable' }}
            </dd>
            <dt>Project</dt>
            <dd>{{ view.data.projectId }}</dd>
            @if (view.data.proposalId; as proposalId) {
              <dt>Proposal</dt>
              <dd>
                <a [routerLink]="['/proposals', proposalId]">{{ proposalId }}</a>
              </dd>
            }
          </dl>
        </article>
      }
    }`,
})
export class DecisionDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(DecisionService);

  readonly view$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((id) =>
      this.service.getDecision(id).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) => {
          const mapped = toRequestError(error, 'decision');
          return of(
            mapped.kind === 'not-found'
              ? { state: 'not-found' as const }
              : { state: 'error' as const, error: mapped },
          );
        }),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );
}
