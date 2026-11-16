import { AsyncPipe } from '@angular/common';
import { Component, inject, Input, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, Observable, of, Subject, switchMap, takeUntil, timer } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ActivityIncidentService } from './activity-incident.service';

interface IncidentSummary {
  readonly total: number;
  readonly errorCount: number;
  readonly warnCount: number;
}

@Component({
  selector: 'app-activity-incident-indicator',
  imports: [AsyncPipe],
  templateUrl: './activity-incident-indicator.html',
  styleUrl: './activity-incident-indicator.scss',
})
export class ActivityIncidentIndicator implements OnDestroy {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(ActivityIncidentService);
  private readonly router = inject(Router);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly destroyed$ = new Subject<void>();

  readonly summary$: Observable<IncidentSummary | null> = new Observable<string>((observer) => {
    observer.next(this.projectId);
    observer.complete();
  }).pipe(
    switchMap((id) =>
      timer(0, this.intervalMs).pipe(
        switchMap(() =>
          this.service.byProject(id, 60, 0, 20).pipe(
            map((page) => ({
              total: page.totalIncidents,
              errorCount: page.errorCount,
              warnCount: page.warnCount,
            })),
            catchError(() => of(null)),
          ),
        ),
        takeUntil(this.destroyed$),
      ),
    ),
  );

  navigateToAlerts(): void {
    void this.router.navigate([], { queryParams: { view: 'alerts' }, queryParamsHandling: 'merge' });
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}
