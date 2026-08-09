import { Injectable } from '@angular/core';

const KEY = 'devlog.proposal-review.reviewer.v1';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

@Injectable({ providedIn: 'root' })
export class ProposalReviewerSessionService {
  private fallback: string | null = null;
  get(): string | null {
    try {
      const value = sessionStorage.getItem(KEY);
      if (value && UUID.test(value)) return value;
      if (value) sessionStorage.removeItem(KEY);
    } catch {
      /* browser storage can be unavailable */
    }
    return this.fallback;
  }
  set(value: string): boolean {
    if (!UUID.test(value)) return false;
    this.fallback = value;
    try {
      sessionStorage.setItem(KEY, value);
    } catch {
      /* memory fallback remains */
    }
    return true;
  }
  clear(): void {
    this.fallback = null;
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      /* nothing else to clear */
    }
  }
}
