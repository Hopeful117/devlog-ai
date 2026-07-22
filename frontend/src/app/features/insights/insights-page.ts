import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, Subject, switchMap } from 'rxjs';
import { toRequestError } from '../../core/http/request-error';
import { InsightService } from './insight.service';
import { InsightSeverity, InsightType } from './insight.models';
import { StatusBadge } from '../../shared/components/status-badge';
@Component({
  selector: 'app-insights-page',
  imports: [AsyncPipe, DatePipe, ReactiveFormsModule, RouterLink, StatusBadge],
  templateUrl: './insights-page.html',
  styleUrl: './insights-page.scss',
})
export class InsightsPage {
  private readonly service = inject(InsightService);
  private readonly filters = new Subject<{
    projectId: string;
    type?: InsightType;
    severity?: InsightSeverity;
  }>();
  readonly types: readonly InsightType[] = [
    'ARCHITECTURAL',
    'DOCUMENTATION',
    'TECHNOLOGY',
    'EVOLUTION',
    'TECHNICAL_DEBT',
    'SECURITY',
    'RISK',
    'RECOMMENDATION',
  ];
  readonly severities: readonly InsightSeverity[] = ['INFO', 'WARNING', 'CRITICAL'];
  readonly form = new FormGroup({
    projectId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    type: new FormControl<InsightType | ''>('', { nonNullable: true }),
    severity: new FormControl<InsightSeverity | ''>('', { nonNullable: true }),
  });
  readonly view$ = this.filters.pipe(
    switchMap((filter) =>
      this.service.getInsightsByProject(filter.projectId, filter.type, filter.severity).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'insight') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    startWith({ state: 'idle' as const }),
  );
  apply(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.filters.next({
      projectId: value.projectId.trim(),
      ...(value.type ? { type: value.type } : {}),
      ...(value.severity ? { severity: value.severity } : {}),
    });
  }
}
