import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input, OnDestroy } from '@angular/core';
import { catchError, map, Observable, of, Subject, switchMap, takeUntil, timer } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { McpToolEvent } from './mcp-tool-event.models';
import { McpToolEventService } from './mcp-tool-event.service';

type ListState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly events: readonly McpToolEvent[] }
  | { readonly state: 'error' };

@Component({
  selector: 'app-mcp-tool-activity-section',
  imports: [AsyncPipe, DatePipe],
  templateUrl: './mcp-tool-activity-section.html',
  styleUrl: './mcp-tool-activity-section.scss',
})
export class McpToolActivitySection implements OnDestroy {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(McpToolEventService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly destroyed$ = new Subject<void>();

  readonly events$: Observable<ListState> = new Observable<string>((observer) => {
    observer.next(this.projectId);
    observer.complete();
  }).pipe(
    switchMap((id) =>
      timer(0, this.intervalMs).pipe(
        switchMap(() =>
          this.service.byProject(id).pipe(
            map((page) => ({ state: 'loaded' as const, events: page.content })),
            catchError(() => of({ state: 'error' as const })),
          ),
        ),
        takeUntil(this.destroyed$),
      ),
    ),
  );

  statusClass(status: string): string {
    switch (status) {
      case 'RESPONDED':
        return 'status--responded';
      case 'FAILED':
        return 'status--failed';
      case 'INTERRUPTED':
        return 'status--interrupted';
      default:
        return 'status--active';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'RESPONDED':
        return '✓';
      case 'FAILED':
        return '✗';
      case 'INTERRUPTED':
        return '~';
      default:
        return '…';
    }
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}
