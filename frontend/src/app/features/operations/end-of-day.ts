import { Component, OnInit, computed, inject, signal } from '@angular/core';
import {
  DatePipe,
  DecimalPipe,
  formatDate,
  formatNumber,
} from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { Toasts } from '../../core/services/toasts';
import { Confirm, ConfirmFact } from '../../core/services/confirm';

import {
  BusinessDate,
  DayCloseRecord,
  DayCloseResult,
  DaySummary,
  Reconciliation,
  ReconciliationBreak,
  TransactionService,
  TrialBalance,
} from '../../core/services/transaction';

/** Where the bank stands against the calendar. */
type Standing = 'behind' | 'inStep' | 'ahead';

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
  private readonly confirm = inject(Confirm);

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

  /** What the bank says about its own date, once it has told us. */
  readonly business = signal<BusinessDate | null>(null);

  readonly tradingInto = computed(() => this.business()?.tradingInto ?? null);

  /**
   * Behind, in step, or ahead of the calendar.
   *
   * Ahead is the ordinary state after tonight's close: the bank trades into
   * tomorrow while the wall still says today. Behind is the one to act on —
   * days that should have been proved and signed off, and were not.
   */
  readonly standing = computed<Standing | null>(() => {

    const b = this.business();

    if (!b) {
      return null;
    }

    if (b.overdueDays.length) {
      return 'behind';
    }

    return b.tradingInto > b.calendarDate ? 'ahead' : 'inStep';
  });

  ngOnInit(): void {

    this.transactionService.businessDate().subscribe({
      next: business => {
        this.business.set(business);
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

  /**
   * Whether the open day is still in the future.
   *
   * True for the rest of the evening once today is closed. The bank is
   * trading into tomorrow, correctly, but tomorrow cannot be signed off
   * before any of it has happened — the server refuses, so the button does
   * not offer.
   */
  isNotStarted(): boolean {
    const b = this.business();
    return !!b && this.date === b.tradingInto && b.tradingInto > b.calendarDate;
  }

  /**
   * Why the day on screen cannot be closed, when it is open but not the one
   * to close. Empty when it can be.
   */
  blockedReason(): string {

    const b = this.business();

    if (!b || this.isClosed()) {
      return '';
    }

    if (this.date === b.tradingInto) {
      return this.isNotStarted()
        ? `${this.shortDay(this.date)} has not started yet`
        : '';
    }

    /*
     * Days are closed in order, so a later one waits for the open one — the
     * reason is which day comes first, not merely that this is not it.
     */
    if (this.date > b.tradingInto) {
      return `Close ${this.shortDay(b.tradingInto)} first`;
    }

    return `The bank is trading into ${this.shortDay(b.tradingInto)}`;
  }

  /** Moves the screen to a day, as the date picker would. */
  goTo(day: string): void {
    this.date = day;
    this.load();
  }

  /** "Tue 8 Sep 2026" — for sentences where the weekday helps. */
  day(date: string | null | undefined): string {
    return date ? formatDate(date, 'EEE d MMM yyyy', 'en-US') : '';
  }

  /** "8 Sep" — for buttons and chips, where the year is noise. */
  shortDay(date: string | null | undefined): string {
    return date ? formatDate(date, 'd MMM', 'en-US') : '';
  }

  private money(value: number): string {
    return formatNumber(value, 'en-US', '1.2-2');
  }

  /** The day after, which a successful close moves the bank on to. */
  private nextDay(date: string): string {

    const [y, m, d] = date.split('-').map(Number);
    const next = new Date(y, m - 1, d + 1);

    return formatDate(next, 'yyyy-MM-dd', 'en-US');
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

  /**
   * Asks before sealing a day, showing exactly what is being sealed.
   *
   * A close cannot be undone, so the question carries the figures being
   * accepted, what is still open and will carry forward, and what the bank
   * does next — the things a person signing off a day is actually vouching
   * for.
   */
  close(): void {

    const day = this.date;
    const next = this.nextDay(day);
    const b = this.business();

    const facts: ConfirmFact[] = [{ label: 'Day', value: this.day(day) }];

    for (const c of this.trialBalance()?.currencies ?? []) {
      facts.push({
        label: `${c.currency} ledger`,
        value: `${this.money(c.totalDebits)} debits = `
          + `${this.money(c.totalCredits)} credits`,
      });
    }

    const r = this.reconciliation();

    if (r) {
      facts.push({
        label: 'Reconciliation',
        value: `${r.matched} ${r.matched === 1 ? 'movement' : 'movements'} `
          + 'matched, no breaks',
      });
    }

    const summary = this.summary();

    for (const o of summary?.outstanding ?? []) {
      facts.push({ label: 'Still open', value: o.description });
    }

    if (summary?.inFlight.length) {
      facts.push({
        label: 'In flight',
        value: `${summary.inFlight.length} `
          + `${summary.inFlight.length === 1 ? 'payment' : 'payments'}, `
          + `${this.money(this.inFlightTotal())} awaiting the scheme`,
      });
    }

    facts.push({ label: 'Afterwards', value: `The bank trades into ${this.day(next)}` });

    /*
     * The consequence depends on where this day sits. Closing an overdue day
     * leaves the rest still to do; closing today sends the rest of today's
     * business into tomorrow, which surprises people who have not thought
     * about it.
     */
    const stillOverdue = (b?.overdueDays ?? []).filter(d => d !== day);

    let note = `This cannot be undone. Nothing more can be booked into `
      + `${this.shortDay(day)} once it is closed.`;

    if (stillOverdue.length) {
      note += ` ${this.list(stillOverdue)} will still need closing after this.`;
    } else if (b && day === b.calendarDate) {
      note += ` Anything done for the rest of today will book to `
        + `${this.shortDay(next)}.`;
    }

    this.confirm.askThen({
      title: `Close ${this.day(day)}?`,
      message: 'Both proofs pass. Closing signs the day off with these '
        + 'figures and moves the bank on to the next day.',
      facts,
      note,
      confirmLabel: `Close ${this.shortDay(day)}`,
      cancelLabel: 'Not yet',
    }, () => this.sealDay(day));
  }

  private sealDay(day: string): void {

    this.closing.set(true);
    this.error.set('');
    this.closeResult.set('');

    this.transactionService.closeDay(day).subscribe({
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
        if (!result.closed) {
          this.confirm.inform({
            title: `${this.day(result.bookingDate)} was not closed`,
            message: result.summary,
            note: 'Nothing was sealed and the bank has not moved on. The '
              + 'breaks are listed below — correct them and close again.',
            confirmLabel: 'Review the breaks',
            danger: true,
          });

          this.loadSummary(this.date);
          return;
        }

        /*
         * Ask the bank where it stands now, rather than working it out here:
         * whether days are still overdue depends on the calendar as well as
         * on this close.
         */
        this.transactionService.businessDate().subscribe({
          next: business => {
            this.business.set(business);
            this.announceClose(result, business);

            /*
             * Follow the bank forward. A close moves the open day on, and
             * leaving the screen on the day just sealed would show a sign-off
             * as the thing still to be done.
             */
            this.date = business.tradingInto;
            this.load();
          },
          error: () => {
            this.date = result.tradingInto;
            this.load();
          },
        });
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

  /**
   * Says which day was just closed and what is left.
   *
   * A dialog rather than a toast: when days are still overdue, the next thing
   * to do is close another one, and that is not a message to let fade while
   * somebody is looking elsewhere.
   */
  private announceClose(result: DayCloseResult, business: BusinessDate): void {

    const facts: ConfirmFact[] = [
      { label: 'Closed', value: this.day(result.bookingDate) },
    ];

    if (result.record) {
      facts.push({
        label: 'Signed off by',
        value: `${result.record.closedByUsername} at `
          + formatDate(result.record.closedAt, 'HH:mm', 'en-US'),
      });
      facts.push({
        label: 'Entries',
        value: `${result.record.entryCount} `
          + `${result.record.entryCount === 1 ? 'entry' : 'entries'}, `
          + `${result.record.matchedMovements} matched`,
      });
    }

    facts.push({ label: 'Now trading into', value: this.day(business.tradingInto) });

    const remaining = business.overdueDays;

    if (remaining.length) {

      const behind = remaining.length === 1 ? '1 day' : `${remaining.length} days`;

      this.confirm.inform({
        title: `${this.day(result.bookingDate)} is closed`,
        message: `The bank is still ${behind} behind the calendar.`,
        facts,
        note: `Still to close: ${this.list(remaining)}. Close `
          + `${this.shortDay(remaining[0])} next — days are closed in order.`,
        confirmLabel: `Review ${this.shortDay(remaining[0])}`,
      });

      return;
    }

    this.confirm.inform({
      title: `${this.day(result.bookingDate)} is closed`,
      message: business.tradingInto > business.calendarDate
        ? `Today's books are closed. Business done from now on books to `
          + `${this.day(business.tradingInto)}.`
        : `The bank is in step with the calendar. `
          + `${this.day(business.tradingInto)} closes at the end of business.`,
      facts,
      confirmLabel: 'Done',
    });
  }

  /** "8 Sep", "8 Sep and 9 Sep", "8 Sep, 9 Sep and 10 Sep". */
  private list(days: string[]): string {

    const named = days.map(d => this.shortDay(d));

    return named.length <= 1
      ? named.join('')
      : `${named.slice(0, -1).join(', ')} and ${named[named.length - 1]}`;
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
