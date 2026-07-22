import { TestBed } from '@angular/core/testing';
import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  beforeEach(async () =>
    TestBed.configureTestingModule({ imports: [StatusBadge] }).compileComponents(),
  );
  it.each([
    ['PROPOSED', 'badge--warning'],
    ['ACCEPTED', 'badge--success'],
    ['REJECTED', 'badge--danger'],
    ['INFO', 'badge--info'],
    ['WARNING', 'badge--warning'],
    ['CRITICAL', 'badge--danger'],
  ] as const)('maps %s deterministically', (value, cssClass) => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentInstance.value = value;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.badge')?.classList).toContain(cssClass);
    expect(fixture.nativeElement.textContent).toContain(value);
  });
});
