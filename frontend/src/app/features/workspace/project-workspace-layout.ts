import { AsyncPipe } from '@angular/common';
import { Component, inject, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, map, of, startWith, Subject, switchMap, takeUntil, timer } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ActivityAlertService } from '../mcp-activity/activity-alert.service';
import { ProjectService } from '../projects/project.service';

@Component({
  selector: 'app-project-workspace-layout',
  imports: [AsyncPipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './project-workspace-layout.html',
  styleUrl: './project-workspace-layout.scss',
})
export class ProjectWorkspaceLayout implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly projects = inject(ProjectService);
  private readonly alertService = inject(ActivityAlertService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly destroyed$ = new Subject<void>();

  readonly project$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((identifier) =>
      this.projects.getProject(identifier).pipe(
        map((project) => ({ state: 'loaded' as const, project })),
        catchError(() => of({ state: 'unavailable' as const })),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );

  readonly alertCount$ = this.project$.pipe(
    switchMap((view) => {
      if (view.state !== 'loaded') return of({ errorCount: 0 });
      return timer(0, this.intervalMs).pipe(
        switchMap(() =>
          this.alertService.countByProject(view.project.id).pipe(
            catchError(() => of({ errorCount: 0 })),
          ),
        ),
        takeUntil(this.destroyed$),
      );
    }),
  );

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}
