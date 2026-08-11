import { TestBed } from '@angular/core/testing';
import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders the dashboard heading', async () => {
    await TestBed.configureTestingModule({ imports: [DashboardPage] }).compileComponents();
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('Dashboard');
  });
});