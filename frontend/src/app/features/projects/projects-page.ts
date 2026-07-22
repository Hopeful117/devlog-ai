import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, map, Observable, of, startWith } from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { ProjectSummary } from './project.models';
import { ProjectService } from './project.service';

type ProjectsViewState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly projects: readonly ProjectSummary[] }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-projects-page',
  imports: [AsyncPipe, DatePipe, RouterLink],
  templateUrl: './projects-page.html',
  styleUrl: './projects-page.scss',
})
export class ProjectsPage {
  private readonly projectService = inject(ProjectService);

  readonly viewModel$: Observable<ProjectsViewState> = this.projectService.getProjects().pipe(
    map((projects) => ({ state: 'loaded' as const, projects })),
    catchError((error: unknown) => of({ state: 'error' as const, error: toRequestError(error) })),
    startWith({ state: 'loading' as const }),
  );
}
