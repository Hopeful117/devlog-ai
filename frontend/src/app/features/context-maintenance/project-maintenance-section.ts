import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input, OnChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  catchError,
  map,
  Observable,
  of,
  ReplaySubject,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { DashboardCard } from '../../shared/components/dashboard-card';
import {
  MaintenanceFindingActionRequest,
  MaintenanceContextSurface,
  MaintenanceFinding,
  MaintenanceFindingSeverity,
  MaintenanceFindingStatus,
  MaintenanceSuggestedActionCategory,
  MaintenanceAssessmentConfidenceLevel,
  MaintenanceAssessmentSemanticClassification,
  MaintenanceAssessmentRecommendedAction,
} from './maintenance-finding.models';
import { MaintenanceFindingService } from './maintenance-finding.service';

type MaintenanceViewState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly findings: readonly MaintenanceFinding[] }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-maintenance-section',
  imports: [AsyncPipe, DatePipe, DashboardCard, FormsModule],
  templateUrl: './project-maintenance-section.html',
  styleUrl: './project-maintenance-section.scss',
})
export class ProjectMaintenanceSection implements OnChanges {
  @Input({ required: true }) projectId = '';

  private readonly service = inject(MaintenanceFindingService);
  private readonly projectIds = new ReplaySubject<string>(1);
  private readonly reviewerId = '00000000-0000-0000-0000-000000000001';
  readonly comments: Record<string, string> = {};
  readonly pendingActions: Record<string, boolean> = {};
  readonly actionErrors: Record<string, string> = {};

  readonly view$: Observable<MaintenanceViewState> = this.projectIds.pipe(
    switchMap((projectId) =>
      this.service.getByProject(projectId).pipe(
        map((findings) => ({ state: 'loaded' as const, findings })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'project') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  ngOnChanges(): void {
    if (this.projectId) this.projectIds.next(this.projectId);
  }

  humanize(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase();
  }

  surfaceLabel(surface: MaintenanceContextSurface): string {
    if (surface === 'PROJECT_PROJECTION') return 'Projection';
    if (surface === 'INTERNAL_HUMAN_CONTEXT') return 'Human context';
    return 'Understanding';
  }

  actionLabel(action: MaintenanceSuggestedActionCategory): string {
    return this.humanize(action);
  }

  statusLabel(status: MaintenanceFindingStatus): string {
    return this.humanize(status);
  }

  severityTone(severity: MaintenanceFindingSeverity): 'high' | 'medium' | 'low' {
    if (severity === 'HIGH') return 'high';
    if (severity === 'MEDIUM') return 'medium';
    return 'low';
  }

  requiresReview(finding: MaintenanceFinding): boolean {
    return (
      finding.humanReviewRequired &&
      (finding.status === 'OPEN' || finding.status === 'ACKNOWLEDGED')
    );
  }

  supportsWorkflow(finding: MaintenanceFinding): boolean {
    return (
      finding.issueType === 'TRUSTED_KNOWLEDGE_EXACT_DUPLICATE' ||
      finding.issueType === 'TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE' ||
      finding.issueType === 'TRUSTED_KNOWLEDGE_OVERLAP_REVIEW' ||
      finding.issueType === 'STALE_HUMAN_CONTEXT_INPUT'
    );
  }

  canAcknowledge(finding: MaintenanceFinding): boolean {
    return this.supportsWorkflow(finding) && finding.status === 'OPEN';
  }

  canDismissOrResolve(finding: MaintenanceFinding): boolean {
    return (
      this.supportsWorkflow(finding) &&
      (finding.status === 'OPEN' || finding.status === 'ACKNOWLEDGED')
    );
  }

  requiresComment(finding: MaintenanceFinding): boolean {
    return this.canDismissOrResolve(finding);
  }

  commentValue(findingId: string): string {
    return this.comments[findingId] ?? '';
  }

  setComment(findingId: string, value: string): void {
    this.comments[findingId] = value;
  }

  latestActionSummary(finding: MaintenanceFinding): string | null {
    const latest = finding.actionHistory[0];
    if (!latest) return null;
    return `${this.humanize(latest.actionType)} by ${latest.actedBy}`;
  }

  latestActionComment(finding: MaintenanceFinding): string | null {
    return finding.actionHistory[0]?.comment ?? null;
  }

  classificationLabel(classification: MaintenanceAssessmentSemanticClassification): string {
    const labels: Record<MaintenanceAssessmentSemanticClassification, string> = {
      LIKELY_DUPLICATE: 'Likely Duplicate',
      LIKELY_ENRICHMENT: 'Likely Enrichment',
      UNCERTAIN: 'Uncertain',
      CORRELATED_STALENESS: 'Correlated Staleness',
      ISOLATED_SIGNAL: 'Isolated Signal',
      NOT_APPLICABLE: 'Not Applicable',
    };
    return labels[classification] ?? this.humanize(classification);
  }

  confidenceLabel(confidence: MaintenanceAssessmentConfidenceLevel): string {
    const labels: Record<MaintenanceAssessmentConfidenceLevel, string> = {
      HIGH: 'High Confidence',
      MEDIUM: 'Medium Confidence',
      LOW: 'Low Confidence',
      VERY_LOW: 'Very Low Confidence',
    };
    return labels[confidence] ?? this.humanize(confidence);
  }

  assessmentActionLabel(action: MaintenanceAssessmentRecommendedAction): string {
    return this.humanize(action);
  }

  acknowledge(finding: MaintenanceFinding): void {
    this.runAction(finding, (request) =>
      this.service.acknowledge(this.projectId, finding.id, request),
    );
  }

  dismiss(finding: MaintenanceFinding): void {
    this.runAction(
      finding,
      (request) => this.service.dismiss(this.projectId, finding.id, request),
      true,
    );
  }

  resolve(finding: MaintenanceFinding): void {
    this.runAction(
      finding,
      (request) => this.service.resolve(this.projectId, finding.id, request),
      true,
    );
  }

  private runAction(
    finding: MaintenanceFinding,
    invoke: (request: MaintenanceFindingActionRequest) => Observable<MaintenanceFinding>,
    requireComment = false,
  ): void {
    const comment = this.commentValue(finding.id).trim();
    if (requireComment && comment.length === 0) {
      this.actionErrors[finding.id] = 'A rationale is required for this action.';
      return;
    }
    this.pendingActions[finding.id] = true;
    this.actionErrors[finding.id] = '';
    invoke({ actedBy: this.reviewerId, comment }).subscribe({
      next: () => {
        this.comments[finding.id] = '';
        this.pendingActions[finding.id] = false;
        this.projectIds.next(this.projectId);
      },
      error: (error: unknown) => {
        this.pendingActions[finding.id] = false;
        this.actionErrors[finding.id] = toRequestError(error, 'project').message;
      },
    });
  }
}
