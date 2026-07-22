import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AppLayout } from './app-layout';

@Component({ template: '' })
class TestPage {}

describe('AppLayout', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppLayout],
      providers: [provideRouter([{ path: 'projects', component: TestPage }])],
    }).compileComponents();
  });

  it('provides labelled navigation and an active route state', async () => {
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(AppLayout);
    fixture.detectChanges();
    await router.navigateByUrl('/projects');
    await fixture.whenStable();
    fixture.detectChanges();

    const navigation = fixture.nativeElement.querySelector('nav[aria-label="Primary navigation"]');
    const projects = navigation?.querySelector('a[href="/projects"]');
    expect(navigation).toBeTruthy();
    expect(projects?.classList).toContain('active');
  });
});
