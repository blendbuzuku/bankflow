import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Toasts } from '../../core/services/toasts';

import {
  Reconciliation,
  ReconciliationBreak,
  TransactionService,
  TrialBalance,
} from '../../core/services/transaction';

/**
 * End-of-day balancing for operations.
 *
 * Two proofs, shown side by side because they answer different questions and
 * one is not a substitute for the other. The trial balance says the ledger adds
 * up. Reconciliation says the ledger describes what actually happened — and a
 * movement that never reached the ledger leaves both its sides missing, so the
 * trial balance still reads "balanced" while the books are wrong.
 */
@Component({
  selector: 'app-end-of-day',
  imports: [DecimalPipe, FormsModule],
  templateUrl: './end-of-day.html',
  styleUrl: './end-of-day.css',
})
export class EndOfDay implements OnInit {

  private readonly transactionService = inject(TransactionService);
  private readonly toasts = inject(Toasts);

  readonly trialBalance = signal<TrialBalance | null>(null);
  readonly reconciliation = signal<Reconciliation | null>(null);

  readonly loading = signal(true);
  readonly closing = signal(false);
  readonly error = signal('');
  readonly closeResult = signal<string>('');

  /** Defaults to today, but any past day can be re-proved. */
  date = new Date().toISOString().slice(0, 10);

  ngOnInit(): void {
    this.load();
  }

  load(): void {

    this.loading.set(true);
    this.error.set('');
    this.closeResult.set('');

    this.transactionService.trialBalance(this.date).subscribe({
      next: balance => {
        this.trialBalance.set(balance);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the trial balance.');
        this.toasts.error('Could not load the trial balance');
        this.loading.set(false);
      },
    });

    this.transactionService.reconciliation(this.date).subscribe({
      next: result => this.reconciliation.set(result),
      error: () => this.reconciliation.set(null),
    });
  }

  close(): void {

    this.closing.set(true);
    this.error.set('');
    this.closeResult.set('');

    this.transactionService.closeDay(this.date).subscribe({
      next: result => {
        this.closing.set(false);
        this.trialBalance.set(result.trialBalance);
        this.reconciliation.set(result.reconciliation);

        this.closeResult.set(
          result.closed
            ? `${result.bookingDate} closed. Both proofs passed and the outcome is recorded.`
            : `${result.bookingDate} did not close. The outcome is recorded and the breaks are listed below.`,
        );

        /*
         * A day that would not close is not an error — the close ran, and its
         * answer was no. Reported as a warning the user must dismiss, because
         * unbalanced books are the one thing that must not scroll past.
         */
        if (result.closed) {
          this.toasts.success(
            `${result.bookingDate} closed`,
            'Trial balance nets to zero and reconciliation found no breaks.',
          );
        } else {
          this.toasts.error(
            `${result.bookingDate} did not close`,
            'The books do not prove out. The breaks are listed on the page.',
          );
        }
      },
      error: () => {
        this.closing.set(false);
        this.error.set('The day could not be closed.');
        this.toasts.error('The day could not be closed');
      },
    });
  }

  /** True only when both proofs pass. */
  isClean(): boolean {
    return !!this.trialBalance()?.balanced
      && !!this.reconciliation()?.reconciled;
  }

  allBreaks(): { kind: string; item: ReconciliationBreak }[] {

    const r = this.reconciliation();

    if (!r) {
      return [];
    }

    return [
      ...r.unrecordedMoves.map(item => ({ kind: 'Money moved, no ledger entry', item })),
      ...r.unmatchedLedger.map(item => ({ kind: 'Ledger entry, no movement', item })),
      ...r.amountMismatch.map(item => ({ kind: 'Ledger and movement disagree', item })),
    ];
  }
}
