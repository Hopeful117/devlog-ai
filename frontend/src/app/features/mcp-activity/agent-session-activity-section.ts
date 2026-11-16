import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import {
  BehaviorSubject,
  catchError,
  map,
  Observable,
  of,
  Subject,
  switchMap,
  takeUntil,
  timer,
} from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { AgentSession } from './agent-session.models';
import { AgentSessionService } from './agent-session.service';
import { McpOperation } from './mcp-operation.models';

type SessionListState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly sessions: readonly AgentSession[] }
  | { readonly state: 'error' };

type DetailState =
  | { readonly state: 'closed' }
  | { readonly state: 'loading'; readonly sessionId: string }
  | {
      readonly state: 'loaded';
      readonly sessionId: string;
      readonly operations: readonly McpOperation[];
    }
  | { readonly state: 'error'; readonly sessionId: string };

@Component({
  selector: 'app-agent-session-activity-section',
  imports: [AsyncPipe, DatePipe],
  templateUrl: './agent-session-activity-section.html',
  styleUrl: './agent-session-activity-section.scss',
})
export class AgentSessionActivitySection implements OnInit, OnDestroy {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(AgentSessionService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly destroyed$ = new Subject<void>();

  detailState: DetailState = { state: 'closed' };
  private readonly detailRequest$ = new BehaviorSubject<string | null>(null);

  readonly sessions$: Observable<SessionListState> = new Observable<string>((observer) => {
    observer.next(this.projectId);
    observer.complete();
  }).pipe(
    switchMap((id) =>
      timer(0, this.intervalMs).pipe(
        switchMap(() =>
          this.service.byProject(id).pipe(
            map((page) => ({ state: 'loaded' as const, sessions: page.content })),
            catchError(() => of({ state: 'error' as const })),
          ),
        ),
        takeUntil(this.destroyed$),
      ),
    ),
  );

  providerBadge(session: AgentSession): string {
    const p = session.agentProvider;
    if (!p) return '?';
    if (p.startsWith('claude-code')) return 'CC';
    if (p.startsWith('opencode')) return 'OC';
    if (p.startsWith('claude-desktop')) return 'CD';
    return '?';
  }

  shortId(session: AgentSession): string {
    return session.agentSessionId.slice(0, 8);
  }

  openDetail(session: AgentSession): void {
    const sessionId = session.agentSessionId;
    if (this.detailState.state !== 'closed' && this.detailState.sessionId === sessionId) {
      this.closeDetail();
      return;
    }
    this.detailState = { state: 'loading', sessionId };
    this.detailRequest$.next(sessionId);
  }

  closeDetail(): void {
    this.detailState = { state: 'closed' };
    this.detailRequest$.next(null);
  }

  ngOnInit(): void {
    this.detailRequest$
      .pipe(
        switchMap((sessionId) => {
          if (sessionId === null) return of(null);
          return this.service.operationsBySession(this.projectId, sessionId).pipe(
            map((page) => ({ sessionId, operations: page.content })),
            catchError(() => of({ sessionId, operations: null })),
          );
        }),
        takeUntil(this.destroyed$),
      )
      .subscribe((result) => {
        if (result === null) return;
        if (result.operations === null) {
          this.detailState = { state: 'error', sessionId: result.sessionId };
        } else {
          this.detailState = {
            state: 'loaded',
            sessionId: result.sessionId,
            operations: result.operations,
          };
        }
      });
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}
