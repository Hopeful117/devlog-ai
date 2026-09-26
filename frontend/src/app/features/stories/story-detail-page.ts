import { AsyncPipe, JsonPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, switchMap, throwError } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { ProjectService } from '../projects/project.service';
import { EngineeringStory, StoryChangeBriefing } from './story.models';
import { StoryService } from './story.service';
type StoryDetailView =
  | { readonly state: 'loading' }
  | {
      readonly state: 'loaded';
      readonly story: EngineeringStory;
      readonly briefing: StoryChangeBriefing;
    }
  | { readonly state: 'error'; readonly message: string };

interface StoryDetailLoadError {
  readonly source: 'story' | 'briefing';
}
@Component({
  imports: [AsyncPipe, JsonPipe, RouterLink],
  templateUrl: './story-detail-page.html',
  styleUrl: './story-detail-page.scss',
})
export class StoryDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly storyService = inject(StoryService);
  readonly view$ = this.route.paramMap.pipe(
    switchMap((params) => {
      const projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
      const storyId = params.get('storyId') ?? '';
      return this.projectService.getProject(projectId).pipe(
        switchMap((project) =>
          forkJoin({
            story: this.storyService
              .get(project.id, storyId)
              .pipe(
                catchError(() =>
                  throwError(() => ({ source: 'story' }) satisfies StoryDetailLoadError),
                ),
              ),
            briefing: this.storyService
              .getChangeBriefing(project.slug, storyId)
              .pipe(
                catchError(() =>
                  throwError(() => ({ source: 'briefing' }) satisfies StoryDetailLoadError),
                ),
              ),
          }),
        ),
      );
    }),
    map(({ story, briefing }): StoryDetailView => ({ state: 'loaded', story, briefing })),
    catchError((error: unknown) => {
      const source = (error as Partial<StoryDetailLoadError>)?.source;
      return of<StoryDetailView>({
        state: 'error',
        message:
          source === 'story'
            ? 'Unable to load the story. Please try again.'
            : source === 'briefing'
              ? 'Unable to load the story change briefing. Please try again.'
              : toRequestError(error).message,
      });
    }),
    startWith<StoryDetailView>({ state: 'loading' }),
  );
}
