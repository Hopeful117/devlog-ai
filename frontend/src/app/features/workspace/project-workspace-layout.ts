import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { ProjectService } from '../projects/project.service';

@Component({
  selector: 'app-project-workspace-layout',
  imports: [AsyncPipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './project-workspace-layout.html',
  styleUrl: './project-workspace-layout.scss',
})
export class ProjectWorkspaceLayout {
  private readonly route = inject(ActivatedRoute);
  private readonly projects = inject(ProjectService);

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
}
