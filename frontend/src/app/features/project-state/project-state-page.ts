import { AsyncPipe, DatePipe } from '@angular/common';
import { MarkdownModule, MarkdownService } from 'ngx-markdown';
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { catchError, map, of, shareReplay, startWith, switchMap } from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { ProjectService } from '../projects/project.service';
import { ProposalSummary } from './project-state.models';
import { ProjectStateService } from './project-state.service';

type OverviewViewState =
  | { readonly state: 'loading' }
  | {
      readonly state: 'loaded';
      readonly project: {
        readonly id: string;
        readonly name: string;
        readonly description: string | null;
      };
      readonly projectState: import('./project-state.models').ProjectState;
    }
  | { readonly state: 'not-found' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-state-page',
  imports: [AsyncPipe, DatePipe, MarkdownModule],
  providers: [MarkdownService],
  templateUrl: './project-state-page.html',
  styleUrl: './project-state-page.scss',
})
export class ProjectStatePage {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly stateService = inject(ProjectStateService);

  readonly viewModel$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((identifier) =>
      this.projectService.getProject(identifier).pipe(
        switchMap((project) =>
          this.stateService.getProjectState(project.id).pipe(
            map((projectState) => ({ state: 'loaded' as const, project, projectState })),
            catchError((error: unknown) =>
              of<OverviewViewState>({ state: 'error' as const, error: toRequestError(error) }),
            ),
          ),
        ),
        catchError((error: unknown) => {
          const requestError = toRequestError(error);
          return requestError.kind === 'not-found'
            ? of<OverviewViewState>({ state: 'not-found' as const })
            : of<OverviewViewState>({ state: 'error' as const, error: requestError });
        }),
        startWith<OverviewViewState>({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  proposalPrimaryLabel(proposal: ProposalSummary): string | null {
    const title = proposal.title?.trim();
    if (title) return title;

    const insightType = proposal.insightType?.trim();
    if (insightType) return this.humanizeToken(insightType);

    const type = proposal.type.trim();
    if (type && type !== 'INSIGHT') return this.humanizeToken(type);

    return null;
  }

  proposalHasMeaningfulDetail(proposal: ProposalSummary): boolean {
    return this.proposalPrimaryLabel(proposal) !== null;
  }

  displayableProposals(proposals: readonly ProposalSummary[]): readonly ProposalSummary[] {
    return proposals.filter((proposal) => this.proposalHasMeaningfulDetail(proposal));
  }

  proposalSecondaryLabel(proposal: ProposalSummary): string | null {
    const title = proposal.title?.trim();
    const insightType = proposal.insightType?.trim();
    if (!title || !insightType) return null;
    return this.humanizeToken(insightType);
  }

  proposalConfidenceLabel(proposal: ProposalSummary): string | null {
    if (proposal.confidence === null) return null;
    const normalized = proposal.confidence <= 1 ? proposal.confidence * 100 : proposal.confidence;
    return `${Math.round(normalized)}% confidence`;
  }

  humanContextPreview(contentMarkdown: string | null | undefined): string {
    if (!contentMarkdown) return '';

    let text = contentMarkdown;

    // Remove heading markers (#, ##, ###...)
    text = text.replace(/^#+\s+/gm, '');

    // Remove bold **text** or __text__
    text = text.replace(/\*\*([^*]+)\*\*/g, '$1');

    // Remove italic *text* or _text_ (but not ** which is bolder)
    text = text.replace(/\*([^*]+)\*/g, '$1');
    text = text.replace(/_([^_]+)_/g, '$1');

    // Remove inline code `code`
    text = text.replace(/`([^`]+)`/g, '$1');

    // Remove unordered list markers - or * at line start
    text = text.replace(/^[-*]\s+/gm, '');

    // Remove ordered list markers 1. 2. at line start
    text = text.replace(/^\d+\.\s+/gm, '');

    // Remove blockquote markers > at line start
    text = text.replace(/^>\s*/gm, '');

    // Remove Markdown links [text](url) → keep text
    text = text.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');

    // Normalize whitespace: collapse multiple spaces/newlines to single space
    text = text.replace(/\s+/g, ' ').trim();

    // Truncate if longer than 180 chars (preserving existing behavior)
    if (text.length <= 180) return text;
    return `${text.slice(0, 177).trimEnd()}...`;
  }

  private humanizeToken(value: string): string {
    return value.replaceAll('_', ' ');
  }
}
