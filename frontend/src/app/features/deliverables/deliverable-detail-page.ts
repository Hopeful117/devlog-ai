import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { DeliverableService } from './deliverable.service';

@Component({
  selector: 'app-deliverable-detail-page',
  imports: [AsyncPipe, DatePipe, RouterLink],
  templateUrl: './deliverable-detail-page.html',
  styleUrl: './deliverable-detail-page.scss',
})
export class DeliverableDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(DeliverableService);
  readonly view$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((id) =>
      this.service.get(id).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) => {
          const mapped = toRequestError(error, 'deliverable');
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
