import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, formatNumber } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth';
import { Toasts } from '../../core/services/toasts';
import { WorkQueues } from '../../core/services/work-queues';
import { Confirm } from '../../core/services/confirm';
import {
  TransactionResponse,
  TransactionService,
} from '../../core/services/transaction';

/**
 * Payments waiting on a second pair of eyes.
 *
 * Everyone on staff can see the queue, so a teller knows their payment has not
 * been forgotten, but only operations and administrators can act — and never on
 * a payment they raised themselves. The server enforces both; the screen simply
 * avoids offering a button that would be refused.
 */
@Component({
  selector: 'app-approval-queue',
  imports: [DecimalPipe, DatePipe, FormsModule, RouterLink],
  templateUrl: './approval-queue.html',
  styleUrl: './approval-queue.css',
})
export class ApprovalQueue implements OnInit {

  private readonly transactionService = inject(TransactionService);
  private readonly authService = inject(AuthService);
  private readonly toasts = inject(Toasts);
  private readonly queues = inject(WorkQueues);
  private readonly confirm = inject(Confirm);

  readonly pending = signal<TransactionResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly busy = signal<string | null>(null);

  /** Reference of the payment whose decline reason is being entered. */
  readonly declining = signal<string | null>(null);
  declineReason = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {

    this.loading.set(true);
    this.error.set('');

    this.transactionService.awaitingApproval().subscribe({
      next: pending => {
        this.pending.set(pending);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the approval queue.');
        this.toasts.error('Could not load the approval queue');
        this.loading.set(false);
      },
    });
  }

  canApprove(): boolean {
    return this.authService.canApprove();
  }

  /**
   * The maker can never be the checker. Hiding the action is a courtesy; the
   * server refuses it regardless.
   */
  isOwnPayment(transaction: TransactionResponse): boolean {
    return transaction.createdByUsername
      === this.authService.currentUser()?.username;
  }

  /**
   * The second pair of eyes, asked to look once more at what it is passing.
   *
   * Approving is one click and releases the money to the scheme — the whole
   * point of four-eyes is that somebody checks, so the check is laid out
   * where it cannot be skimmed past on the way to the button.
   */
  approve(transaction: TransactionResponse): void {

    const debited = transaction.amount + (transaction.debtorFeeAmount ?? 0);

    this.confirm.askThen({
      title: `Approve ${formatNumber(transaction.amount, 'en-US', '1.2-2')} `
        + `${transaction.currency}?`,
      message: 'This releases the payment. It leaves the queue and goes out '
        + 'on the rail below.',
      facts: [
        {
          label: 'Amount',
          value: `${formatNumber(transaction.amount, 'en-US', '1.2-2')} `
            + `${transaction.currency}`,
        },
        {
          label: 'To',
          value: [transaction.creditorName, transaction.creditorIban]
            .filter(Boolean).join(' · ') || '—',
        },
        {
          label: 'From',
          value: [transaction.debtorName, transaction.debtorIban]
            .filter(Boolean).join(' · ') || '—',
        },
        {
          label: 'Rail',
          value: transaction.paymentTypeName ?? transaction.paymentType ?? '—',
        },
        {
          label: 'Total debited',
          value: `${formatNumber(debited, 'en-US', '1.2-2')} `
            + `${transaction.currency}`,
        },
        { label: 'Raised by', value: transaction.createdByUsername ?? 'unknown' },
      ],
      note: 'Once released it cannot be put back in the queue. Getting the '
        + 'money back afterwards takes a recall, which the other bank may refuse.',
      confirmLabel: 'Approve and release',
      cancelLabel: 'Not yet',
    }, () => this.release(transaction));
  }

  private release(transaction: TransactionResponse): void {

    this.busy.set(transaction.transactionReference);
    this.error.set('');

    this.transactionService.approve(transaction.transactionReference).subscribe({
      next: released => {
        this.busy.set(null);
        this.toasts.success(
          `Payment approved — ${released.amount} ${released.currency}`,
          `${released.transactionReference} released and now ${released.status}.`,
        );
        this.load();

        /*
         * The badge counts this queue, and acting on it does not
         * navigate -- so without this the shell keeps claiming the
         * item is still waiting until the page is reloaded.
         */
        this.queues.refresh();
      },
      error: error => {
        this.busy.set(null);
        this.error.set(this.messageFrom(error, 'The payment could not be approved.'));
        this.toasts.failure('The payment could not be approved', error);
      },
    });
  }

  startDecline(transaction: TransactionResponse): void {
    this.declining.set(transaction.transactionReference);
    this.declineReason = '';
  }

  cancelDecline(): void {
    this.declining.set(null);
    this.declineReason = '';
  }

  confirmDecline(transaction: TransactionResponse): void {

    this.busy.set(transaction.transactionReference);
    this.error.set('');

    this.transactionService
      .decline(transaction.transactionReference, this.declineReason || 'Declined by approver')
      .subscribe({
        next: declined => {
          this.busy.set(null);
          this.declining.set(null);
          this.declineReason = '';
          this.toasts.info(
            `Payment declined — ${declined.amount} ${declined.currency}`,
            `${declined.transactionReference}. No money moved.`,
          );
          this.load();

        /*
         * The badge counts this queue, and acting on it does not
         * navigate -- so without this the shell keeps claiming the
         * item is still waiting until the page is reloaded.
         */
        this.queues.refresh();
        },
        error: error => {
          this.busy.set(null);
          this.error.set(this.messageFrom(error, 'The payment could not be declined.'));
          this.toasts.failure('The payment could not be declined', error);
        },
      });
  }

  beneficiary(transaction: TransactionResponse): string {
    return transaction.creditorName
      ?? transaction.creditorIban
      ?? (transaction.destinationAccountId
        ? `Account ${transaction.destinationAccountId}`
        : 'Not stated');
  }

  private messageFrom(error: unknown, fallback: string): string {
    return (error as { error?: { message?: string } })?.error?.message ?? fallback;
  }
}
