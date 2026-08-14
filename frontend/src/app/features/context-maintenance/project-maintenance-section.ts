import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input, OnChanges } from '@angular/core';
import {
  catchError,
  map,
  Observable,
  of,
  ReplaySubject,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { DashboardCard } from '../../shared/components/dashboard-card';
import {
  MaintenanceContextSurface,
  MaintenanceFinding,
  MaintenanceFindingSeverity,
  MaintenanceFindingStatus,
  MaintenanceSuggestedActionCategory,
} from './maintenance-finding.models';
import { MaintenanceFindingService } from './maintenance-finding.service';

type MaintenanceViewState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly findings: readonly MaintenanceFinding[] }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-maintenance-section',
  imports: [AsyncPipe, DatePipe, DashboardCard],
  templateUrl: './project-maintenance-section.html',
  styleUrl: './project-maintenance-section.scss',
})
export class ProjectMaintenanceSection implements OnChanges {
  @Input({ required: true }) projectId = '';

  private readonly service = inject(MaintenanceFindingService);
  private readonly projectIds = new ReplaySubject<string>(1);

  readonly view$: Observable<MaintenanceViewState> = this.projectIds.pipe(
    switchMap((projectId) =>
      this.service.getByProject(projectId).pipe(
        map((findings) => ({ state: 'loaded' as const, findings })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'project') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  ngOnChanges(): void {
    if (this.projectId) this.projectIds.next(this.projectId);
  }

  humanize(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase();
  }

  surfaceLabel(surface: MaintenanceContextSurface): string {
    return surface === 'PROJECT_PROJECTION' ? 'Projection' : 'Understanding';
  }

  actionLabel(action: MaintenanceSuggestedActionCategory): string {
    return this.humanize(action);
  }

  statusLabel(status: MaintenanceFindingStatus): string {
    return this.humanize(status);
  }

  severityTone(severity: MaintenanceFindingSeverity): 'high' | 'medium' | 'low' {
    if (severity === 'HIGH') return 'high';
    if (severity === 'MEDIUM') return 'medium';
    return 'low';
  }

  requiresReview(finding: MaintenanceFinding): boolean {
    return finding.humanReviewRequired && finding.status === 'OPEN';
  }
}
