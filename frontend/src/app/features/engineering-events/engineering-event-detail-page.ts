import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { EngineeringEventService } from './engineering-event.service';

@Component({
  imports: [AsyncPipe, DatePipe, RouterLink],
  template: `
    <article>
      @if (view$ | async; as view) {
        @if (view.state === 'loading') { <p role="status">Loading Engineering Event…</p> }
        @if (view.state === 'error') { <p role="alert">{{ view.error.message }}</p> }
        @if (view.state === 'loaded') {
          <h1>{{ view.event.title }}</h1>
          <p><strong>{{ view.event.category.replaceAll('_', ' ') }}</strong> · {{ view.event.occurredAt | date: 'medium' }}</p>
          <p>{{ view.event.summary }}</p>
          <h2>Why this evolution is significant</h2><p>{{ view.event.significance }}</p>
          <h2>Immutable revision boundary</h2>
          <code>{{ view.event.baseCommit }}</code><span> → </span><code>{{ view.event.targetCommit }}</code>
          @if (view.event.mergeCommit) { <p>Merge commit compared through its first parent.</p> }
          <h2>Audit</h2>
          <a [routerLink]="['/analyses', view.event.analysisId]">Analysis</a> ·
          <a [routerLink]="['/proposals', view.event.proposalId]">Proposal and Validation</a>
          <ul>@for (reference of view.event.evidenceReferences; track reference) { <li><code>{{ reference }}</code></li> }</ul>
        }
      }
    </article>`,
})
export class EngineeringEventDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(EngineeringEventService);
  readonly view$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((id) => this.service.get(id).pipe(
      map((event) => ({ state: 'loaded' as const, event })),
      catchError((error) => of({ state: 'error' as const, error: toRequestError(error, 'analysis') })),
      startWith({ state: 'loading' as const }),
    )),
  );
}
