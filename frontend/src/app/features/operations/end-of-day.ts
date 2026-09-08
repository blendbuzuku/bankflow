import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { Toasts } from '../../core/services/toasts';

import {
  DayCloseRecord,
  DaySummary,
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
  imports: [DecimalPipe, DatePipe, FormsModule, RouterLink],
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

  // --- what the day consisted of ---

  readonly summary = signal<DaySummary | null>(null);
  readonly history = signal<DayCloseRecord[]>([]);
  readonly closeRecord = signal<DayCloseRecord | null>(null);

  /**
   * The day on screen. Starts on the open one, but any past day can be
   * re-proved.
   *
   * Seeded from the browser only until the bank answers. It used to be seeded
   * from the browser and left there, which held right up until a day was
   * signed off — the bank moves on and the calendar does not, so the screen
   * went on offering the day it had just sealed, proving a closed day and
   * refusing to close it again.
   */
  date = new Date().toISOString().slice(0, 10);

  /** What the bank says the open day is, once it has told us. */
  readonly tradingInto = signal<string | null>(null);

  ngOnInit(): void {

    this.transactionService.businessDate().subscribe({
      next: business => {
        this.tradingInto.set(business.tradingInto);
        this.date = business.tradingInto;
        this.load();
      },

      /*
       * The calendar date is the fallback and usually right — the two only
       * differ once the bank is behind the clock. Better a day that may be
       * wrong than a screen with nothing on it.
       */
      error: () => this.load(),
    });
  }

  /** Whether the day on screen is the one the bank is trading into. */
  isOpenDay(): boolean {
    const open = this.tradingInto();
    return open === null || open === this.date;
  }

  /** Whether the day has already been signed off and sealed. */
  isClosed(): boolean {
    return !!this.summary()?.closed;
  }

  /** Money gone from a debtor and not yet answered for by the scheme. */
  inFlightTotal(): number {
    return (this.summary()?.inFlight ?? [])
      .reduce((sum, p) => sum + Number(p.amount), 0);
  }

  /** A payment in flight for days usually means an answer that never came. */
  isStale(ageInDays: number): boolean {
    return ageInDays >= 2;
  }

  private loadSummary(date: string): void {

    this.transactionService.daySummary(date).subscribe({
      next: summary => this.summary.set(summary),
      error: () => this.summary.set(null),
    });

    this.transactionService.dayHistory().subscribe({
      next: history => {
        this.history.set(history);
        this.closeRecord.set(
          history.find(d => d.bookingDate === date) ?? null,
        );
      },
      error: () => this.history.set([]),
    });
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

    this.loadSummary(this.date);
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
        this.closeResult.set(result.summary);
        this.closeRecord.set(result.record);

        /*
         * A day that would not close is not an error — the close ran and its
         * answer was no. It is reported as something to act on rather than
         * something that went wrong.
         */
        if (result.closed) {
          this.toasts.success(
            `${result.bookingDate} closed`,
            `${result.summary} Now trading into ${result.tradingInto}.`,
          );
        } else {
          this.toasts.error(
            `${result.bookingDate} did not close`,
            result.summary,
          );
        }

        /*
         * Follow the bank forward. A close moves the open day on, and leaving
         * the screen on the day just sealed would show a sign-off as the
         * thing still to be done.
         */
        if (result.closed && result.tradingInto) {
          this.tradingInto.set(result.tradingInto);
          this.date = result.tradingInto;
          this.load();
          return;
        }

        this.loadSummary(this.date);
      },
      error: error => {
        this.closing.set(false);
        this.error.set(
          error?.error?.message ?? 'The day could not be closed.',
        );
        this.toasts.failure('The day could not be closed', error);
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
