import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input, OnDestroy } from '@angular/core';
import { catchError, map, Observable, of, Subject, switchMap, takeUntil, timer } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ActivityAlert } from './activity-alert.models';
import { ActivityAlertService } from './activity-alert.service';

type AlertListState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly alerts: readonly ActivityAlert[] }
  | { readonly state: 'error' };

@Component({
  selector: 'app-activity-alert-section',
  imports: [AsyncPipe, DatePipe],
  templateUrl: './activity-alert-section.html',
  styleUrl: './activity-alert-section.scss',
})
export class ActivityAlertSection implements OnDestroy {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(ActivityAlertService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly destroyed$ = new Subject<void>();

  readonly alerts$: Observable<AlertListState> = new Observable<string>((observer) => {
    observer.next(this.projectId);
    observer.complete();
  }).pipe(
    switchMap((id) =>
      timer(0, this.intervalMs).pipe(
        switchMap(() =>
          this.service.byProject(id).pipe(
            map((page) => ({ state: 'loaded' as const, alerts: page.content })),
            catchError(() => of({ state: 'error' as const })),
          ),
        ),
        takeUntil(this.destroyed$),
      ),
    ),
  );

  toolLabel(alert: ActivityAlert): string {
    return alert.toolName ?? 'unknown tool';
  }

  reasonLabel(alert: ActivityAlert): string {
    if (alert.reasonCode) return alert.reasonCode;
    return alert.eventType === 'MCP_TOOL_FAILED' ? 'operation failed' : 'operation interrupted';
  }

  shortSession(alert: ActivityAlert): string | null {
    return alert.agentSessionId ? alert.agentSessionId.slice(0, 8) : null;
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}
