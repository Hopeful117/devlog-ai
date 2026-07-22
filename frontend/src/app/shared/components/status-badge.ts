import { Component, Input } from '@angular/core';

export type BadgeValue = 'PROPOSED' | 'ACCEPTED' | 'REJECTED' | 'INFO' | 'WARNING' | 'CRITICAL';

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="'badge ' + tone" [attr.aria-label]="label">{{
    value
  }}</span>`,
})
export class StatusBadge {
  @Input({ required: true }) value: BadgeValue = 'INFO';

  get tone(): string {
    switch (this.value) {
      case 'ACCEPTED':
        return 'badge--success';
      case 'PROPOSED':
      case 'WARNING':
        return 'badge--warning';
      case 'REJECTED':
      case 'CRITICAL':
        return 'badge--danger';
      case 'INFO':
        return 'badge--info';
    }
  }

  get label(): string {
    return `${this.value.toLowerCase()} status`;
  }
}
