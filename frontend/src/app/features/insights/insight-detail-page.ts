import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { InsightService } from './insight.service';
@Component({
  selector: 'app-insight-detail-page',
  imports: [AsyncPipe, DatePipe, RouterLink],
  template: `<a routerLink="/insights">← Back to Insights</a>
    @if (view$ | async; as vm) {
      @if (vm.state === 'loading') {
        <p role="status">Loading Insight…</p>
      } @else if (vm.state === 'not-found') {
        <h1>Insight not found</h1>
      } @else if (vm.state === 'error') {
        <p role="alert">{{ vm.error.message }}</p>
      } @else {
        <article>
          <h1>{{ vm.data.title }}</h1>
          <p>{{ vm.data.content }}</p>
          <dl>
            <dt>Type</dt>
            <dd>{{ vm.data.type }}</dd>
            <dt>Severity</dt>
            <dd>{{ vm.data.severity }}</dd>
            <dt>Accepted</dt>
            <dd>{{ vm.data.createdAt | date: 'medium' }}</dd>
            <dt>Project</dt>
            <dd>{{ vm.data.projectId }}</dd>
            <dt>Analysis</dt>
            <dd>
              <a [routerLink]="['/analyses', vm.data.analysisId]">{{ vm.data.analysisId }}</a>
            </dd>
            <dt>Proposal</dt>
            <dd>
              <a [routerLink]="['/proposals', vm.data.proposalId]">{{ vm.data.proposalId }}</a>
            </dd>
          </dl>
        </article>
      }
    }`,
})
export class InsightDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(InsightService);
  readonly view$ = this.route.paramMap.pipe(
    map((p) => p.get('id') ?? ''),
    switchMap((id) =>
      this.service.getInsight(id).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) => {
          const mapped = toRequestError(error, 'insight');
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
