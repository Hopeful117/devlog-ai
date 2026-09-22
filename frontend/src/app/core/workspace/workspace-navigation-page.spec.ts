import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { WorkspaceNavigationPage } from './workspace-navigation-page';

@Component({ template: 'cockpit' })
class CockpitPage {}

describe('WorkspaceNavigationPage', () => {
  it('redirects a valid resource to the controlled DevLog cockpit destination', async () => {
    await TestBed.configureTestingModule({
      imports: [WorkspaceNavigationPage],
      providers: [
        provideRouter([
          { path: 'navigation', component: WorkspaceNavigationPage },
          { path: 'projects/:id', component: CockpitPage },
        ]),
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    await router.navigateByUrl(
      '/navigation?resource=devlog%3A%2F%2Fprojects%2Fdevlog-ai%2Ffreshness',
    );
    await TestBed.createComponent(WorkspaceNavigationPage).whenStable();

    expect(router.url).toBe('/projects/devlog-ai');
  });

  it('renders a bounded fallback for an unsupported resource', async () => {
    await TestBed.configureTestingModule({
      imports: [WorkspaceNavigationPage],
      providers: [provideRouter([{ path: 'navigation', component: WorkspaceNavigationPage }])],
    }).compileComponents();

    const router = TestBed.inject(Router);
    await router.navigateByUrl('/navigation?resource=https%3A%2F%2Fexample.com');
    const fixture = TestBed.createComponent(WorkspaceNavigationPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('cannot be opened here');
    expect(fixture.nativeElement.querySelector('a[href="/projects"]')).not.toBeNull();
  });
});
