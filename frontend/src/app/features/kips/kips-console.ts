import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, formatNumber } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Toasts } from '../../core/services/toasts';
import { Confirm, ConfirmFact } from '../../core/services/confirm';
import { WorkQueues } from '../../core/services/work-queues';
import { SchemeAction, TransactionService } from '../../core/services/transaction';
import { MessageView } from '../../shared/message-view';
import { HasUnsavedChanges } from '../../core/guards/unsaved-changes';

/**
 * The other side of the wire.
 *
 * Everything here is something KIPS or a counterparty bank does in real life,
 * not something this bank does: answering a payment we sent, sending one back,
 * delivering one to us, asking for one back. Those actions were scattered
 * across the screens of the people who cannot perform them, which read as if
 * a teller could accept their own payment on the beneficiary's behalf.
 *
 * Keeping them apart makes the boundary visible. Nothing on the bank's own
 * screens can move money on the scheme's say-so any more; it has to come
 * through here, and it comes through as a real message down the real inbound
 * path.
 */
@Component({
  selector: 'app-kips-console',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, MessageView],
  templateUrl: './kips-console.html',
  styleUrl: './kips-console.css',
})
export class KipsConsole implements OnInit, HasUnsavedChanges {

  /** A message typed out but not yet delivered. */
  hasUnsavedChanges(): boolean {
    return !this.busy() && !!this.pasted.trim();
  }

  /**
   * Back to how the console loads.
   *
   * Only after something the scheme actually did. A refused message keeps its
   * XML in the box, because that is the text the operator has to fix.
   */
  private resetForms(): void {

    this.pasted = '';
    this.openRow.set(null);
    this.rejectReason = 'AC01';
    this.returnReason = 'AC04';
    this.returnAmount = '';
  }

  private readonly transactions = inject(TransactionService);
  private readonly toasts = inject(Toasts);
  private readonly queues = inject(WorkQueues);
  private readonly confirm = inject(Confirm);

  readonly awaiting = signal<SchemeAction[]>([]);
  readonly returnable = signal<SchemeAction[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);

  /** The message the last action produced, shown so it can be read. */
  readonly lastMessage = signal('');
  readonly lastMessageTitle = signal('');

  /** Which row has its answer form open, by reference. */
  readonly openRow = signal<string | null>(null);

  /*
   * The reasons a beneficiary bank actually gives. Kept to the codes the KIPS
   * schemas accept, since anything else fails validation on the way in.
   */
  readonly rejectionReasons = [
    { code: 'AC01', label: 'AC01 — Incorrect account number' },
    { code: 'AC04', label: 'AC04 — Account closed' },
    { code: 'AC06', label: 'AC06 — Account blocked' },
    { code: 'AM04', label: 'AM04 — Insufficient funds' },
    { code: 'AM05', label: 'AM05 — Duplicate' },
    { code: 'RR03', label: 'RR03 — Missing creditor name or address' },
  ];

  readonly returnReasons = [
    { code: 'AC04', label: 'AC04 — Account closed' },
    { code: 'AC01', label: 'AC01 — Incorrect account number' },
    { code: 'AC06', label: 'AC06 — Account blocked' },
    { code: 'CUST', label: 'CUST — Their customer asked them to' },
    { code: 'AM05', label: 'AM05 — Duplicate' },
  ];

  rejectReason = 'AC01';
  returnReason = 'AC04';
  returnAmount = '';

  pasted = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {

    this.loading.set(true);

    this.transactions.schemeQueue().subscribe({
      next: queue => {
        this.awaiting.set(queue.awaitingStatus);
        this.returnable.set(queue.returnable);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.toasts.failure('The scheme queue could not be loaded', error);
      },
    });
  }

  /** Opening a row closes any other, so only one answer is in progress. */
  toggleRow(action: SchemeAction): void {

    const open = this.openRow() === action.transactionReference;

    this.openRow.set(open ? null : action.transactionReference);
    this.rejectReason = 'AC01';
    this.returnReason = action.recallReason ?? 'AC04';
    this.returnAmount = '';
  }

  isOpen(action: SchemeAction): boolean {
    return this.openRow() === action.transactionReference;
  }

  /** The payment being answered, the same way on every question. */
  private paymentFacts(action: SchemeAction): ConfirmFact[] {
    return [
      { label: 'Payment', value: action.transactionReference },
      {
        label: 'Amount',
        value: `${formatNumber(action.amount, 'en-US', '1.2-2')} ${action.currency}`,
      },
      {
        label: 'Beneficiary',
        value: [action.creditorName, action.creditorIban].filter(Boolean).join(' · '),
      },
    ];
  }

  private reasonLabel(
    reasons: { code: string; label: string }[],
    code: string,
  ): string {
    return reasons.find(r => r.code === code)?.label ?? code;
  }

  /*
   * Every answer here is the other bank speaking, and each one moves money on
   * our side the moment it lands — settles it, unwinds it, or brings it back.
   * None has an undo, so each is asked once, with the payment it applies to
   * named, because the rows look alike and the buttons sit close together.
   */

  accept(action: SchemeAction): void {

    this.confirm.askThen({
      title: `Accept for ${this.who(action)}?`,
      message: 'Plays the beneficiary bank confirming it received the money. '
        + 'A pacs.002 ACSC arrives and the payment settles.',
      facts: this.paymentFacts(action),
      note: 'Settled is final. After this the money can only come back by a '
        + 'return or an agreed recall.',
      confirmLabel: 'Send ACSC',
      cancelLabel: 'Go back',
    }, () => this.act(
      this.transactions.simulateCounterpartyResponse(
        action.transactionReference, 'ACSC'),
      `pacs.002 — accepted`,
      'Accepted',
      `${action.transactionReference} settled. The suspense position is cleared.`,
    ));
  }

  reject(action: SchemeAction): void {

    this.confirm.askThen({
      title: `Reject for ${this.who(action)}?`,
      message: 'Plays the beneficiary bank refusing the payment. A pacs.002 '
        + 'RJCT arrives and the debtor is refunded, charge included.',
      facts: [
        ...this.paymentFacts(action),
        {
          label: 'Reason',
          value: `${this.rejectReason} — `
            + this.reasonLabel(this.rejectionReasons, this.rejectReason),
        },
      ],
      note: 'The payment ends here as rejected. Sending it again means raising '
        + 'a new payment.',
      confirmLabel: 'Send RJCT',
      cancelLabel: 'Go back',
      danger: true,
    }, () => this.act(
      this.transactions.simulateCounterpartyResponse(
        action.transactionReference, 'RJCT', this.rejectReason),
      `pacs.002 — rejected (${this.rejectReason})`,
      'Rejected',
      `${action.transactionReference} came back. The debtor has been made whole,`
        + ` charge included.`,
    ));
  }

  sendBack(action: SchemeAction): void {

    const amount = this.returnAmount.trim();
    const returning = amount
      ? `${amount} ${action.currency}`
      : `${formatNumber(action.amount, 'en-US', '1.2-2')} ${action.currency} (all of it)`;

    const facts: ConfirmFact[] = [
      ...this.paymentFacts(action),
      { label: 'Returning', value: returning },
      {
        label: 'Reason',
        value: `${this.returnReason} — `
          + this.reasonLabel(this.returnReasons, this.returnReason),
      },
    ];

    if (action.recallId) {
      facts.push({ label: 'Answers recall', value: action.recallId });
    }

    this.confirm.askThen({
      title: `Send ${this.who(action)}'s payment back?`,
      message: 'Plays the beneficiary bank returning a settled payment. A '
        + 'pacs.004 arrives and the money is credited back to our customer.',
      facts,
      note: 'The charge for sending it is not refunded — a return undoes a '
        + 'payment that happened, unlike a rejection.',
      confirmLabel: 'Send pacs.004',
      cancelLabel: 'Go back',
    }, () => this.act(
      this.transactions.simulateReturn(
        action.transactionReference, this.returnReason, amount || undefined),
      `pacs.004 — returned (${this.returnReason})`,
      'Returned',
      `${amount || action.amount} ${action.currency} is on its way back to the`
        + ` debtor. The charge for sending it is not refunded.`,
    ));
  }

  deliver(): void {

    const xml = this.pasted.trim();

    if (!xml) {
      return;
    }

    /*
     * Named from the message itself, so the question says what is being
     * delivered rather than "this XML". A pacs.008 credits a customer; a
     * camt.056 lands in the recall queue — very different consequences.
     */
    const kind = xml.includes('FIToFICstmrCdtTrf') ? 'pacs.008 — a payment to one of our customers'
      : xml.includes('FIToFIPmtCxlReq') ? 'camt.056 — a request to send a payment back'
      : xml.includes('FIToFIPmtStsRpt') ? 'pacs.002 — a status report'
      : xml.includes('PmtRtr') ? 'pacs.004 — a payment return'
      : 'Unrecognised — it will be checked against the schema';

    this.confirm.askThen({
      title: 'Deliver this message to the bank?',
      message: 'It goes down the same inbound path a real delivery would, '
        + 'validated against the KIPS schema first.',
      facts: [
        { label: 'Message', value: kind },
        { label: 'Size', value: `${xml.length.toLocaleString('en-US')} characters` },
      ],
      note: 'If it validates, it is acted on at once — a payment is credited, '
        + 'a request is queued. There is no draft stage.',
      confirmLabel: 'Deliver it',
      cancelLabel: 'Go back',
    }, () => this.act(
      this.transactions.deliverInbound(xml),
      'Our answer',
      'Delivered',
      'The message went down the same path a real one would.',
    ));
  }

  /**
   * Every action here ends the same way: a message we can read, a queue that
   * has moved on, and the operator told which of those happened.
   */
  private act(
    call: { subscribe: (o: { next: (x: string) => void; error: (e: unknown) => void }) => void },
    title: string,
    toastTitle: string,
    toastBody: string,
  ): void {

    this.busy.set(true);
    this.lastMessage.set('');

    call.subscribe({
      next: xml => {
        this.busy.set(false);
        this.lastMessageTitle.set(title);
        this.lastMessage.set(xml);
        this.toasts.success(toastTitle, toastBody);
        this.resetForms();
        this.load();

        /*
         * Answering a payment empties the queue the KIPS badge counts, and
         * nothing here navigates -- so the badge would otherwise go on
         * claiming the payment was still waiting until the page reloaded.
         */
        this.queues.refresh();
      },
      error: error => {
        this.busy.set(false);
        this.toasts.failure('The scheme could not do that', error);
      },
    });
  }

  clearMessage(): void {
    this.lastMessage.set('');
  }

  /** An IBAN is easier to place with its holder's name beside it. */
  who(action: SchemeAction): string {
    return action.creditorName || 'the beneficiary';
  }
}
