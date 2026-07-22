import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CreateDeliverableRequest, DeliverableType } from './deliverable.models';

@Component({
  selector: 'app-deliverable-form',
  imports: [ReactiveFormsModule],
  templateUrl: './deliverable-form.html',
  styleUrl: './deliverable-form.scss',
})
export class DeliverableForm {
  @Input({ required: true }) projectId = '';
  @Input() analysisId: string | null = null;
  @Input() submitting = false;
  @Output() readonly generate = new EventEmitter<CreateDeliverableRequest>();
  readonly types: readonly DeliverableType[] = [
    'PROJECT_DESCRIPTION',
    'README',
    'ARCHITECTURE_SUMMARY',
    'PORTFOLIO_DESCRIPTION',
    'TECHNICAL_SUMMARY',
    'BLOG_ARTICLE',
  ];
  readonly form = new FormGroup({
    type: new FormControl<DeliverableType>('PROJECT_DESCRIPTION', {
      nonNullable: true,
      validators: Validators.required,
    }),
    audience: new FormControl('Software engineers', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    style: new FormControl('Technical and concise', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    language: new FormControl('en', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(20)],
    }),
    additionalGuidance: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(1000),
    }),
  });
  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const guidance = value.additionalGuidance.trim();
    this.generate.emit({
      projectId: this.projectId,
      ...(this.analysisId ? { analysisId: this.analysisId } : {}),
      type: value.type,
      audience: value.audience.trim(),
      style: value.style.trim(),
      language: value.language.trim(),
      ...(guidance ? { additionalGuidance: guidance } : {}),
    });
  }
}
