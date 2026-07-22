import { TestBed } from '@angular/core/testing';

import { SourceForm } from './source-form';

describe('SourceForm', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SourceForm] }).compileComponents();
  });

  it('requires a name and a valid Git repository location', () => {
    const fixture = TestBed.createComponent(SourceForm);
    const component = fixture.componentInstance;

    component.form.controls.name.setValue('core');
    component.form.controls.repositoryUrl.setValue('not a repository URL');
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.repositoryUrl.hasError('gitLocation')).toBe(true);

    component.form.controls.repositoryUrl.setValue('git@example.test:team/core.git');
    expect(component.form.valid).toBe(true);
  });

  it('emits the typed Git creation request and omits blank option values as null', () => {
    const fixture = TestBed.createComponent(SourceForm);
    const component = fixture.componentInstance;
    component.projectId = 'a1ee6d55-e034-491a-a6e6-cdad70573b24';
    const emitted = vi.fn();
    component.createSource.subscribe(emitted);
    component.form.setValue({
      name: ' core ',
      repositoryUrl: ' https://example.test/core.git ',
      defaultBranch: '',
      provider: '',
    });

    component.submit();

    expect(emitted).toHaveBeenCalledWith({
      projectId: component.projectId,
      type: 'GIT_REPOSITORY',
      name: 'core',
      repositoryUrl: 'https://example.test/core.git',
      defaultBranch: null,
      provider: null,
    });
  });
});
