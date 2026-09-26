import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { EngineeringStory, StoryChangeBriefing } from './story.models';
@Injectable({ providedIn: 'root' })
export class StoryService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;
  get(projectId: string, storyId: string): Observable<EngineeringStory> {
    return this.http.get<EngineeringStory>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/stories/${encodeURIComponent(storyId)}`,
    );
  }
  getChangeBriefing(projectSlug: string, storyId: string): Observable<StoryChangeBriefing> {
    return this.http.get<StoryChangeBriefing>(
      `${this.base}/projects/${encodeURIComponent(projectSlug)}/stories/${encodeURIComponent(storyId)}/change-briefing`,
    );
  }
}
