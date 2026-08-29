import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CreateAnalysisRequest, Source, UserGuidance } from './analysis.models';

export interface Objective {
  readonly label: string;
  readonly description: string;
  readonly intentId: string;
  readonly scope: 'PROJECT_SCOPE' | 'REPOSITORY_SCOPE';
}

@Component({
  selector: 'app-analysis-form',
  imports: [ReactiveFormsModule],
  templateUrl: './analysis-form.html',
  styleUrl: './analysis-form.scss',
})
export class AnalysisForm implements OnInit {
  @Input({ required: true }) projectId = '';
  @Input({ required: true }) objectives: readonly Objective[] = [];
  @Input({ required: true }) sources: readonly Source[] = [];
  @Input() submitting = false;
  @Output() readonly launch = new EventEmitter<CreateAnalysisRequest>();

  readonly form = new FormGroup({
    objective: new FormControl('', { nonNullable: true, validators: Validators.required }),
    sourceId: new FormControl<string | null>(null),
    targetRevision: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(255),
    }),
    focus: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }),
    audience: new FormControl('', { nonNullable: true, validators: Validators.maxLength(200) }),
    levelOfDetail: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(100),
    }),
    writingStyle: new FormControl('', { nonNullable: true, validators: Validators.maxLength(100) }),
    outputContext: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(500),
    }),
    priorities: new FormControl('', { nonNullable: true }),
  });

  get selectedObjective(): Objective | undefined {
    return this.objectives.find((obj) => obj.intentId === this.form.controls.objective.value);
  }

  get isRepositoryScope(): boolean {
    return this.selectedObjective?.scope === 'REPOSITORY_SCOPE';
  }

  get isProjectScope(): boolean {
    return this.selectedObjective?.scope === 'PROJECT_SCOPE';
  }

  ngOnInit(): void {
    this.form.controls.objective.valueChanges.subscribe((objectiveId) => {
      const obj = this.objectives.find((o) => o.intentId === objectiveId);
      if (obj && obj.scope === 'REPOSITORY_SCOPE') {
        if (this.sources.length === 1) {
          this.form.controls.sourceId.setValue(this.sources[0].id);
        } else {
          this.form.controls.sourceId.setValue(null);
        }
      } else {
        this.form.controls.sourceId.setValue(null);
      }
    });
  }

  submit(): void {
    const priorities = this.form.controls.priorities.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean);
    if (priorities.length > 10 || priorities.some((item) => item.length > 300))
      this.form.controls.priorities.setErrors({ priorities: true });
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const text = (input: string): string | null => input.trim() || null;
    const guidance: UserGuidance = {
      focus: text(value.focus),
      audience: text(value.audience),
      levelOfDetail: text(value.levelOfDetail),
      writingStyle: text(value.writingStyle),
      outputContext: text(value.outputContext),
      priorities,
    };
    const hasGuidance = Object.values(guidance).some((entry) =>
      Array.isArray(entry) ? entry.length > 0 : entry !== null,
    );
    const request: CreateAnalysisRequest = {
      projectId: this.projectId,
      intentId: value.objective,
      sourceId: value.sourceId || undefined,
    };
    const revision = value.targetRevision.trim();
    this.launch.emit({
      ...request,
      ...(revision ? { targetRevision: revision } : {}),
      ...(hasGuidance ? { userGuidance: guidance } : {}),
    });
  }
}
