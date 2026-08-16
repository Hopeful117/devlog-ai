import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { MarkdownModule, MarkdownService } from 'ngx-markdown';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  catchError,
  exhaustMap,
  map,
  Observable,
  of,
  scan,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import {
  CreateProjectHumanContextInputRequest,
  ProjectHumanContextInput,
  ProjectHumanContextInputType,
} from './project-context-input.models';
import { ProjectContextInputService } from './project-context-input.service';

type ContextInputsViewState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly inputs: readonly ProjectHumanContextInput[] }
  | { readonly state: 'error'; readonly error: RequestError };

type MutationAction =
  | { readonly kind: 'create'; readonly request: CreateProjectHumanContextInputRequest }
  | { readonly kind: 'archive'; readonly input: ProjectHumanContextInput };

type MutationEvent =
  | { readonly state: 'pending'; readonly action: MutationAction }
  | { readonly state: 'success'; readonly action: MutationAction; readonly message: string }
  | { readonly state: 'error'; readonly action: MutationAction; readonly error: RequestError };

type MutationViewState =
  | { readonly state: 'idle'; readonly resetToken: number }
  | (MutationEvent & { readonly resetToken: number });

const initialMutationState: MutationViewState = { state: 'idle', resetToken: 0 };

function nonBlankTrimmed(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() === '' ? { required: true } : null;
}

@Component({
  selector: 'app-project-context-inputs-section',
  imports: [AsyncPipe, DatePipe, ReactiveFormsModule, MarkdownModule],
  templateUrl: './project-context-inputs-section.html',
  styleUrl: './project-context-inputs-section.scss',
  providers: [MarkdownService],
})
export class ProjectContextInputsSection {
  @Input({ required: true }) projectId = '';

  private readonly service = inject(ProjectContextInputService);
  private readonly refresh = new Subject<void>();
  private readonly mutations = new Subject<MutationAction>();

  readonly inputTypes: readonly ProjectHumanContextInputType[] = [
    'GOAL',
    'CONSTRAINT',
    'ASSUMPTION',
    'KNOWN_GAP',
    'DOMAIN_CONTEXT',
  ];

  readonly form = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, nonBlankTrimmed, Validators.maxLength(255)],
    }),
    type: new FormControl<ProjectHumanContextInputType>('GOAL', { nonNullable: true }),
    contentMarkdown: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, nonBlankTrimmed, Validators.maxLength(20000)],
    }),
  });

  readonly inputsViewModel$: Observable<ContextInputsViewState> = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.service.getByProject(this.projectId).pipe(
        map((inputs) => ({ state: 'loaded' as const, inputs })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'project') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly mutationViewModel$: Observable<MutationViewState> = this.mutations.pipe(
    exhaustMap((action) => {
      const mutation$ =
        action.kind === 'create'
          ? this.service.create(this.projectId, action.request)
          : this.service.archive(this.projectId, action.input.id);

      return mutation$.pipe(
        tap(() => {
          this.refresh.next();
          if (action.kind === 'create') {
            this.form.reset({ title: '', type: 'GOAL', contentMarkdown: '' });
          }
        }),
        map((): MutationEvent => ({
          state: 'success',
          action,
          message:
            action.kind === 'create'
              ? 'Project note saved successfully.'
              : `"${action.input.title}" archived successfully.`,
        })),
        catchError((error: unknown) =>
          of<MutationEvent>({
            state: 'error',
            action,
            error: toRequestError(error, 'project'),
          }),
        ),
        startWith<MutationEvent>({ state: 'pending', action }),
      );
    }),
    scan<MutationEvent, MutationViewState>((current, event) => {
      const resetToken =
        event.state === 'success' && event.action.kind === 'create'
          ? current.resetToken + 1
          : current.resetToken;
      return { ...event, resetToken };
    }, initialMutationState),
    startWith(initialMutationState),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.mutations.next({
      kind: 'create',
      request: {
        title: value.title.trim(),
        type: value.type,
        contentMarkdown: value.contentMarkdown.trim(),
      },
    });
  }

  archive(input: ProjectHumanContextInput): void {
    this.mutations.next({ kind: 'archive', input });
  }

  humanizeType(type: ProjectHumanContextInputType): string {
    return type.replaceAll('_', ' ');
  }
}
