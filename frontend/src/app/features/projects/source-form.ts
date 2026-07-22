import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';

import { CreateSourceRequest, GitProvider } from './source.models';

export interface GitSourceFormValue {
  readonly name: string;
  readonly repositoryUrl: string;
  readonly defaultBranch: string;
  readonly provider: GitProvider | '';
}

const GIT_LOCATION_PATTERN =
  /^(https?:\/\/|ssh:\/\/|git:\/\/|file:\/\/|\/|\.\.?\/|[^\s@]+@[^\s:]+:)[^\s]+$/i;

function gitLocation(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return value === '' || GIT_LOCATION_PATTERN.test(value) ? null : { gitLocation: true };
}

@Component({
  selector: 'app-source-form',
  imports: [ReactiveFormsModule],
  templateUrl: './source-form.html',
  styleUrl: './source-form.scss',
})
export class SourceForm implements OnChanges {
  @Input({ required: true }) projectId = '';
  @Input() submitting = false;
  @Input() resetToken = 0;
  @Output() readonly createSource = new EventEmitter<CreateSourceRequest>();

  readonly providers: readonly GitProvider[] = [
    'GITHUB',
    'GITLAB',
    'BITBUCKET',
    'AZURE_DEVOPS',
    'GENERIC_GIT',
  ];

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255)],
    }),
    repositoryUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(2000), gitLocation],
    }),
    defaultBranch: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(255)],
    }),
    provider: new FormControl<GitProvider | ''>('', { nonNullable: true }),
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['resetToken'] && !changes['resetToken'].firstChange) {
      this.form.reset();
    }
  }

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }

    const value: GitSourceFormValue = this.form.getRawValue();
    this.createSource.emit({
      projectId: this.projectId,
      type: 'GIT_REPOSITORY',
      name: value.name.trim(),
      repositoryUrl: value.repositoryUrl.trim(),
      defaultBranch: value.defaultBranch.trim() || null,
      provider: value.provider || null,
    });
  }
}
