import { Injectable, inject, signal } from '@angular/core';

import { AuthService } from './auth';
import { TransactionService } from './transaction';

/**
 * What is waiting for somebody, counted in one place.
 *
 * The badges used to be state on the shell component, refreshed when the
 * router navigated. That works for arriving at a screen and not for acting on
 * one: answering the last item in a queue leaves the badge claiming it is
 * still there, because nothing navigated. The count only corrected itself on
 * a page refresh, which is the one thing a person will not think to do.
 *
 * Held here so a screen that changes a queue can say so, without the shell and
 * the screen having to know about each other.
 */
@Injectable({ providedIn: 'root' })
export class WorkQueues {

  private readonly authService = inject(AuthService);
  private readonly transactions = inject(TransactionService);

  /** Payments waiting for a second person. */
  readonly approvals = signal(0);

  /** Recalls waiting for an answer. */
  readonly recalls = signal(0);

  /**
   * Payments the scheme has not answered.
   *
   * Only the unanswered ones. A settled payment can still be returned, but
   * nobody is waiting on that -- a badge that never reached zero would stop
   * being read.
   */
  readonly scheme = signal(0);

  /**
   * Re-counts everything.
   *
   * A failure to read a count is not worth interrupting anybody over: a badge
   * is a prompt, and a wrong prompt is better than an error message in front
   * of whatever the person was actually doing. So each call fails quietly to
   * zero rather than surfacing.
   */
  refresh(): void {

    if (!this.authService.isStaff()) {
      this.clear();
      return;
    }

    this.transactions.awaitingApproval().subscribe({
      next: pending => this.approvals.set(pending.length),
      error: () => this.approvals.set(0),
    });

    this.transactions.openRecalls().subscribe({
      next: open => this.recalls.set(open.length),
      error: () => this.recalls.set(0),
    });

    // Only the roles that may play the scheme are offered the endpoint.
    if (this.authService.canApprove()) {
      this.transactions.schemeQueue().subscribe({
        next: queue => this.scheme.set(queue.awaitingStatus.length),
        error: () => this.scheme.set(0),
      });
    } else {
      this.scheme.set(0);
    }
  }

  clear(): void {
    this.approvals.set(0);
    this.recalls.set(0);
    this.scheme.set(0);
  }
}
