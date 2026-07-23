import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  catchError,
  exhaustMap,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { ProjectSummary } from './project.models';
import { ProjectService } from './project.service';

type ProjectsViewState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly projects: readonly ProjectSummary[] }
  | { readonly state: 'error'; readonly error: RequestError };
type CreationState =
  | { readonly state: 'idle' }
  | { readonly state: 'pending' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-projects-page',
  imports: [AsyncPipe, DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './projects-page.html',
  styleUrl: './projects-page.scss',
})
export class ProjectsPage {
  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);
  private readonly refresh = new Subject<void>();
  private readonly creations = new Subject<{
    readonly name: string;
    readonly description?: string;
  }>();
  showForm = false;

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(5000),
    }),
  });

  readonly viewModel$: Observable<ProjectsViewState> = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.projectService.getProjects().pipe(
        map((projects) => ({ state: 'loaded' as const, projects })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error) }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly creationState$: Observable<CreationState> = this.creations.pipe(
    exhaustMap((request) =>
      this.projectService.createProject(request).pipe(
        tap((project) => {
          this.form.reset();
          this.refresh.next();
          void this.router.navigate(['/projects', project.slug]);
        }),
        map((): CreationState => ({ state: 'idle' })),
        catchError((error: unknown) =>
          of<CreationState>({ state: 'error', error: toRequestError(error, 'project') }),
        ),
        startWith<CreationState>({ state: 'pending' }),
      ),
    ),
    startWith<CreationState>({ state: 'idle' }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  createProject(): void {
    const value = this.form.getRawValue();
    const name = value.name.trim();
    if (!name) {
      this.form.controls.name.setErrors({ required: true });
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const description = value.description.trim();
    this.creations.next({
      name,
      ...(description ? { description } : {}),
    });
  }
}
