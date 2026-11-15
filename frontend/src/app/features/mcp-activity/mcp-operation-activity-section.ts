import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input, OnDestroy } from '@angular/core';
import { catchError, map, Observable, of, Subject, switchMap, takeUntil, timer } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { McpOperation } from './mcp-operation.models';
import { McpOperationService } from './mcp-operation.service';

const STALE_THRESHOLD_MS = 5 * 60 * 1000;

type ListState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly operations: readonly McpOperation[] }
  | { readonly state: 'error' };

@Component({
  selector: 'app-mcp-operation-activity-section',
  imports: [AsyncPipe, DatePipe],
  templateUrl: './mcp-operation-activity-section.html',
  styleUrl: './mcp-operation-activity-section.scss',
})
export class McpOperationActivitySection implements OnDestroy {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(McpOperationService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly destroyed$ = new Subject<void>();

  readonly operations$: Observable<ListState> = new Observable<string>((observer) => {
    observer.next(this.projectId);
    observer.complete();
  }).pipe(
    switchMap((id) =>
      timer(0, this.intervalMs).pipe(
        switchMap(() =>
          this.service.byProject(id).pipe(
            map((page) => ({ state: 'loaded' as const, operations: page.content })),
            catchError(() => of({ state: 'error' as const })),
          ),
        ),
        takeUntil(this.destroyed$),
      ),
    ),
  );

  statusClass(op: McpOperation): string {
    if (this.isStale(op)) return 'status--stale';
    switch (op.status) {
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

  statusLabel(op: McpOperation): string {
    if (this.isStale(op)) return '?';
    switch (op.status) {
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

  isStale(op: McpOperation): boolean {
    if (op.status !== 'ACTIVE' || op.startedAt === null) return false;
    return Date.now() - new Date(op.startedAt).getTime() > STALE_THRESHOLD_MS;
  }

  displayTime(op: McpOperation): string | null {
    return op.completedAt ?? op.startedAt;
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}
