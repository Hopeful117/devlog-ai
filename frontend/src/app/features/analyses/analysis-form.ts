import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CreateAnalysisRequest,
  IntentDefinition,
  LaunchableAnalysisType,
  UserGuidance,
} from './analysis.models';

@Component({
  selector: 'app-analysis-form',
  imports: [ReactiveFormsModule],
  templateUrl: './analysis-form.html',
  styleUrl: './analysis-form.scss',
})
export class AnalysisForm {
  @Input({ required: true }) projectId = '';
  @Input({ required: true }) intents: readonly IntentDefinition[] = [];
  @Input() submitting = false;
  @Output() readonly launch = new EventEmitter<CreateAnalysisRequest>();

  readonly types: readonly LaunchableAnalysisType[] = ['ARCHITECTURE_REVIEW', 'PROJECT_EVOLUTION'];
  readonly form = new FormGroup({
    type: new FormControl<LaunchableAnalysisType>('ARCHITECTURE_REVIEW', {
      nonNullable: true,
      validators: Validators.required,
    }),
    intentKey: new FormControl('', { nonNullable: true, validators: Validators.required }),
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

  get selectedIntent(): IntentDefinition | undefined {
    return this.intents.find(
      (intent) => `${intent.id}-${intent.version}` === this.form.controls.intentKey.value,
    );
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
      type: value.type,
      intentId: value.intentKey,
    };
    const revision = value.targetRevision.trim();
    this.launch.emit({
      ...request,
      ...(revision ? { targetRevision: revision } : {}),
      ...(hasGuidance ? { userGuidance: guidance } : {}),
    });
  }
}
