import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { ProjectService } from '../projects/project.service';
import { EngineeringEventService } from './engineering-event.service';

@Component({
  imports: [AsyncPipe, DatePipe, RouterLink],
  template: `
    <section>
      <h1>Validated Engineering Events</h1>
      @if (view$ | async; as view) {
        @if (view.state === 'loading') { <p role="status">Loading events…</p> }
        @if (view.state === 'error') { <p role="alert">{{ view.error.message }}</p> }
        @if (view.state === 'loaded') {
          <a [routerLink]="['/projects', view.project.slug]">← Project cockpit</a>
          <ul>
            @for (event of view.page.items; track event.id) {
              <li><a [routerLink]="['/engineering-events', event.id]">{{ event.title }}</a>
                <p>{{ event.summary }}</p><small>{{ event.category.replaceAll('_', ' ') }} · {{ event.occurredAt | date: 'medium' }}</small></li>
            } @empty { <li>No validated Engineering Event.</li> }
          </ul>
        }
      }
    </section>`,
})
export class EngineeringEventsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly projects = inject(ProjectService);
  private readonly events = inject(EngineeringEventService);
  readonly view$ = this.route.parent!.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((id) => this.projects.getProject(id)),
    switchMap((project) => this.events.byProject(project.id).pipe(
      map((page) => ({ state: 'loaded' as const, project, page })),
      catchError((error) => of({ state: 'error' as const, error: toRequestError(error, 'analysis') })),
      startWith({ state: 'loading' as const }),
    )),
  );
}
